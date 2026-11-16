package sg.hawkscc.platform.identity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** SQL for members, email tokens and sessions (V4__identity.sql). */
@Repository
class IdentityRepository {

    private final JdbcClient jdbc;

    IdentityRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record MemberRow(UUID id, UUID clubId, String email, String displayName, String phone, Role role,
                     MemberStatus status, String passwordHash, UUID playerId, Instant createdAt,
                     Instant inviteSentAt, String lastEmailError, Instant lastSignInAt) {
    }

    private static final String MEMBER_COLUMNS = """
            id, club_id, email, display_name, phone, role, status, password_hash, player_id, created_at,
            invite_sent_at, last_email_error, last_sign_in_at""";

    private static MemberRow member(ResultSet rs, int n) throws SQLException {
        return new MemberRow(rs.getObject("id", UUID.class), rs.getObject("club_id", UUID.class),
                rs.getString("email"), rs.getString("display_name"), rs.getString("phone"),
                Role.valueOf(rs.getString("role").toUpperCase()), MemberStatus.fromCode(rs.getString("status")),
                rs.getString("password_hash"), rs.getObject("player_id", UUID.class), instant(rs, "created_at"),
                instant(rs, "invite_sent_at"), rs.getString("last_email_error"), instant(rs, "last_sign_in_at"));
    }

    // ---------- members ----------

    Optional<MemberRow> findMember(UUID clubId, UUID id) {
        return jdbc.sql("select " + MEMBER_COLUMNS + " from members where club_id = :club and id = :id")
                .param("club", clubId).param("id", id).query(IdentityRepository::member).optional();
    }

    Optional<MemberRow> findMemberByEmail(UUID clubId, String email) {
        return jdbc.sql("select " + MEMBER_COLUMNS + " from members where club_id = :club and email = :email")
                .param("club", clubId).param("email", email).query(IdentityRepository::member).optional();
    }

    /** Members, optionally filtered by status and a name/email search, by name. */
    List<MemberRow> listMembers(UUID clubId, MemberStatus status, String search) {
        String q = search == null || search.isBlank() ? null : "%" + search.strip().toLowerCase() + "%";
        return jdbc.sql("select " + MEMBER_COLUMNS + """
                         from members
                        where club_id = :club
                          and (cast(:status as text) is null or status = cast(:status as text))
                          and (cast(:q as text) is null or lower(display_name) like cast(:q as text)
                               or email like cast(:q as text))
                        order by lower(display_name), email""")
                .param("club", clubId).param("status", status == null ? null : status.code()).param("q", q)
                .query(IdentityRepository::member).list();
    }

    UUID insertMember(UUID clubId, String email, String displayName, String phone, Role role, MemberStatus status,
                      String passwordHash, UUID invitedBy) {
        return jdbc.sql("""
                        insert into members (club_id, email, display_name, phone, role, status, password_hash, invited_by)
                        values (:club, :email, :name, :phone, :role, :status, :hash, :by)
                        returning id""")
                .param("club", clubId).param("email", email).param("name", displayName).param("phone", phone)
                .param("role", role.code()).param("status", status.code()).param("hash", passwordHash)
                .param("by", invitedBy)
                .query(UUID.class).single();
    }

    void updateProfile(UUID id, String email, String displayName, String phone) {
        jdbc.sql("""
                        update members set email = :email, display_name = :name, phone = :phone, updated_at = now()
                        where id = :id""")
                .param("id", id).param("email", email).param("name", displayName).param("phone", phone).update();
    }

    void setStatus(UUID id, MemberStatus status) {
        jdbc.sql("update members set status = :status, updated_at = now() where id = :id")
                .param("id", id).param("status", status.code()).update();
    }

    void setRole(UUID id, Role role) {
        jdbc.sql("update members set role = :role, updated_at = now() where id = :id")
                .param("id", id).param("role", role.code()).update();
    }

    /** Sets the password and makes an invited member active. */
    void setPassword(UUID id, String passwordHash) {
        jdbc.sql("""
                        update members set password_hash = :hash, updated_at = now(),
                               status = case when status = 'invited' then 'active' else status end
                        where id = :id""")
                .param("id", id).param("hash", passwordHash).update();
    }

    /** Remembers how the last invite email went, so admins see failures and can resend. */
    void recordInviteEmail(UUID id, String error) {
        jdbc.sql("""
                        update members set last_email_error = :error,
                               invite_sent_at = case when cast(:error as text) is null then now() else invite_sent_at end
                        where id = :id""")
                .param("id", id).param("error", error).update();
    }

    void recordSignIn(UUID id) {
        jdbc.sql("update members set last_sign_in_at = now() where id = :id").param("id", id).update();
    }

    /** Active superusers other than {@code except}. */
    int countOtherActiveSuperusers(UUID clubId, UUID except) {
        return jdbc.sql("""
                        select count(*) from members
                        where club_id = :club and role = 'superuser' and status = 'active' and id <> :except""")
                .param("club", clubId).param("except", except).query(Integer.class).single();
    }

