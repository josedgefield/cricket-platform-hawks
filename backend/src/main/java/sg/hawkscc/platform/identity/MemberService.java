package sg.hawkscc.platform.identity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import sg.hawkscc.platform.audit.AuditLog;
import sg.hawkscc.platform.club.ClubContext;

/**
 * Inviting and managing members (docs/03). Admins manage players; only superusers (support)
 * manage admins and superusers or change anyone's role. Nobody changes their own role or
 * status, and the club always keeps at least one active superuser. Every change is audited.
 */
@Service
public class MemberService {

    private static final Logger log = LoggerFactory.getLogger(MemberService.class);

    private final IdentityRepository repo;
    private final IdentityMailer mailer;
    private final AuditLog audit;
    private final ClubContext club;
    private final TransactionTemplate tx;

    public MemberService(IdentityRepository repo, IdentityMailer mailer, AuditLog audit, ClubContext club,
                         TransactionTemplate tx) {
        this.repo = repo;
        this.mailer = mailer;
        this.audit = audit;
        this.club = club;
        this.tx = tx;
    }

    public record NewMember(String email, String displayName, String phone, Role role) {
    }

    public record Changes(String email, String displayName, String phone) {
    }

    // ---------- reading ----------

    public List<MemberView> list(CurrentMember actor, MemberStatus status, String search) {
        return repo.listMembers(club.clubId(), status, search).stream()
                .map(m -> MemberView.of(m, canManage(actor, m)))
                .toList();
    }

    public MemberView get(CurrentMember actor, UUID id) {
        var m = find(id);
        return MemberView.of(m, canManage(actor, m));
    }

    public MemberView me(CurrentMember me) {
        return MemberView.of(find(me.memberId()), false);
    }

    // ---------- changes ----------

    public MemberView invite(CurrentMember actor, NewMember request) {
        Role role = request.role() == null ? Role.PLAYER : request.role();
        if (role != Role.PLAYER && actor.role() != Role.SUPERUSER) {
            throw IdentityException.forbidden("Only support (a superuser) can invite an admin or superuser.");
        }
        String email = Tokens.normaliseEmail(request.email());
        repo.findMemberByEmail(club.clubId(), email).ifPresent(existing -> {
            throw IdentityException.conflict(existing.displayName() + " already has this email ("
                    + existing.status().code() + ").");
        });
        String token = Tokens.newToken();
        UUID id = tx.execute(s -> {
            UUID created = repo.insertMember(club.clubId(), email, request.displayName().strip(), blankToNull(request.phone()),
                    role, MemberStatus.INVITED, null, actor.memberId());
            repo.insertToken(created, IdentityRepository.Purpose.INVITE, Tokens.hash(token), AuthService.INVITE_VALID_FOR,
                    actor.memberId());
            audit.record(club.clubId(), actor.memberId(), "member.invited", "member", created,
                    Map.of("email", email, "role", role.code()));
            return created;
        });
        log.info("Member {} invited by {}", id, actor.memberId());
        sendInvite(id, actor.displayName(), token);
        return get(actor, id);
    }

    public MemberView resendInvite(CurrentMember actor, UUID id) {
        var m = manageable(actor, id);
        if (m.status() != MemberStatus.INVITED) {
            throw IdentityException.conflict(m.status() == MemberStatus.ACTIVE
                    ? m.displayName() + " has already joined."
                    : "Reactivate " + m.displayName() + " before sending a new invite.");
        }
        String token = Tokens.newToken();
        tx.executeWithoutResult(s -> {
            repo.expireTokens(id, IdentityRepository.Purpose.INVITE);
            repo.insertToken(id, IdentityRepository.Purpose.INVITE, Tokens.hash(token), AuthService.INVITE_VALID_FOR,
                    actor.memberId());
            audit.record(club.clubId(), actor.memberId(), "member.invite_resent", "member", id, Map.of());
        });
        sendInvite(id, actor.displayName(), token);
        return get(actor, id);
    }

