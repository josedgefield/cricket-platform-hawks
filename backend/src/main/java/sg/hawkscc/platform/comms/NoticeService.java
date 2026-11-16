package sg.hawkscc.platform.comms;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import sg.hawkscc.platform.audit.AuditLog;
import sg.hawkscc.platform.club.ClubContext;
import sg.hawkscc.platform.comms.NoticeRepository.NoticeRow;
import sg.hawkscc.platform.identity.CurrentMember;
import sg.hawkscc.platform.identity.MailGateway;
import sg.hawkscc.platform.identity.Role;

/**
 * Club notices (docs/07). Admins and superusers write, pin, edit and withdraw them; every
 * member reads, reacts and replies. A notice can also be emailed to active members; that runs
 * in the background and its result is stored, so a slow or failing mail server never blocks
 * or loses the notice itself.
 */
@Service
public class NoticeService {

    private static final Logger log = LoggerFactory.getLogger(NoticeService.class);

    static final Set<String> REACTIONS = Set.of("like", "done", "cricket", "clap", "question");
    private static final int MAX_REPLIES_PER_MINUTE = 5;

    private final NoticeRepository repo;
    private final AuditLog audit;
    private final ClubContext club;
    private final TransactionTemplate tx;
    private final MailGateway mail;
    private final String baseUrl;
    private final Executor background;

    public NoticeService(NoticeRepository repo, AuditLog audit, ClubContext club, TransactionTemplate tx,
                         MailGateway mail, @Value("${hawks.app.base-url}") String baseUrl,
                         @Value("${hawks.comms.email-in-background:true}") boolean inBackground) {
        this.repo = repo;
        this.audit = audit;
        this.club = club;
        this.tx = tx;
        this.mail = mail;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.background = inBackground ? task -> Thread.ofVirtual().name("notice-email").start(task) : Runnable::run;
    }

    public record NewNotice(String title, String body, boolean pinned, boolean emailMembers, String requestKey) {
    }

    /** Null fields are left as they are. */
    public record Changes(String title, String body, Boolean pinned) {
    }

    // ---------- reading ----------

    public List<NoticeViews.Notice> list(CurrentMember me) {
        Map<UUID, Map<String, Integer>> reactions = repo.reactionCounts(club.clubId());
        return repo.list(club.clubId(), me.memberId()).stream()
                .map(n -> view(n, reactions.getOrDefault(n.id(), Map.of()), me))
                .toList();
    }

    /** One notice with its replies. Opening it counts as reading it. */
    public NoticeViews.NoticeWithReplies open(CurrentMember me, UUID id) {
        find(me, id);
        repo.markRead(id, me.memberId());
        return withReplies(me, id);
    }

    // ---------- members ----------

    /** Sets the caller's reaction, or removes it when {@code reaction} is null or blank. */
    public NoticeViews.NoticeWithReplies react(CurrentMember me, UUID id, String reaction) {
        find(me, id);
        if (reaction == null || reaction.isBlank()) {
            repo.clearReaction(id, me.memberId());
        } else if (REACTIONS.contains(reaction.strip())) {
            repo.setReaction(id, me.memberId(), reaction.strip());
        } else {
            throw CommsException.badRequest("Reaction must be like, done, cricket, clap or question.");
        }
        return withReplies(me, id);
    }

    public NoticeViews.NoticeWithReplies reply(CurrentMember me, UUID id, String body) {
        find(me, id);
        String text = text(body, "reply", 1000);
        if (repo.recentReplies(id, me.memberId()) >= MAX_REPLIES_PER_MINUTE) {
            throw new CommsException(HttpStatus.TOO_MANY_REQUESTS, "That's a lot of replies. Wait a minute and try again.");
        }
        repo.insertReply(club.clubId(), id, me.memberId(), text);
        return withReplies(me, id);
    }

    /** A member can hide their own reply; an admin can hide anyone's, with a reason. */
    public NoticeViews.NoticeWithReplies hideReply(CurrentMember me, UUID id, UUID replyId, String reason) {
        find(me, id);
        UUID author = repo.replyAuthor(id, replyId).orElseThrow(() -> CommsException.notFound("reply"));
        boolean own = author.equals(me.memberId());
        if (!own && !me.role().atLeast(Role.ADMIN)) {
            throw CommsException.forbidden("You can only remove your own replies.");
        }
        String why = own && (reason == null || reason.isBlank()) ? "Removed by its writer" : text(reason, "reason", 300);
        tx.executeWithoutResult(s -> {
            repo.hideReply(replyId, me.memberId(), why);
            if (!own) {
                audit.record(club.clubId(), me.memberId(), "notice.reply_hidden", "announcement_reply", replyId,
                        Map.of("reason", why));
            }
        });
        return withReplies(me, id);
    }

    // ---------- admins ----------

