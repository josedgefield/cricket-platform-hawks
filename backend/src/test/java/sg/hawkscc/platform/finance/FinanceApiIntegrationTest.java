package sg.hawkscc.platform.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
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
import sg.hawkscc.platform.identity.MailGateway;
import sg.hawkscc.platform.identity.OutboxMailGateway;

/**
 * The ledger through the real HTTP API and a real Postgres. Each test invites its own members,
 * so the figures it checks are not affected by other tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class FinanceApiIntegrationTest {

    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");
    private static final String PASSWORD = "long enough phrase 42";

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
    String admin;

    @BeforeEach
    void setUp() {
        http = RestClient.create("http://localhost:" + port);
        admin = SignIn.token(http, superEmail, superPassword);
    }

    // ---------- helpers ----------

    record Result(int status, Map<String, Object> body) {
        Object get(String key) {
            return body == null ? null : body.get(key);
        }

        long cents(String key) {
            return ((Number) get(key)).longValue();
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list(String key) {
            return (List<Map<String, Object>>) get(key);
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
                    // empty body
                }
            }
            return new Result(res.getStatusCode().value(), parsed);
        });
    }

    /** Invites a player and accepts the invite. Returns {memberId, sessionToken}. */
    private String[] player(String name) {
        String email = name.toLowerCase().replace(' ', '.') + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.org";
        Result invited = call(HttpMethod.POST, "/api/admin/members", admin, Map.of("email", email, "displayName", name));
        assertThat(invited.status()).isEqualTo(201);
        var sent = ((OutboxMailGateway) mail).sentTo(email);
        Matcher m = TOKEN.matcher(sent.getLast().text());
        assertThat(m.find()).isTrue();
        Result accepted = call(HttpMethod.POST, "/api/auth/invitations/" + m.group(1) + "/accept", null,
                Map.of("password", PASSWORD));
        assertThat(accepted.status()).isEqualTo(200);
        return new String[] {(String) invited.get("id"), (String) accepted.get("token")};
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private Result charge(String memberId, String description, long cents, LocalDate due) {
        Map<String, Object> body = new HashMap<>(Map.of("memberIds", List.of(memberId), "description", description,
                "amountCents", cents, "requestKey", key()));
        if (due != null) {
            body.put("dueDate", due.toString());
        }
        return call(HttpMethod.POST, "/api/admin/finance/charges", admin, body);
    }

    private Result pay(String memberId, long cents, String requestKey) {
        return call(HttpMethod.POST, "/api/admin/finance/payments", admin,
                Map.of("memberId", memberId, "amountCents", cents, "method", "paynow", "requestKey", requestKey));
    }

    private Result adjust(Map<String, Object> body) {
        Map<String, Object> withKey = new HashMap<>(body);
        withKey.putIfAbsent("requestKey", key());
        return call(HttpMethod.POST, "/api/admin/finance/adjustments", admin, withKey);
    }

    private Result statement(String memberId) {
        return call(HttpMethod.GET, "/api/admin/finance/members/" + memberId + "/statement", admin, null);
    }

    private static Map<String, Object> byDescription(Result statement, String description) {
        return statement.list("charges").stream().filter(c -> description.equals(c.get("description")))
                .findFirst().orElseThrow();
    }

    // ---------- tests ----------

    @Test
    void aPlayerSeesWhatTheyOweAndOnlyAdminsUseTheFinanceApi() {
        String[] p = player("Owing Player");
        LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Singapore")).minusDays(1);
        assertThat(charge(p[0], "Season fee", 12_000, yesterday).status()).isEqualTo(201);
        assertThat(charge(p[0], "Match fee", 2_500, null).status()).isEqualTo(201);

        Result mine = call(HttpMethod.GET, "/api/me/statement", p[1], null);
        assertThat(mine.status()).isEqualTo(200);
        assertThat(mine.cents("balanceCents")).isEqualTo(14_500);
        assertThat(mine.cents("owedCents")).isEqualTo(14_500);
        assertThat(mine.cents("overdueCents")).isEqualTo(12_000);
        Map<String, Object> season = byDescription(mine, "Season fee");
        assertThat(season.get("status")).isEqualTo("open");
        assertThat(season.get("overdue")).isEqualTo(true);
        assertThat((String) season.get("reference")).startsWith("HWK-C-");
        assertThat(mine.list("entries")).hasSize(2);

        assertThat(call(HttpMethod.GET, "/api/admin/finance/overview", p[1], null).status()).isEqualTo(403);
        assertThat(call(HttpMethod.GET, "/api/admin/finance/members/" + p[0] + "/statement", p[1], null).status())
                .isEqualTo(403);
        assertThat(call(HttpMethod.POST, "/api/admin/finance/payments", p[1],
                Map.of("memberId", p[0], "amountCents", 14_500, "method", "cash", "requestKey", key())).status())
                .isEqualTo(403);
        assertThat(call(HttpMethod.GET, "/api/me/statement", null, null).status()).isEqualTo(401);

        Result overview = call(HttpMethod.GET, "/api/admin/finance/overview", admin, null);
        Map<String, Object> row = overview.list("members").stream().filter(m -> p[0].equals(m.get("memberId")))
                .findFirst().orElseThrow();
        assertThat(((Number) row.get("balanceCents")).longValue()).isEqualTo(14_500);
        assertThat(((Number) row.get("overdueCents")).longValue()).isEqualTo(12_000);
        assertThat(row.get("openCharges")).isEqualTo(2);
        assertThat(overview.cents("owedCents")).isGreaterThanOrEqualTo(14_500);
    }

    @Test
    void paymentsPayTheOldestChargeFirstAndARepeatedRequestChangesNothing() {
        String[] p = player("Paying Player");
        charge(p[0], "First", 5_000, LocalDate.of(2026, 1, 31));
        charge(p[0], "Second", 5_000, LocalDate.of(2026, 2, 28));

        String requestKey = key();
        Result paid = pay(p[0], 7_500, requestKey);
        assertThat(paid.status()).isEqualTo(201);
        assertThat(paid.cents("balanceCents")).isEqualTo(2_500);
        assertThat(byDescription(paid, "First").get("status")).isEqualTo("paid");
        assertThat(byDescription(paid, "Second").get("status")).isEqualTo("part_paid");

        // The same form sent again (double tap, retry after a timeout).
        Result again = pay(p[0], 7_500, requestKey);
        assertThat(again.status()).isEqualTo(201);
        assertThat(again.cents("balanceCents")).isEqualTo(2_500);
        assertThat(again.list("payments")).hasSize(1);

        // Overpaying leaves unused money, which pays the next charge automatically.
        Result over = pay(p[0], 4_000, key());
        assertThat(over.cents("balanceCents")).isEqualTo(-1_500);
        assertThat(over.cents("unusedCents")).isEqualTo(1_500);
        charge(p[0], "Third", 1_000, null);
        Result after = statement(p[0]);
        assertThat(byDescription(after, "Third").get("status")).isEqualTo("paid");
        assertThat(after.cents("unusedCents")).isEqualTo(500);
        assertThat(after.cents("balanceCents")).isEqualTo(-500);
    }

    @Test
    void aNamedChargeIsPaidFirst() {
        String[] p = player("Choosy Player");
        charge(p[0], "Old", 5_000, LocalDate.of(2026, 1, 31));
        Result raised = charge(p[0], "Kit", 3_000, null);
        assertThat(raised.status()).isEqualTo(201);
        String kit = (String) byDescription(statement(p[0]), "Kit").get("id");
        Result paid = call(HttpMethod.POST, "/api/admin/finance/payments", admin, Map.of("memberId", p[0],
                "amountCents", 3_000, "method", "cash", "chargeId", kit, "requestKey", key()));
        assertThat(byDescription(paid, "Kit").get("status")).isEqualTo("paid");
        assertThat(byDescription(paid, "Old").get("status")).isEqualTo("open");
    }

    @Test
    void cancellingAPaidChargeMovesTheMoneyToTheNextOne() {
        String[] p = player("Mischarged Player");
        charge(p[0], "Wrong", 5_000, LocalDate.of(2026, 1, 31));
        charge(p[0], "Right", 5_000, LocalDate.of(2026, 2, 28));
        Result paid = pay(p[0], 5_000, key());
        String wrong = (String) byDescription(paid, "Wrong").get("id");
        assertThat(byDescription(paid, "Wrong").get("status")).isEqualTo("paid");

        Result voided = adjust(Map.of("kind", "void", "chargeId", wrong, "reason", "Charged twice"));
        assertThat(voided.status()).isEqualTo(201);
        assertThat(byDescription(voided, "Wrong").get("status")).isEqualTo("void");
        assertThat(byDescription(voided, "Right").get("status")).isEqualTo("paid");
        assertThat(voided.cents("balanceCents")).isZero();

        assertThat(adjust(Map.of("kind", "void", "chargeId", wrong, "reason", "Again")).status()).isEqualTo(409);
    }

    @Test
    void waiversCreditsRefundsAndReversalsFollowTheLedgerRules() {
        String[] p = player("Adjusted Player");
        charge(p[0], "Fee", 10_000, null);
        String fee = (String) byDescription(statement(p[0]), "Fee").get("id");

        // A reason is required, and a waiver can't exceed what is unpaid.
        assertThat(adjust(Map.of("kind", "waiver", "chargeId", fee, "amountCents", 2_000, "reason", " ")).status())
                .isEqualTo(400);
        Result tooMuch = adjust(Map.of("kind", "waiver", "chargeId", fee, "amountCents", 10_001, "reason", "Student"));
        assertThat(tooMuch.status()).isEqualTo(409);
        assertThat((String) tooMuch.get("detail")).contains("S$100.00");
        Result waived = adjust(Map.of("kind", "waiver", "chargeId", fee, "amountCents", 2_000, "reason", "Student rate"));
        assertThat(waived.cents("balanceCents")).isEqualTo(8_000);

        // A credit pays charges like money.
        Result credited = adjust(Map.of("kind", "credit", "memberId", p[0], "amountCents", 3_000, "reason", "Umpired twice"));
        assertThat(credited.cents("balanceCents")).isEqualTo(5_000);
        assertThat(byDescription(credited, "Fee").get("status")).isEqualTo("part_paid");

        // Overpay, then refund only the unused part.
        Result paid = pay(p[0], 6_000, key());
        assertThat(paid.cents("unusedCents")).isEqualTo(1_000);
        String payment = (String) paid.list("payments").getFirst().get("id");
        assertThat(adjust(Map.of("kind", "refund", "paymentId", payment, "amountCents", 1_001, "reason", "Overpaid")).status())
                .isEqualTo(409);
        Result refunded = adjust(Map.of("kind", "refund", "paymentId", payment, "reason", "Overpaid"));
        assertThat(refunded.status()).isEqualTo(201);
        assertThat(refunded.cents("balanceCents")).isZero();
        assertThat(refunded.cents("unusedCents")).isZero();

        // Cancelling the payment reopens the charge for what the payment had covered.
        Result reversed = adjust(Map.of("kind", "reversal", "paymentId", payment, "reason", "Wrong member"));
        assertThat(reversed.status()).isEqualTo(201);
        assertThat(reversed.cents("balanceCents")).isEqualTo(5_000);
        assertThat(byDescription(reversed, "Fee").get("status")).isEqualTo("part_paid");
        assertThat(reversed.list("payments").getFirst().get("reversed")).isEqualTo(true);
        assertThat(adjust(Map.of("kind", "reversal", "paymentId", payment, "reason", "Again")).status()).isEqualTo(409);

        // Every row is on the statement, and the last running balance is the balance.
        List<Map<String, Object>> entries = reversed.list("entries");
        assertThat(entries).extracting(e -> e.get("kind"))
                .containsExactly("charge", "waiver", "credit", "payment", "refund", "reversal");
        assertThat(((Number) entries.getLast().get("balanceCents")).longValue()).isEqualTo(5_000);

        // The player sees the same figures, without the admin's private note.
        Result mine = call(HttpMethod.GET, "/api/me/statement", p[1], null);
        assertThat(mine.cents("balanceCents")).isEqualTo(5_000);
    }

    @Test
    void chargingEveryoneSkipsDeactivatedMembers() {
        String[] active = player("Squad Player");
        String[] gone = player("Former Player");
        assertThat(call(HttpMethod.POST, "/api/admin/members/" + gone[0] + "/deactivate", admin, null).status()).isEqualTo(200);

        String description = "Nets " + UUID.randomUUID();
        Result raised = call(HttpMethod.POST, "/api/admin/finance/charges", admin,
                Map.of("everyone", true, "description", description, "amountCents", 1_500, "requestKey", key()));
        assertThat(raised.status()).isEqualTo(201);
        assertThat(((Number) raised.get("count")).intValue()).isGreaterThanOrEqualTo(2);

        assertThat(statement(active[0]).list("charges")).extracting(c -> c.get("description")).contains(description);
        assertThat(statement(gone[0]).list("charges")).isEmpty();

        Result refused = charge(gone[0], "Late fee", 500, null);
        assertThat(refused.status()).isEqualTo(409);
        assertThat((String) refused.get("detail")).contains("deactivated");
    }

    @Test
    void invalidRequestsAreRefusedWithReasons() {
        String[] p = player("Careful Player");
        assertThat(charge(p[0], "Zero", 0, null).status()).isEqualTo(400);
        assertThat(charge(p[0], "Huge", 10_000_001, null).status()).isEqualTo(400);
        assertThat(call(HttpMethod.POST, "/api/admin/finance/charges", admin,
                Map.of("memberIds", List.of(), "description", "Nobody", "amountCents", 100, "requestKey", key())).status())
                .isEqualTo(400);
        assertThat(call(HttpMethod.POST, "/api/admin/finance/charges", admin,
                Map.of("memberIds", List.of(UUID.randomUUID().toString()), "description", "Ghost", "amountCents", 100,
                        "requestKey", key())).status()).isEqualTo(404);
        assertThat(call(HttpMethod.POST, "/api/admin/finance/payments", admin, Map.of("memberId", p[0],
                "amountCents", 100, "method", "cheque", "requestKey", key())).status()).isEqualTo(400);
        String tomorrow = LocalDate.now(ZoneId.of("Asia/Singapore")).plusDays(2).toString();
        assertThat(call(HttpMethod.POST, "/api/admin/finance/payments", admin, Map.of("memberId", p[0],
                "amountCents", 100, "method", "cash", "receivedOn", tomorrow, "requestKey", key())).status()).isEqualTo(400);
        assertThat(adjust(Map.of("kind", "discount", "memberId", p[0], "amountCents", 100, "reason", "x")).status())
                .isEqualTo(400);
        assertThat(adjust(Map.of("kind", "credit", "memberId", p[0], "reason", "No amount")).status()).isEqualTo(400);
        assertThat(statement(p[0]).list("entries")).isEmpty();
    }

    @Test
    void ledgerRowsCanNotBeChangedOrDeletedAndChangesAreAudited() {
        String[] p = player("Audited Player");
        charge(p[0], "Fee", 1_000, null);
        pay(p[0], 1_000, key());
        UUID member = UUID.fromString(p[0]);

        for (String table : List.of("charges", "payments", "payment_allocations")) {
            assertThatThrownBy(() -> jdbc.sql("update " + table + " set amount_cents = 1 where member_id = :m")
                    .param("m", member).update()).hasMessageContaining("append-only");
            assertThatThrownBy(() -> jdbc.sql("delete from " + table + " where member_id = :m")
                    .param("m", member).update()).hasMessageContaining("append-only");
        }
        assertThatThrownBy(() -> jdbc.sql("delete from audit_log").update()).hasMessageContaining("append-only");

        long audited = jdbc.sql("""
                        select count(*) from audit_log
                        where action in ('finance.charges_raised', 'finance.payment_recorded')
                          and (details ->> 'memberId' = :m or target_id in (select batch_id from charges where member_id = :id))""")
                .param("m", p[0]).param("id", member).query(Long.class).single();
        assertThat(audited).isEqualTo(2);
    }
}
