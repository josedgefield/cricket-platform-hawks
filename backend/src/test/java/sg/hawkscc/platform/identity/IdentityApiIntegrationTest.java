package sg.hawkscc.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import sg.hawkscc.platform.SignIn;
import sg.hawkscc.platform.TestcontainersConfiguration;

/**
 * Invite-only sign-in and member management through the real HTTP API and a real Postgres.
 * Emails go to the in-memory outbox (hawks.mail.transport=outbox), where tests read the links.
 * Each test uses fresh email addresses and never changes the shared test superuser.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class IdentityApiIntegrationTest {

    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");
    private static final String GOOD_PASSWORD = "long enough phrase 42";

    @Value("${local.server.port}")
    int port;

    @Value("${hawks.identity.dev-superuser.email}")
    String superEmail;

    @Value("${hawks.identity.dev-superuser.password}")
    String superPassword;

    @Autowired
    MailGateway mail;

    @Autowired
    JdbcClient jdbc;

    RestClient http;
    String superToken;

    @BeforeEach
    void setUp() {
        http = RestClient.create("http://localhost:" + port);
        superToken = SignIn.token(http, superEmail, superPassword);
    }

    // ---------- helpers ----------

    record Result(int status, Map<String, Object> body) {
        Object get(String key) {
            return body == null ? null : body.get(key);
        }
    }

    private Result call(HttpMethod method, String path, String token, Object body) {
        var spec = http.method(method).uri(path).headers(h -> {
            if (token != null) {
                h.setBearerAuth(token);
            }
        });
        if (body != null) {
            spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        return spec.exchange((req, res) -> {
            Map<String, Object> parsed = null;
            if (res.getStatusCode().value() != 204 && res.getHeaders().getContentLength() != 0) {
                try {
                    parsed = res.bodyTo(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
                } catch (RuntimeException ignored) {
                    // a list or an empty body; callers that need it use list()
                }
            }
            return new Result(res.getStatusCode().value(), parsed);
        });
    }

    private List<Map<String, Object>> list(String path, String token) {
        return http.get().uri(path).headers(h -> h.setBearerAuth(token)).retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private static String unique(String name) {
        return name + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.org";
    }

    private Result invite(String token, String email, String name, String role) {
        return call(HttpMethod.POST, "/api/admin/members", token,
                role == null ? Map.of("email", email, "displayName", name)
                        : Map.of("email", email, "displayName", name, "role", role));
    }

    /** The token from the newest email to this address. */
    private String linkToken(String email) {
        var sent = ((OutboxMailGateway) mail).sentTo(email);
        assertThat(sent).as("emails to " + email).isNotEmpty();
        Matcher m = TOKEN.matcher(sent.getLast().text());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    /** Invites, accepts, and returns the new member's id and session token. */
    private String[] join(String email, String name, String role) {
        Result invited = invite(superToken, email, name, role);
        assertThat(invited.status()).isEqualTo(201);
        Result accepted = call(HttpMethod.POST, "/api/auth/invitations/" + linkToken(email) + "/accept", null,
                Map.of("password", GOOD_PASSWORD));
        assertThat(accepted.status()).isEqualTo(200);
        return new String[] {(String) invited.get("id"), (String) accepted.get("token")};
    }

    // ---------- invites and sign-in ----------

    @Test
    void anInvitedPlayerSetsAPasswordSignsInAndOut() {
        String email = unique("Player");
        Result invited = invite(superToken, email, "New Player", null);
        assertThat(invited.status()).isEqualTo(201);
        assertThat(invited.get("status")).isEqualTo("invited");
        assertThat(invited.get("role")).isEqualTo("player");
        assertThat(invited.get("inviteEmailError")).isNull();
        assertThat(invited.get("inviteSentAt")).isNotNull();

        String link = linkToken(email.toLowerCase());
        assertThat(((OutboxMailGateway) mail).sentTo(email).getLast().text())
                .contains("http://localhost:8081/accept-invite?token=");
        Result info = call(HttpMethod.GET, "/api/auth/invitations/" + link, null, null);
        assertThat(info.status()).isEqualTo(200);
        assertThat(info.get("displayName")).isEqualTo("New Player");
        assertThat(info.get("clubName")).isEqualTo("Hawks Cricket Club");

        Result weak = call(HttpMethod.POST, "/api/auth/invitations/" + link + "/accept", null, Map.of("password", "password1"));
        assertThat(weak.status()).isEqualTo(400);
        assertThat((String) weak.get("detail")).contains("too common");
        Result tooShort = call(HttpMethod.POST, "/api/auth/invitations/" + link + "/accept", null, Map.of("password", "short"));
        assertThat((String) tooShort.get("detail")).contains("at least 8");

        Result accepted = call(HttpMethod.POST, "/api/auth/invitations/" + link + "/accept", null,
                Map.of("password", GOOD_PASSWORD));
        assertThat(accepted.status()).isEqualTo(200);
        String token = (String) accepted.get("token");

        Result me = call(HttpMethod.GET, "/api/me", token, null);
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.get("status")).isEqualTo("active");
        assertThat(me.get("email")).isEqualTo(email.toLowerCase());

        // The link works once.
        assertThat(call(HttpMethod.POST, "/api/auth/invitations/" + link + "/accept", null,
                Map.of("password", GOOD_PASSWORD)).status()).isEqualTo(410);

        // Sign in again with the password (email is case-insensitive), then sign out.
        String second = SignIn.token(http, email.toUpperCase(), GOOD_PASSWORD);
        assertThat(call(HttpMethod.POST, "/api/auth/sign-out", second, null).status()).isEqualTo(204);
        assertThat(call(HttpMethod.GET, "/api/me", second, null).status()).isEqualTo(401);
        assertThat(call(HttpMethod.GET, "/api/me", token, null).status()).isEqualTo(200);
    }

    @Test
    void unknownAndBadLinksAreRefused() {
        assertThat(call(HttpMethod.GET, "/api/auth/invitations/not-a-real-token", null, null).status()).isEqualTo(404);
        String email = unique("Late");
        invite(superToken, email, "Late Joiner", null);
        String link = linkToken(email);
        jdbc.sql("update member_tokens set expires_at = now() - interval '1 minute' where token_hash = :h")
                .param("h", Tokens.hash(link)).update();
        Result expired = call(HttpMethod.GET, "/api/auth/invitations/" + link, null, null);
        assertThat(expired.status()).isEqualTo(410);
        assertThat((String) expired.get("detail")).contains("expired");
    }

    @Test
    void signInErrorsDontRevealWhoIsAMemberAndRepeatedFailuresLockTheEmail() {
        String email = unique("Locked");
        join(email, "Locked Out", null);
        Result unknown = call(HttpMethod.POST, "/api/auth/sign-in", null,
                Map.of("email", unique("Nobody"), "password", GOOD_PASSWORD));
        Result wrong = call(HttpMethod.POST, "/api/auth/sign-in", null, Map.of("email", email, "password", "wrong password"));
        assertThat(unknown.status()).isEqualTo(401);
        assertThat(wrong.status()).isEqualTo(401);
        assertThat(unknown.get("detail")).isEqualTo(wrong.get("detail"));

        for (int i = 0; i < 4; i++) {
            call(HttpMethod.POST, "/api/auth/sign-in", null, Map.of("email", email, "password", "wrong password"));
        }
        Result locked = call(HttpMethod.POST, "/api/auth/sign-in", null, Map.of("email", email, "password", GOOD_PASSWORD));
        assertThat(locked.status()).isEqualTo(429);
    }

    @Test
    void passwordResetSignsOutOtherDevicesAndTheOldPasswordStopsWorking() {
        String email = unique("Forgetful");
        String oldSession = join(email, "Forgetful Player", null)[1];

        assertThat(call(HttpMethod.POST, "/api/auth/password-reset", null, Map.of("email", email)).status()).isEqualTo(202);
        String nobody = unique("Nobody");
        assertThat(call(HttpMethod.POST, "/api/auth/password-reset", null, Map.of("email", nobody)).status()).isEqualTo(202);
        assertThat(((OutboxMailGateway) mail).sentTo(nobody)).isEmpty();

        String link = linkToken(email);
        assertThat(((OutboxMailGateway) mail).sentTo(email).getLast().text()).contains("/reset-password?token=");
        Result reset = call(HttpMethod.POST, "/api/auth/password-reset/" + link, null,
                Map.of("password", "a brand new phrase 7"));
        assertThat(reset.status()).isEqualTo(200);
        assertThat(call(HttpMethod.GET, "/api/me", (String) reset.get("token"), null).status()).isEqualTo(200);
        assertThat(call(HttpMethod.GET, "/api/me", oldSession, null).status()).isEqualTo(401);
        assertThat(call(HttpMethod.POST, "/api/auth/sign-in", null, Map.of("email", email, "password", GOOD_PASSWORD))
                .status()).isEqualTo(401);
        SignIn.token(http, email, "a brand new phrase 7");
    }

    @Test
    void membersEditTheirOwnProfileAndChangeTheirPassword() {
        String email = unique("Self");
        String token = join(email, "Self Service", null)[1];
        Result edited = call(HttpMethod.PATCH, "/api/me", token, Map.of("displayName", "Self Served", "phone", "+65 9123 4567"));
        assertThat(edited.get("displayName")).isEqualTo("Self Served");
        assertThat(edited.get("phone")).isEqualTo("+65 9123 4567");

        assertThat(call(HttpMethod.POST, "/api/me/password", token,
                Map.of("currentPassword", "not it at all", "newPassword", "another long phrase")).status()).isEqualTo(400);
        assertThat(call(HttpMethod.POST, "/api/me/password", token,
                Map.of("currentPassword", GOOD_PASSWORD, "newPassword", "another long phrase")).status()).isEqualTo(204);
        assertThat(call(HttpMethod.GET, "/api/me", token, null).status()).isEqualTo(200);
        SignIn.token(http, email, "another long phrase");
    }

    // ---------- who may manage whom ----------

    @Test
    void adminsManagePlayersButOnlyViewAdminsAndSuperusers() {
        String adminEmail = unique("Admin");
        String admin = join(adminEmail, "Club Admin", "admin")[1];
        String superId = (String) call(HttpMethod.GET, "/api/me", superToken, null).get("id");

        // Admins invite and manage players.
        String playerEmail = unique("Managed");
        Result player = invite(admin, playerEmail, "Managed Player", null);
        assertThat(player.status()).isEqualTo(201);
        assertThat(player.get("manageable")).isEqualTo(true);
        String playerId = (String) player.get("id");
        assertThat(call(HttpMethod.PATCH, "/api/admin/members/" + playerId, admin, Map.of("phone", "+65 8000 0000"))
                .get("phone")).isEqualTo("+65 8000 0000");

        // ...but can't create admins, touch superusers, or change roles.
        assertThat(invite(admin, unique("Sneaky"), "Sneaky Admin", "admin").status()).isEqualTo(403);
        assertThat(call(HttpMethod.POST, "/api/admin/members/" + superId + "/deactivate", admin, null).status()).isEqualTo(403);
        assertThat(call(HttpMethod.PATCH, "/api/admin/members/" + superId, admin, Map.of("displayName", "X")).status())
                .isEqualTo(403);
        assertThat(call(HttpMethod.PUT, "/api/admin/members/" + playerId + "/role", admin, Map.of("role", "admin")).status())
                .isEqualTo(403);

        // They can see everyone, and the list says what they may change.
        var all = list("/api/admin/members", admin);
        var superRow = all.stream().filter(m -> superId.equals(m.get("id"))).findFirst().orElseThrow();
        assertThat(superRow.get("manageable")).isEqualTo(false);
        assertThat(list("/api/admin/members?status=invited&q=managed", admin))
                .extracting(m -> m.get("id")).contains(playerId);

        // Players can't use the admin API at all; anonymous callers must sign in.
        String playerToken = join(unique("Plain"), "Plain Player", null)[1];
        assertThat(call(HttpMethod.GET, "/api/admin/members", playerToken, null).status()).isEqualTo(403);
        assertThat(call(HttpMethod.GET, "/api/admin/members", null, null).status()).isEqualTo(401);
        assertThat(call(HttpMethod.POST, "/api/admin/stats/competitions", playerToken,
                Map.of("source", "sca", "name", "Nope")).status()).isEqualTo(403);
    }

    @Test
    void onlySuperusersChangeRolesAndNobodyChangesTheirOwn() {
        String[] player = join(unique("Promoted"), "Promoted Player", null);
        Result promoted = call(HttpMethod.PUT, "/api/admin/members/" + player[0] + "/role", superToken, Map.of("role", "admin"));
        assertThat(promoted.status()).isEqualTo(200);
        assertThat(promoted.get("role")).isEqualTo("admin");
        // A role change signs them out everywhere, so new permissions apply from the next sign-in.
        assertThat(call(HttpMethod.GET, "/api/me", player[1], null).status()).isEqualTo(401);

        String superId = (String) call(HttpMethod.GET, "/api/me", superToken, null).get("id");
        assertThat(call(HttpMethod.PUT, "/api/admin/members/" + superId + "/role", superToken, Map.of("role", "player"))
                .status()).isEqualTo(403);
        assertThat(call(HttpMethod.POST, "/api/admin/members/" + superId + "/deactivate", superToken, null).status())
                .isEqualTo(403);
        assertThat(call(HttpMethod.PUT, "/api/admin/members/" + player[0] + "/role", superToken, Map.of("role", "captain"))
                .status()).isEqualTo(400);
    }

    @Test
    void deactivatingSignsOutAndBlocksSignInUntilReactivated() {
        String email = unique("Leaver");
        String[] member = join(email, "Leaving Player", null);
        Result off = call(HttpMethod.POST, "/api/admin/members/" + member[0] + "/deactivate", superToken, null);
        assertThat(off.get("status")).isEqualTo("deactivated");
        assertThat(call(HttpMethod.GET, "/api/me", member[1], null).status()).isEqualTo(401);
        assertThat(call(HttpMethod.POST, "/api/auth/sign-in", null, Map.of("email", email, "password", GOOD_PASSWORD))
                .status()).isEqualTo(401);

        Result on = call(HttpMethod.POST, "/api/admin/members/" + member[0] + "/reactivate", superToken, null);
        assertThat(on.get("status")).isEqualTo("active");
        SignIn.token(http, email, GOOD_PASSWORD);

        long audited = jdbc.sql("select count(*) from audit_log where target_id = :id and action in ('member.deactivated', 'member.reactivated')")
                .param("id", UUID.fromString(member[0])).query(Long.class).single();
        assertThat(audited).isEqualTo(2);
    }

    @Test
    void emailCanBeFixedBeforeTheInviteIsAcceptedButNotAfter() {
        String typo = unique("Tpyo");
        String id = (String) invite(superToken, typo, "Typo Player", null).get("id");
        String fixed = unique("Typo");
        Result edited = call(HttpMethod.PATCH, "/api/admin/members/" + id, superToken, Map.of("email", fixed));
        assertThat(edited.get("email")).isEqualTo(fixed);
        // The first link no longer works; the new address got a fresh one.
        String oldLink = linkToken(typo);
        assertThat(call(HttpMethod.GET, "/api/auth/invitations/" + oldLink, null, null).status()).isEqualTo(410);
        assertThat(call(HttpMethod.POST, "/api/auth/invitations/" + linkToken(fixed) + "/accept", null,
                Map.of("password", GOOD_PASSWORD)).status()).isEqualTo(200);

        assertThat(call(HttpMethod.PATCH, "/api/admin/members/" + id, superToken, Map.of("email", unique("Other")))
                .status()).isEqualTo(409);
        assertThat(invite(superToken, fixed, "Duplicate", null).status()).isEqualTo(409);
        assertThat(call(HttpMethod.POST, "/api/admin/members/" + id + "/resend-invite", superToken, null).status())
                .isEqualTo(409);
    }

    @Test
    void resendingAnInviteReplacesTheOldLink() {
        String email = unique("Resend");
        String id = (String) invite(superToken, email, "Resend Player", null).get("id");
        String first = linkToken(email);
        assertThat(call(HttpMethod.POST, "/api/admin/members/" + id + "/resend-invite", superToken, null).status())
                .isEqualTo(200);
        String second = linkToken(email);
        assertThat(second).isNotEqualTo(first);
        assertThat(call(HttpMethod.GET, "/api/auth/invitations/" + first, null, null).status()).isEqualTo(410);
        assertThat(call(HttpMethod.GET, "/api/auth/invitations/" + second, null, null).status()).isEqualTo(200);
    }

    @Test
    void invalidInvitesAreRejectedWithReasons() {
        Result bad = invite(superToken, "not-an-email", "", null);
        assertThat(bad.status()).isEqualTo(400);
        assertThat((List<?>) bad.get("errors")).hasSize(2);
    }
}