    public NoticeViews.Notice publish(CurrentMember actor, NewNotice request) {
        String title = text(request.title(), "title", 120);
        String body = text(request.body(), "message", 5000);
        String key = text(request.requestKey(), "requestKey", 80);
        UUID clubId = club.clubId();

        var done = repo.findByRequestKey(clubId, key);
        if (done.isPresent()) {
            return notice(actor, done.get());
        }
        UUID id;
        try {
            id = tx.execute(s -> {
                UUID created = repo.insert(clubId, actor.memberId(), title, body, request.pinned(), request.emailMembers(), key);
                audit.record(clubId, actor.memberId(), "notice.published", "announcement", created,
                        details("title", title, "pinned", request.pinned(), "emailMembers", request.emailMembers()));
                return created;
            });
        } catch (DuplicateKeyException e) {
            // The same form arrived twice at once: the other request published it.
            return notice(actor, repo.findByRequestKey(clubId, key).orElseThrow(() -> e));
        }
        if (request.emailMembers()) {
            String author = actor.displayName();
            background.execute(() -> emailMembers(clubId, id, title, body, author));
        }
        return notice(actor, id);
    }

    public NoticeViews.Notice edit(CurrentMember actor, UUID id, Changes changes) {
        NoticeRow current = find(actor, id);
        String title = changes.title() == null ? current.title() : text(changes.title(), "title", 120);
        String body = changes.body() == null ? current.body() : text(changes.body(), "message", 5000);
        boolean pinned = changes.pinned() == null ? current.pinned() : changes.pinned();
        boolean textChanged = !title.equals(current.title()) || !body.equals(current.body());
        if (textChanged || pinned != current.pinned()) {
            tx.executeWithoutResult(s -> {
                repo.update(id, title, body, pinned, textChanged);
                audit.record(club.clubId(), actor.memberId(), "notice.edited", "announcement", id,
                        details("textChanged", textChanged, "pinned", pinned));
            });
        }
        return notice(actor, id);
    }

    /** Withdraws a notice: members no longer see it, but it is kept for the record. */
    public void archive(CurrentMember actor, UUID id) {
        find(actor, id);
        tx.executeWithoutResult(s -> {
            repo.archive(id, actor.memberId());
            audit.record(club.clubId(), actor.memberId(), "notice.withdrawn", "announcement", id, Map.of());
        });
    }

    // ---------- email copy ----------

    private void emailMembers(UUID clubId, UUID id, String title, String body, String author) {
        int sent = 0;
        int failed = 0;
        try {
            String clubName = club.current().name();
            String link = baseUrl + "/notices/" + id;
            for (NoticeRepository.Recipient r : repo.recipients(clubId)) {
                String text = "Hi " + r.displayName() + ",\n\n" + author + " posted a notice in the " + clubName
                        + " app:\n\n" + title + "\n\n" + body + "\n\nReply or react in the app:\n" + link + "\n";
                try {
                    mail.send(new MailGateway.Email(r.email(), clubName + ": " + title, text, null));
                    sent++;
                } catch (RuntimeException e) {
                    failed++;
                    log.warn("Notice {} email to a member failed: {}", id, e.getMessage());
                }
            }
        } catch (RuntimeException e) {
            log.error("Emailing notice {} stopped early", id, e);
            failed++;
        } finally {
            try {
                repo.recordEmail(id, failed == 0 ? "sent" : "failed", sent, failed);
            } catch (RuntimeException e) {
                log.error("Couldn't record the email result for notice {}", id, e);
            }
        }
        log.info("Notice {} emailed: {} sent, {} failed", id, sent, failed);
    }

    // ---------- helpers ----------

    private NoticeRow find(CurrentMember me, UUID id) {
        return repo.find(club.clubId(), id, me.memberId()).orElseThrow(() -> CommsException.notFound("notice"));
    }

    private NoticeViews.Notice notice(CurrentMember me, UUID id) {
        NoticeRow row = find(me, id);
        return view(row, repo.reactionCounts(club.clubId()).getOrDefault(id, Map.of()), me);
    }

    private NoticeViews.NoticeWithReplies withReplies(CurrentMember me, UUID id) {
        boolean admin = me.role().atLeast(Role.ADMIN);
        List<NoticeViews.Reply> replies = repo.replies(id).stream()
                // Players don't see that a hidden reply existed, unless it is their own.
                .filter(r -> !r.hidden() || admin || r.memberId().equals(me.memberId()))
                .map(r -> new NoticeViews.Reply(r.id(), r.authorName(), r.memberId().equals(me.memberId()),
                        r.hidden() ? null : r.body(), r.createdAt(), r.hidden(), r.hidden() ? r.hiddenReason() : null))
                .toList();
        return new NoticeViews.NoticeWithReplies(notice(me, id), replies);
    }

    private static NoticeViews.Notice view(NoticeRow n, Map<String, Integer> reactions, CurrentMember me) {
        boolean admin = me.role().atLeast(Role.ADMIN);
        NoticeViews.EmailCopy email = admin && n.emailStatus() != null
                ? new NoticeViews.EmailCopy(n.emailStatus(), n.emailedCount(), n.emailFailures()) : null;
        return new NoticeViews.Notice(n.id(), n.title(), n.body(), n.pinned(), n.authorName(), n.createdAt(),
                n.editedAt(), reactions, n.myReaction(), n.replyCount(), n.unread(), admin ? n.readCount() : null, email);
    }

    private static String text(String value, String name, int max) {
        if (value == null || value.isBlank()) {
            throw CommsException.badRequest("Enter a " + name + ".");
        }
        String v = value.strip();
        if (v.length() > max) {
            throw CommsException.badRequest("The " + name + " can be at most " + max + " characters.");
        }
        return v;
    }

    private static Map<String, String> details(Object... pairs) {
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.put((String) pairs[i], String.valueOf(pairs[i + 1]));
        }
        return out;
    }
}
