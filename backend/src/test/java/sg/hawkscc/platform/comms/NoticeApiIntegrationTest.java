package sg.hawkscc.platform.comms;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import sg.hawkscc.platform.SignIn;
import sg.hawkscc.platform.TestcontainersConfiguration;
import sg.hawkscc.platform.identity.MailGateway;
import sg.hawkscc.platform.identity.OutboxMailGateway;

/** Notices through the real HTTP API and a real Postgres. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class NoticeApiIntegrationTest {

    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Value("${local.server.port}")
    int port;

    @Value("${hawks.identity.dev-superuser.email}")
    String superEmail;

    @Value("${hawks.identity.dev-superuser.password}")
    String superPassword;

    @Autowired
    MailGateway mail;

    RestClient http;
    String admin;

    @BeforeEach
    void setUp() {
        http = RestClient.create("http://localhost:" + port);
        admin = SignIn.token(http, superEmail, superPassword);
    }

    record Result(int status, Map<String, Object> body) {
        Object get(String key) {
            return body == null ? null : body.get(key);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> notice() {
            return (Map<String, Object>) get("notice");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> replies() {
            return (List<Map<String, Object>>) get("replies");
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
                    // a list or an empty body
                }
            }
            return new Result(res.getStatusCode().value(), parsed);
        });
    }

    private List<Map<String, Object>> notices(String token) {
        return http.get().uri("/api/notices").headers(h -> h.setBearerAuth(token)).retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    private static Map<String, Object> byId(List<Map<String, Object>> all, String id) {
        return all.stream().filter(n -> id.equals(n.get("id"))).findFirst().orElseThrow();
    }

    /** Invites a player and accepts. Returns {email, sessionToken}. */
    private String[] player(String name) {
        String email = name.toLowerCase().replace(' ', '.') + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.org";
        assertThat(call(HttpMethod.POST, "/api/admin/members", admin, Map.of("email", email, "displayName", name)).status())
                .isEqualTo(201);
        Matcher m = TOKEN.matcher(((OutboxMailGateway) mail).sentTo(email).getLast().text());
        assertThat(m.find()).isTrue();
        Result accepted = call(HttpMethod.POST, "/api/auth/invitations/" + m.group(1) + "/accept", null,
                Map.of("password", "long enough phrase 42"));
        assertThat(accepted.status()).isEqualTo(200);
        return new String[] {email, (String) accepted.get("token")};
    }

    private String publish(String title, boolean pinned, boolean email) {
        Result r = call(HttpMethod.POST, "/api/admin/notices", admin, Map.of("title", title, "body", "Details for " + title,
                "pinned", pinned, "emailMembers", email, "requestKey", UUID.randomUUID().toString()));
        assertThat(r.status()).isEqualTo(201);
        return (String) r.get("id");
    }

    @Test
    void adminsPublishAndMembersReadReactAndReply() {
        String[] p = player("Reading Player");
        String title = "Nets on Saturday " + UUID.randomUUID();
        String id = publish(title, false, false);

        Map<String, Object> listed = byId(notices(p[1]), id);
        assertThat(listed.get("title")).isEqualTo(title);
        assertThat(listed.get("unread")).isEqualTo(true);
        assertThat(listed.get("readCount")).isNull();   // players don't see who has read it

        Result opened = call(HttpMethod.GET, "/api/notices/" + id, p[1], null);
        assertThat(opened.status()).isEqualTo(200);
        assertThat(opened.notice().get("unread")).isEqualTo(false);
        assertThat(byId(notices(admin), id).get("readCount")).isEqualTo(1);

        Result reacted = call(HttpMethod.PUT, "/api/notices/" + id + "/reaction", p[1], Map.of("reaction", "cricket"));
        assertThat(reacted.notice().get("myReaction")).isEqualTo("cricket");
        assertThat(reacted.notice().get("reactions")).isEqualTo(Map.of("cricket", 1));
        // Choosing another replaces it; an unknown one is refused.
        reacted = call(HttpMethod.PUT, "/api/notices/" + id + "/reaction", p[1], Map.of("reaction", "like"));
        assertThat(reacted.notice().get("reactions")).isEqualTo(Map.of("like", 1));
        assertThat(call(HttpMethod.PUT, "/api/notices/" + id + "/reaction", p[1], Map.of("reaction", "angry")).status())
                .isEqualTo(400);
        java.util.HashMap<String, Object> none = new java.util.HashMap<>();
        none.put("reaction", null);
        assertThat(call(HttpMethod.PUT, "/api/notices/" + id + "/reaction", p[1], none).notice().get("myReaction")).isNull();

        Result replied = call(HttpMethod.POST, "/api/notices/" + id + "/replies", p[1], Map.of("body", "I'll be there"));
        assertThat(replied.status()).isEqualTo(201);
        assertThat(replied.replies()).hasSize(1);
        assertThat(replied.replies().getFirst().get("mine")).isEqualTo(true);
        assertThat(replied.replies().getFirst().get("authorName")).isEqualTo("Reading Player");
        assertThat(replied.notice().get("replyCount")).isEqualTo(1);
        assertThat(call(HttpMethod.POST, "/api/notices/" + id + "/replies", p[1], Map.of("body", " ")).status()).isEqualTo(400);

        // Players can't write, edit or withdraw notices; anonymous callers must sign in.
        assertThat(call(HttpMethod.POST, "/api/admin/notices", p[1], Map.of("title", "x", "body", "y", "requestKey", "k")).status())
                .isEqualTo(403);
        assertThat(call(HttpMethod.POST, "/api/admin/notices/" + id + "/withdraw", p[1], null).status()).isEqualTo(403);
        assertThat(call(HttpMethod.GET, "/api/notices", null, null).status()).isEqualTo(401);
    }

    @Test
    void repliesCanBeHiddenByTheirWriterOrAnAdminWithAReason() {
        String[] writer = player("Reply Writer");
        String[] other = player("Other Player");
        String id = publish("Team dinner " + UUID.randomUUID(), false, false);
        Result first = call(HttpMethod.POST, "/api/notices/" + id + "/replies", writer[1], Map.of("body", "Rude words"));
        String rude = (String) first.replies().getFirst().get("id");
        Result second = call(HttpMethod.POST, "/api/notices/" + id + "/replies", writer[1], Map.of("body", "Oops, typo"));
        String typo = (String) second.replies().get(1).get("id");

        assertThat(call(HttpMethod.POST, "/api/notices/" + id + "/replies/" + rude + "/hide", other[1], Map.of("reason", "x")).status())
                .isEqualTo(403);
        assertThat(call(HttpMethod.POST, "/api/notices/" + id + "/replies/" + rude + "/hide", admin, Map.of()).status())
                .isEqualTo(400);   // an admin must say why
        assertThat(call(HttpMethod.POST, "/api/notices/" + id + "/replies/" + rude + "/hide", admin,
                Map.of("reason", "Keep it friendly")).status()).isEqualTo(200);
        assertThat(call(HttpMethod.POST, "/api/notices/" + id + "/replies/" + typo + "/hide", writer[1], null).status())
                .isEqualTo(200);

        // Other players no longer see either; the writer sees that theirs were hidden, without the text.
        assertThat(call(HttpMethod.GET, "/api/notices/" + id, other[1], null).replies()).isEmpty();
        List<Map<String, Object>> mine = call(HttpMethod.GET, "/api/notices/" + id, writer[1], null).replies();
        assertThat(mine).hasSize(2);
        assertThat(mine.getFirst().get("hidden")).isEqualTo(true);
        assertThat(mine.getFirst().get("body")).isNull();
        assertThat(mine.getFirst().get("hiddenReason")).isEqualTo("Keep it friendly");
    }

    @Test
    void pinnedNoticesComeFirstAndEditsAndWithdrawalsWork() {
        String older = publish("Pinned " + UUID.randomUUID(), true, false);
        String newer = publish("Newer " + UUID.randomUUID(), false, false);
        List<Map<String, Object>> all = notices(admin);
        List<Object> ids = all.stream().map(n -> n.get("id")).toList();
        assertThat(ids.indexOf(older)).isLessThan(ids.indexOf(newer));

        Result edited = call(HttpMethod.PATCH, "/api/admin/notices/" + newer, admin, Map.of("title", "Newer, corrected"));
        assertThat(edited.get("title")).isEqualTo("Newer, corrected");
        assertThat(edited.get("editedAt")).isNotNull();
        // Unpinning alone isn't an edit of the text.
        Result unpinned = call(HttpMethod.PATCH, "/api/admin/notices/" + older, admin, Map.of("pinned", false));
        assertThat(unpinned.get("pinned")).isEqualTo(false);
        assertThat(unpinned.get("editedAt")).isNull();

        assertThat(call(HttpMethod.POST, "/api/admin/notices/" + newer + "/withdraw", admin, null).status()).isEqualTo(204);
        assertThat(notices(admin)).extracting(n -> n.get("id")).doesNotContain(newer);
        assertThat(call(HttpMethod.GET, "/api/notices/" + newer, admin, null).status()).isEqualTo(404);
    }

    @Test
    void aNoticeCanBeEmailedToActiveMembersAndARepeatedRequestPublishesOnce() {
        String[] p = player("Emailed Player");
        String title = "AGM date " + UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        Map<String, Object> body = Map.of("title", title, "body", "7pm at the clubhouse", "emailMembers", true, "requestKey", key);
        Result first = call(HttpMethod.POST, "/api/admin/notices", admin, body);
        assertThat(first.status()).isEqualTo(201);
        Result again = call(HttpMethod.POST, "/api/admin/notices", admin, body);
        assertThat(again.get("id")).isEqualTo(first.get("id"));
        assertThat(notices(admin).stream().filter(n -> title.equals(n.get("title")))).hasSize(1);

        var sent = ((OutboxMailGateway) mail).sentTo(p[0]).stream().filter(e -> e.subject().contains(title)).toList();
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().text()).contains("7pm at the clubhouse").contains("/notices/" + first.get("id"));

        @SuppressWarnings("unchecked")
        Map<String, Object> email = (Map<String, Object>) byId(notices(admin), (String) first.get("id")).get("email");
        assertThat(email.get("status")).isEqualTo("sent");
        assertThat(((Number) email.get("sent")).intValue()).isGreaterThanOrEqualTo(2);
        assertThat(byId(notices(p[1]), (String) first.get("id")).get("email")).isNull();
    }
}