    /**
     * Edits name, phone and (only before the invite is accepted, to fix a typo) email. Changing an
     * active member's email is refused: it is their sign-in, so it could be used to take over
     * their account.
     */
    public MemberView update(CurrentMember actor, UUID id, Changes changes) {
        var m = manageable(actor, id);
        return applyChanges(actor, m, changes, true);
    }

    public MemberView updateMe(CurrentMember me, Changes changes) {
        var m = find(me.memberId());
        return applyChanges(me, m, new Changes(null, changes.displayName(), changes.phone()), false);
    }

    public MemberView deactivate(CurrentMember actor, UUID id) {
        var m = manageable(actor, id);
        if (m.id().equals(actor.memberId())) {
            throw IdentityException.forbidden("You can't deactivate yourself.");
        }
        if (m.status() == MemberStatus.DEACTIVATED) {
            return get(actor, id);
        }
        keepOneSuperuser(m);
        tx.executeWithoutResult(s -> {
            repo.setStatus(id, MemberStatus.DEACTIVATED);
            repo.expireTokens(id, IdentityRepository.Purpose.INVITE);
            repo.expireTokens(id, IdentityRepository.Purpose.PASSWORD_RESET);
            repo.revokeSessions(id, null);
            audit.record(club.clubId(), actor.memberId(), "member.deactivated", "member", id,
                    Map.of("previousStatus", m.status().code()));
        });
        return get(actor, id);
    }

    /** Back to active if they had joined, otherwise back to invited (send a new invite next). */
    public MemberView reactivate(CurrentMember actor, UUID id) {
        var m = manageable(actor, id);
        if (m.status() != MemberStatus.DEACTIVATED) {
            return get(actor, id);
        }
        MemberStatus next = m.passwordHash() == null ? MemberStatus.INVITED : MemberStatus.ACTIVE;
        tx.executeWithoutResult(s -> {
            repo.setStatus(id, next);
            audit.record(club.clubId(), actor.memberId(), "member.reactivated", "member", id,
                    Map.of("status", next.code()));
        });
        return get(actor, id);
    }

    public MemberView changeRole(CurrentMember actor, UUID id, Role role) {
        if (actor.role() != Role.SUPERUSER) {
            throw IdentityException.forbidden("Only support (a superuser) can change roles.");
        }
        var m = find(id);
        if (m.id().equals(actor.memberId())) {
            throw IdentityException.forbidden("You can't change your own role.");
        }
        if (m.role() == role) {
            return get(actor, id);
        }
        if (role != Role.SUPERUSER) {
            keepOneSuperuser(m);
        }
        tx.executeWithoutResult(s -> {
            repo.setRole(id, role);
            // Sessions last a different time per role, and permissions change: sign in again.
            repo.revokeSessions(id, null);
            audit.record(club.clubId(), actor.memberId(), "member.role_changed", "member", id,
                    Map.of("from", m.role().code(), "to", role.code()));
        });
        return get(actor, id);
    }

    // ---------- bootstrap ----------

    /** Creates and invites the first superuser on a new server. No-op if one already exists. */
    void bootstrapSuperuser(String email, String displayName) {
        if (repo.anySuperuser(club.clubId())) {
            return;
        }
        String normalised = Tokens.normaliseEmail(email);
        if (repo.findMemberByEmail(club.clubId(), normalised).isPresent()) {
            log.warn("Bootstrap superuser {} already exists with another role; not changing it", normalised);
            return;
        }
        String token = Tokens.newToken();
        UUID id = tx.execute(s -> {
            UUID created = repo.insertMember(club.clubId(), normalised, displayName, null, Role.SUPERUSER,
                    MemberStatus.INVITED, null, null);
            repo.insertToken(created, IdentityRepository.Purpose.INVITE, Tokens.hash(token), AuthService.INVITE_VALID_FOR,
                    null);
            audit.record(club.clubId(), null, "member.bootstrap_superuser", "member", created, Map.of("email", normalised));
            return created;
        });
        log.info("Invited the first superuser {} ({})", normalised, id);
        sendInvite(id, null, token);
    }

