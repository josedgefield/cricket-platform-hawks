package sg.hawkscc.platform.comms;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL for notices (V6__announcements.sql). Reads names and email addresses from the members
 * table (owned by identity); never writes to it.
 */
@Repository
class NoticeRepository {

    private final JdbcClient jdbc;

    NoticeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record NoticeRow(UUID id, String title, String body, boolean pinned, String authorName, Instant createdAt,
                     Instant editedAt, String myReaction, int replyCount, boolean unread, int readCount,
                     String emailStatus, Integer emailedCount, Integer emailFailures) {
    }

    record ReplyRow(UUID id, UUID memberId, String authorName, String body, Instant createdAt, boolean hidden,
                    String hiddenReason) {
    }

    record Recipient(String email, String displayName) {
    }

    private static final String NOTICE_SELECT = """
            select a.id, a.title, a.body, a.pinned, m.display_name as author_name, a.created_at, a.edited_at,
                   a.email_status, a.emailed_count, a.email_failures,
                   (select r.reaction from announcement_reactions r
                     where r.announcement_id = a.id and r.member_id = :me) as my_reaction,
                   (select count(*) from announcement_replies p
                     where p.announcement_id = a.id and p.hidden_at is null) as reply_count,
                   not exists (select 1 from announcement_reads d
                                where d.announcement_id = a.id and d.member_id = :me) as unread,
                   (select count(*) from announcement_reads d where d.announcement_id = a.id) as read_count
            from announcements a join members m on m.id = a.author_id
            where a.club_id = :club and a.archived_at is null""";

    private static NoticeRow notice(ResultSet rs, int n) throws SQLException {
        return new NoticeRow(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("body"),
                rs.getBoolean("pinned"), rs.getString("author_name"), instant(rs, "created_at"),
                instant(rs, "edited_at"), rs.getString("my_reaction"), rs.getInt("reply_count"),
                rs.getBoolean("unread"), rs.getInt("read_count"), rs.getString("email_status"),
                rs.getObject("emailed_count", Integer.class), rs.getObject("email_failures", Integer.class));
    }

    /** Current notices: pinned first, then newest. */
    List<NoticeRow> list(UUID clubId, UUID me) {
        return jdbc.sql(NOTICE_SELECT + " order by a.pinned desc, a.created_at desc limit 100")
                .param("club", clubId).param("me", me).query(NoticeRepository::notice).list();
    }

    Optional<NoticeRow> find(UUID clubId, UUID id, UUID me) {
        return jdbc.sql(NOTICE_SELECT + " and a.id = :id")
                .param("club", clubId).param("me", me).param("id", id).query(NoticeRepository::notice).optional();
    }

    Optional<UUID> findByRequestKey(UUID clubId, String requestKey) {
        return jdbc.sql("select id from announcements where club_id = :club and request_key = :key")
                .param("club", clubId).param("key", requestKey).query(UUID.class).optional();
    }

    /** Reaction counts for the given notices: notice id → (reaction → count). */
    Map<UUID, Map<String, Integer>> reactionCounts(UUID clubId) {
        Map<UUID, Map<String, Integer>> out = new LinkedHashMap<>();
        jdbc.sql("""
                        select r.announcement_id, r.reaction, count(*) as n
                        from announcement_reactions r join announcements a on a.id = r.announcement_id
                        where a.club_id = :club and a.archived_at is null
                        group by r.announcement_id, r.reaction""")
                .param("club", clubId)
                .query((rs, n) -> {
                    out.computeIfAbsent(rs.getObject("announcement_id", UUID.class), k -> new LinkedHashMap<>())
                            .put(rs.getString("reaction"), rs.getInt("n"));
                    return n;
                }).list();
        return out;
    }

    UUID insert(UUID clubId, UUID author, String title, String body, boolean pinned, boolean emailMembers,
                String requestKey) {
        return jdbc.sql("""
                        insert into announcements (club_id, author_id, title, body, pinned, email_status, request_key)
                        values (:club, :author, :title, :body, :pinned, :email, :key) returning id""")
                .param("club", clubId).param("author", author).param("title", title).param("body", body)
                .param("pinned", pinned).param("email", emailMembers ? "sending" : null).param("key", requestKey)
                .query(UUID.class).single();
    }