    boolean anySuperuser(UUID clubId) {
        return jdbc.sql("select exists (select 1 from members where club_id = :club and role = 'superuser' and status <> 'deactivated')")
                .param("club", clubId).query(Boolean.class).single();
    }

    // ---------- email tokens ----------

    enum Purpose {
        INVITE("invite"), PASSWORD_RESET("password_reset");

        final String code;

        Purpose(String code) {
            this.code = code;
        }
    }

    record TokenRow(UUID id, UUID memberId, String purpose, boolean expired, boolean used) {
    }

    /** Makes any outstanding links of this kind unusable, e.g. when a new one is sent. */
    void expireTokens(UUID memberId, Purpose purpose) {
        jdbc.sql("""
                        update member_tokens set expires_at = now()
                        where member_id = :member and purpose = :purpose and used_at is null and expires_at > now()""")
                .param("member", memberId).param("purpose", purpose.code).update();
    }

    void insertToken(UUID memberId, Purpose purpose, String tokenHash, Duration ttl, UUID createdBy) {
        jdbc.sql("""
                        insert into member_tokens (member_id, purpose, token_hash, expires_at, created_by)
                        values (:member, :purpose, :hash, now() + cast(:secs as double precision) * interval '1 second', :by)""")
                .param("member", memberId).param("purpose", purpose.code).param("hash", tokenHash)
                .param("secs", ttl.toSeconds()).param("by", createdBy).update();
    }

    Optional<TokenRow> findToken(String tokenHash) {
        return jdbc.sql("""
                        select id, member_id, purpose, expires_at <= now() as expired, used_at is not null as used
                        from member_tokens where token_hash = :hash""")
                .param("hash", tokenHash)
                .query((rs, n) -> new TokenRow(rs.getObject("id", UUID.class), rs.getObject("member_id", UUID.class),
                        rs.getString("purpose"), rs.getBoolean("expired"), rs.getBoolean("used")))
                .optional();
    }

    /** True if this call used the token; false if it was already used, so a link works once. */
    boolean useToken(UUID id) {
        return jdbc.sql("update member_tokens set used_at = now() where id = :id and used_at is null and expires_at > now()")
                .param("id", id).update() == 1;
    }

    // ---------- sessions ----------

    record SessionRow(UUID sessionId, UUID memberId, UUID clubId, Role role, String email, String displayName,
                      boolean stale) {
    }

    UUID insertSession(UUID memberId, String tokenHash, String userAgent, Duration ttl) {
        return jdbc.sql("""
                        insert into sessions (member_id, token_hash, user_agent, expires_at)
                        values (:member, :hash, :agent, now() + cast(:secs as double precision) * interval '1 second')
                        returning id""")
                .param("member", memberId).param("hash", tokenHash).param("agent", userAgent)
                .param("secs", ttl.toSeconds())
                .query(UUID.class).single();
    }

    /** A live session of an active member; {@code stale} means it hasn't been extended for an hour. */
    Optional<SessionRow> findLiveSession(String tokenHash) {
        return jdbc.sql("""
                        select s.id, s.member_id, m.club_id, m.role, m.email, m.display_name,
                               s.last_used_at < now() - interval '1 hour' as stale
                        from sessions s join members m on m.id = s.member_id
                        where s.token_hash = :hash and s.revoked_at is null and s.expires_at > now()
                          and m.status = 'active'""")
                .param("hash", tokenHash)
                .query((rs, n) -> new SessionRow(rs.getObject("id", UUID.class), rs.getObject("member_id", UUID.class),
                        rs.getObject("club_id", UUID.class), Role.valueOf(rs.getString("role").toUpperCase()),
                        rs.getString("email"), rs.getString("display_name"), rs.getBoolean("stale")))
                .optional();
    }

    /** Slides the expiry forward: a session ends after a period without use. */
    void extendSession(UUID sessionId, Duration ttl) {
        jdbc.sql("""
                        update sessions set last_used_at = now(), expires_at = now() + cast(:secs as double precision) * interval '1 second'
                        where id = :id""")
                .param("id", sessionId).param("secs", ttl.toSeconds()).update();
    }

    void revokeSession(UUID sessionId) {
        jdbc.sql("update sessions set revoked_at = now() where id = :id and revoked_at is null")
                .param("id", sessionId).update();
    }

    /** Signs a member out everywhere, except optionally the session making the change. */
    void revokeSessions(UUID memberId, UUID except) {
        jdbc.sql("""
                        update sessions set revoked_at = now()
                        where member_id = :member and revoked_at is null
                          and (cast(:except as uuid) is null or id <> cast(:except as uuid))""")
                .param("member", memberId).param("except", except).update();
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toInstant();
    }
}
