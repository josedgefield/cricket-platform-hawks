package sg.hawkscc.platform.identity;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import sg.hawkscc.platform.audit.AuditLog;
import sg.hawkscc.platform.club.ClubContext;

/**
 * Sign-in, sessions and the emailed links (invite acceptance, password reset). Sessions are
 * opaque bearer tokens; only their hashes are stored, and they can be revoked at once.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    static final Duration INVITE_VALID_FOR = Duration.ofDays(7);
    static final Duration RESET_VALID_FOR = Duration.ofHours(1);
    private static final Duration PLAYER_IDLE = Duration.ofDays(60);
    private static final Duration ADMIN_IDLE = Duration.ofDays(14);

    private static final String WRONG = "Email or password is wrong.";
    private static final String LOCKED = "Too many sign-in attempts. Wait 15 minutes, or reset your password.";

    private final IdentityRepository repo;
    private final PasswordEncoder encoder;
    private final IdentityMailer mailer;
    private final AuditLog audit;
    private final ClubContext club;
    private final TransactionTemplate tx;
    private final String dummyHash;

    private final AttemptLimiter failedByEmail;
    private final AttemptLimiter failedByIp;
    private final AttemptLimiter resetsByEmail;
    private final AttemptLimiter resetsByIp;

    public AuthService(IdentityRepository repo, PasswordEncoder encoder, IdentityMailer mailer, AuditLog audit,
                       ClubContext club, TransactionTemplate tx, Clock clock) {
        this.repo = repo;
        this.encoder = encoder;
        this.mailer = mailer;
        this.audit = audit;
        this.club = club;
        this.tx = tx;
        // Checked when there's no account, so a wrong email takes as long as a wrong password.
        this.dummyHash = encoder.encode("no-such-member-" + Tokens.newToken());
        this.failedByEmail = new AttemptLimiter(5, Duration.ofMinutes(15), clock);
        this.failedByIp = new AttemptLimiter(50, Duration.ofMinutes(15), clock);
        this.resetsByEmail = new AttemptLimiter(3, Duration.ofHours(1), clock);
        this.resetsByIp = new AttemptLimiter(10, Duration.ofHours(1), clock);
    }

    public record SignedIn(String token, MemberView member) {
    }

    public record Invitation(String email, String displayName, String clubName) {
    }

    static Duration idleTimeout(Role role) {
        return role == Role.PLAYER ? PLAYER_IDLE : ADMIN_IDLE;
    }

    /** The signed-in member for a bearer token, if the session is live and the member active. */
    public Optional<CurrentMember> authenticate(String token) {
        if (token == null || token.isBlank() || token.length() > 100) {
            return Optional.empty();
        }
        return repo.findLiveSession(Tokens.hash(token)).map(s -> {
            if (s.stale()) {
                repo.extendSession(s.sessionId(), idleTimeout(s.role()));
            }
            return new CurrentMember(s.memberId(), s.clubId(), s.sessionId(), s.role(), s.email(), s.displayName());
        });
    }

    public SignedIn signIn(String email, String password, String ip, String userAgent) {
        String key = Tokens.normaliseEmail(email);
        if (failedByIp.blocked(ip) || failedByEmail.blocked(key)) {
            throw new IdentityException(HttpStatus.TOO_MANY_REQUESTS, LOCKED);
        }
        var member = repo.findMemberByEmail(club.clubId(), key);
        String hash = member.map(IdentityRepository.MemberRow::passwordHash).orElse(null);
        boolean matches = encoder.matches(password == null ? "" : password, hash == null ? dummyHash : hash);
        if (!matches || member.get().status() != MemberStatus.ACTIVE) {
            failedByEmail.record(key);
            failedByIp.record(ip);
            throw new IdentityException(HttpStatus.UNAUTHORIZED, WRONG);
        }
        failedByEmail.reset(key);
        var m = member.get();
        String token = tx.execute(s -> {
            repo.recordSignIn(m.id());
            return newSession(m, userAgent);
        });
        log.info("Member {} signed in", m.id());
        return new SignedIn(token, MemberView.of(repo.findMember(club.clubId(), m.id()).orElseThrow(), false));
    }

    public void signOut(CurrentMember me) {
        repo.revokeSession(me.sessionId());
    }

    /** Who an invite link is for, so the app can greet them before they choose a password. */
    public Invitation invitation(String token) {
        var t = usableToken(token, IdentityRepository.Purpose.INVITE);
        var m = repo.findMember(club.clubId(), t.memberId()).orElseThrow(this::linkInvalid);
        if (m.status() != MemberStatus.INVITED) {
            throw linkUsed();
        }
        return new Invitation(m.email(), m.displayName(), club.current().name());
    }

    /** Sets the first password, activates the member and signs them in. */
    public SignedIn acceptInvite(String token, String password, String userAgent) {
        var t = usableToken(token, IdentityRepository.Purpose.INVITE);
        var m = repo.findMember(club.clubId(), t.memberId()).orElseThrow(this::linkInvalid);
        if (m.status() != MemberStatus.INVITED) {
            throw linkUsed();
        }
        PasswordPolicy.check(password, m.email());
        String session = tx.execute(s -> {
            if (!repo.useToken(t.id())) {
                throw linkUsed();
            }
            repo.setPassword(m.id(), encoder.encode(password));
            repo.recordSignIn(m.id());
            audit.record(m.clubId(), m.id(), "member.invite_accepted", "member", m.id(), Map.of());
            return newSession(m, userAgent);
        });
        log.info("Member {} accepted their invite", m.id());
        return new SignedIn(session, MemberView.of(repo.findMember(club.clubId(), m.id()).orElseThrow(), false));
    }

    /**
     * Emails a reset link if the address belongs to an active member. Always looks the same to
     * the caller, so it can't be used to find out who is a member.
     */
    public void requestPasswordReset(String email, String ip) {
        String key = Tokens.normaliseEmail(email);
        if (resetsByIp.blocked(ip) || resetsByEmail.blocked(key)) {
            return;
        }
        resetsByIp.record(ip);
        resetsByEmail.record(key);
        var member = repo.findMemberByEmail(club.clubId(), key)
                .filter(m -> m.status() == MemberStatus.ACTIVE);
        if (member.isEmpty()) {
            return;
        }
        var m = member.get();
        String token = Tokens.newToken();
        tx.executeWithoutResult(s -> {
            repo.expireTokens(m.id(), IdentityRepository.Purpose.PASSWORD_RESET);
            repo.insertToken(m.id(), IdentityRepository.Purpose.PASSWORD_RESET, Tokens.hash(token), RESET_VALID_FOR, null);
            audit.record(m.clubId(), null, "member.password_reset_requested", "member", m.id(), Map.of());
        });
        mailer.sendPasswordReset(m.email(), m.displayName(), token, RESET_VALID_FOR);
    }

    /** Sets a new password from a reset link, signs out every device, and signs in this one. */
    public SignedIn resetPassword(String token, String password, String userAgent) {
        var t = usableToken(token, IdentityRepository.Purpose.PASSWORD_RESET);
        var m = repo.findMember(club.clubId(), t.memberId()).orElseThrow(this::linkInvalid);
        if (m.status() != MemberStatus.ACTIVE) {
            throw linkInvalid();
        }
        PasswordPolicy.check(password, m.email());
        String session = tx.execute(s -> {
            if (!repo.useToken(t.id())) {
                throw linkUsed();
            }
            repo.setPassword(m.id(), encoder.encode(password));
            repo.revokeSessions(m.id(), null);
            repo.recordSignIn(m.id());
            audit.record(m.clubId(), m.id(), "member.password_reset", "member", m.id(), Map.of());
            return newSession(m, userAgent);
        });
        failedByEmail.reset(m.email());
        return new SignedIn(session, MemberView.of(repo.findMember(club.clubId(), m.id()).orElseThrow(), false));
    }

    /** Changes the password and signs out every other device. */
    public void changePassword(CurrentMember me, String current, String next) {
        var m = repo.findMember(me.clubId(), me.memberId()).orElseThrow(IdentityException::notFound);
        if (m.passwordHash() == null || !encoder.matches(current == null ? "" : current, m.passwordHash())) {
            throw IdentityException.badRequest("Your current password is wrong.");
        }
        PasswordPolicy.check(next, m.email());
        tx.executeWithoutResult(s -> {
            repo.setPassword(m.id(), encoder.encode(next));
            repo.revokeSessions(m.id(), me.sessionId());
            audit.record(m.clubId(), m.id(), "member.password_changed", "member", m.id(), Map.of());
        });
    }

    private String newSession(IdentityRepository.MemberRow m, String userAgent) {
        String token = Tokens.newToken();
        String agent = userAgent == null ? null : userAgent.length() > 200 ? userAgent.substring(0, 200) : userAgent;
        repo.insertSession(m.id(), Tokens.hash(token), agent, idleTimeout(m.role()));
        return token;
    }

    private IdentityRepository.TokenRow usableToken(String token, IdentityRepository.Purpose purpose) {
        if (token == null || token.isBlank() || token.length() > 100) {
            throw linkInvalid();
        }
        var t = repo.findToken(Tokens.hash(token)).filter(r -> r.purpose().equals(purpose.code))
                .orElseThrow(this::linkInvalid);
        if (t.used()) {
            throw linkUsed();
        }
        if (t.expired()) {
            throw new IdentityException(HttpStatus.GONE, purpose == IdentityRepository.Purpose.INVITE
                    ? "This invite link has expired. Ask a club admin to send a new one."
                    : "This reset link has expired. Ask for a new one from the sign-in screen.");
        }
        return t;
    }

    private IdentityException linkInvalid() {
        return new IdentityException(HttpStatus.NOT_FOUND, "This link isn't valid. Check you opened the whole link from the email.");
    }

    private IdentityException linkUsed() {
        return new IdentityException(HttpStatus.GONE, "This link has already been used. Sign in with your email and password.");
    }
}