    void update(UUID id, String title, String body, boolean pinned, boolean textChanged) {
        jdbc.sql("""
                        update announcements set title = :title, body = :body, pinned = :pinned,
                               edited_at = case when cast(:changed as boolean) then now() else edited_at end
                        where id = :id""")
                .param("id", id).param("title", title).param("body", body).param("pinned", pinned)
                .param("changed", textChanged).update();
    }

    void archive(UUID id, UUID by) {
        jdbc.sql("update announcements set archived_at = now(), archived_by = :by where id = :id and archived_at is null")
                .param("id", id).param("by", by).update();
    }

    void recordEmail(UUID id, String status, int sent, int failed) {
        jdbc.sql("update announcements set email_status = :status, emailed_count = :sent, email_failures = :failed where id = :id")
                .param("id", id).param("status", status).param("sent", sent).param("failed", failed).update();
    }

    void markRead(UUID id, UUID me) {
        jdbc.sql("insert into announcement_reads (announcement_id, member_id) values (:id, :me) on conflict do nothing")
                .param("id", id).param("me", me).update();
    }

    void setReaction(UUID id, UUID me, String reaction) {
        jdbc.sql("""
                        insert into announcement_reactions (announcement_id, member_id, reaction)
                        values (:id, :me, :reaction)
                        on conflict (announcement_id, member_id) do update set reaction = excluded.reaction, created_at = now()""")
                .param("id", id).param("me", me).param("reaction", reaction).update();
    }

    void clearReaction(UUID id, UUID me) {
        jdbc.sql("delete from announcement_reactions where announcement_id = :id and member_id = :me")
                .param("id", id).param("me", me).update();
    }

    List<ReplyRow> replies(UUID announcementId) {
        return jdbc.sql("""
                        select p.id, p.member_id, m.display_name, p.body, p.created_at, p.hidden_at is not null as hidden,
                               p.hidden_reason
                        from announcement_replies p join members m on m.id = p.member_id
                        where p.announcement_id = :id order by p.created_at, p.id""")
                .param("id", announcementId)
                .query((rs, n) -> new ReplyRow(rs.getObject("id", UUID.class), rs.getObject("member_id", UUID.class),
                        rs.getString("display_name"), rs.getString("body"), instant(rs, "created_at"),
                        rs.getBoolean("hidden"), rs.getString("hidden_reason")))
                .list();
    }

    /** Replies this member wrote on this notice in the last minute, to slow down flooding. */
    int recentReplies(UUID announcementId, UUID me) {
        return jdbc.sql("""
                        select count(*) from announcement_replies
                        where announcement_id = :id and member_id = :me and created_at > now() - interval '1 minute'""")
                .param("id", announcementId).param("me", me).query(Integer.class).single();
    }

    UUID insertReply(UUID clubId, UUID announcementId, UUID me, String body) {
        return jdbc.sql("""
                        insert into announcement_replies (club_id, announcement_id, member_id, body)
                        values (:club, :id, :me, :body) returning id""")
                .param("club", clubId).param("id", announcementId).param("me", me).param("body", body)
                .query(UUID.class).single();
    }

    Optional<UUID> replyAuthor(UUID announcementId, UUID replyId) {
        return jdbc.sql("select member_id from announcement_replies where id = :reply and announcement_id = :id")
                .param("reply", replyId).param("id", announcementId).query(UUID.class).optional();
    }

    void hideReply(UUID replyId, UUID by, String reason) {
        jdbc.sql("""
                        update announcement_replies set hidden_at = now(), hidden_by = :by, hidden_reason = :reason
                        where id = :id and hidden_at is null""")
                .param("id", replyId).param("by", by).param("reason", reason).update();
    }

    /** Active members, who get the email copy. */
    List<Recipient> recipients(UUID clubId) {
        return jdbc.sql("select email, display_name from members where club_id = :club and status = 'active' order by email")
                .param("club", clubId)
                .query((rs, n) -> new Recipient(rs.getString("email"), rs.getString("display_name"))).list();
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toInstant();
    }
}