    // ---------- helpers ----------

    /** Superusers manage everyone; admins manage players only. */
    static boolean canManage(CurrentMember actor, IdentityRepository.MemberRow target) {
        return switch (actor.role()) {
            case SUPERUSER -> true;
            case ADMIN -> target.role() == Role.PLAYER;
            case PLAYER -> false;
        };
    }

    private IdentityRepository.MemberRow find(UUID id) {
        return repo.findMember(club.clubId(), id).orElseThrow(IdentityException::notFound);
    }

    private IdentityRepository.MemberRow manageable(CurrentMember actor, UUID id) {
        var m = find(id);
        if (!canManage(actor, m)) {
            throw IdentityException.forbidden("Admins can manage players only. Ask support to change an admin or superuser.");
        }
        return m;
    }

    private void keepOneSuperuser(IdentityRepository.MemberRow m) {
        if (m.role() == Role.SUPERUSER && m.status() == MemberStatus.ACTIVE
                && repo.countOtherActiveSuperusers(club.clubId(), m.id()) == 0) {
            throw IdentityException.conflict("The club needs at least one active superuser. Make someone else a superuser first.");
        }
    }

    private MemberView applyChanges(CurrentMember actor, IdentityRepository.MemberRow m, Changes c, boolean emailAllowed) {
        String email = c.email() == null || !emailAllowed ? m.email() : Tokens.normaliseEmail(c.email());
        String name = c.displayName() == null ? m.displayName() : c.displayName().strip();
        String phone = c.phone() == null ? m.phone() : blankToNull(c.phone());
        boolean emailChanged = !email.equals(m.email());
        if (emailChanged) {
            if (m.status() != MemberStatus.INVITED) {
                throw IdentityException.conflict("An email can only be changed before the invite is accepted. "
                        + "Ask support if a member's sign-in email must change.");
            }
            repo.findMemberByEmail(club.clubId(), email).ifPresent(other -> {
                throw IdentityException.conflict(other.displayName() + " already has this email.");
            });
        }
        Map<String, String> changed = new LinkedHashMap<>();
        if (emailChanged) {
            changed.put("email", email);
        }
        if (!name.equals(m.displayName())) {
            changed.put("displayName", name);
        }
        if (!Objects.equals(phone, m.phone())) {
            changed.put("phone", phone == null ? "" : "changed");
        }
        if (changed.isEmpty()) {
            return emailAllowed ? get(actor, m.id()) : me(actor);
        }
        String token = emailChanged ? Tokens.newToken() : null;
        tx.executeWithoutResult(s -> {
            repo.updateProfile(m.id(), email, name, phone);
            if (emailChanged) {
                // The old link went to the wrong address: replace it and send to the new one.
                repo.expireTokens(m.id(), IdentityRepository.Purpose.INVITE);
                repo.insertToken(m.id(), IdentityRepository.Purpose.INVITE, Tokens.hash(token),
                        AuthService.INVITE_VALID_FOR, actor.memberId());
            }
            audit.record(club.clubId(), actor.memberId(), "member.updated", "member", m.id(), changed);
        });
        if (emailChanged) {
            sendInvite(m.id(), actor.displayName(), token);
        }
        return emailAllowed ? get(actor, m.id()) : me(actor);
    }

    /** Sends outside the transaction, then records how it went so admins can see failures. */
    private void sendInvite(UUID id, String invitedBy, String token) {
        var m = find(id);
        String error = mailer.sendInvite(m.email(), m.displayName(), invitedBy, token, AuthService.INVITE_VALID_FOR)
                .orElse(null);
        repo.recordInviteEmail(id, error);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
