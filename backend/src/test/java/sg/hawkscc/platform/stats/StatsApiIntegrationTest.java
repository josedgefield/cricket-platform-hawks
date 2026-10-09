package sg.hawkscc.platform.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import sg.hawkscc.platform.TestcontainersConfiguration;
import sg.hawkscc.platform.stats.domain.Rate;

/**
 * Runs the whole app against a real Postgres. The CricHeroes BPL 2025 seed is loaded on
 * startup through the normal import path, so the first tests check the seed. Tests that
 * write use their own throwaway competition, so they never disturb the seeded data.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class StatsApiIntegrationTest {

    @Value("${local.server.port}")
    int port;

    @Value("${hawks.dev.admin-username}")
    String adminUser;

    @Value("${hawks.dev.admin-password}")
    String adminPassword;

    @Autowired
    JdbcClient jdbc;

    RestClient http;

    @BeforeEach
    void setUp() {
        http = RestClient.create("http://localhost:" + port);
    }

    // ---------- helpers ----------

    private List<StatsViews.PlayerStats> players(String source, UUID competitionId) {
        return http.get()
                .uri(b -> {
                    b.path("/api/stats/players").queryParam("source", source);
                    if (competitionId != null) {
                        b.queryParam("competitionId", competitionId);
                    }
                    return b.build();
                })
                .retrieve().body(new ParameterizedTypeReference<>() {
                });
    }

    private static StatsViews.PlayerStats named(List<StatsViews.PlayerStats> all, String name) {
        return all.stream().filter(p -> p.name().equals(name)).findFirst().orElseThrow();
    }

    private UUID competition(String name) {
        List<StatsViews.Competition> comps = http.get().uri("/api/stats/competitions").retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return comps.stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow().id();
    }

    /** A fresh competition for a test that writes data. */
    private UUID newCompetition() {
        return newCompetition("cricheroes");
    }

    private UUID newCompetition(String source) {
        String name = "Test " + UUID.randomUUID();
        http.post().uri("/api/admin/stats/competitions")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> h.setBasicAuth(adminUser, adminPassword))
                .body(Map.of("source", source, "name", name))
                .retrieve().toBodilessEntity();
        return competition(name);
    }

    private int link(String player, String source, String sourceName) {
        return http.post().uri("/api/admin/stats/player-links")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> h.setBasicAuth(adminUser, adminPassword))
                .body(Map.of("player", player, "source", source, "sourceName", sourceName))
                .exchange((req, res) -> res.getStatusCode().value());
    }

    private int importCsv(UUID competitionId, String kind, String csv, boolean authenticated) {
        return http.post().uri("/api/admin/stats/competitions/" + competitionId + "/imports/" + kind)
                .contentType(MediaType.valueOf("text/csv"))
                .headers(h -> {
                    if (authenticated) {
                        h.setBasicAuth(adminUser, adminPassword);
                    }
                })
                .body(csv)
                .exchange((req, res) -> res.getStatusCode().value());
    }

    private String lastRunStatus(UUID competitionId) {
        return jdbc.sql("select status from sync_runs where competition_id = :c order by finished_at desc, started_at desc limit 1")
                .param("c", competitionId).query(String.class).single();
    }

    // ---------- seeded data ----------

    @Test
    void seedLoadsRealCricHeroesFiguresWithoutInventingCounts() {
        UUID bpl = competition("BPL 2025");
        var all = players("cricheroes", bpl);

        var sandeep = named(all, "Sandeep Chandrasekharan Nair Roja");
        assertThat(sandeep.sources()).containsExactly("cricheroes");
        assertThat(sandeep.matches()).isEqualTo(4);
        assertThat(sandeep.batting().runs()).isEqualTo(149);
        // Not printed, but exactly one value fits the published SR and average; marked as recovered.
        assertThat(sandeep.batting().balls()).isEqualTo(113);
        assertThat(sandeep.batting().notOuts()).isEqualTo(1);
        assertThat(sandeep.recovered()).contains("batting.balls", "batting.notOuts");
        assertThat(sandeep.batting().strikeRate()).isEqualTo(new Rate(new BigDecimal("131.86"), false));
        assertThat(sandeep.fielding().dismissals()).isEqualTo(2);
        assertThat(sandeep.fielding().stumpings()).isNull();

        // Linked across sources, so shown under the name from player-links.csv.
        var shreyas = named(all, "Shreyas Puttur");
        assertThat(shreyas.bowling().wickets()).isEqualTo(13);
        assertThat(shreyas.batting()).isNull(); // not in the BPL batting top 10: unknown, not zero

        // An average printed as "12" fits several run totals, so those stay unknown.
        var shashank = named(all, "Shashank Patwal");
        assertThat(shashank.bowling().runs()).isNull();
        assertThat(shashank.bowling().economy()).isEqualTo(new Rate(new BigDecimal("4.75"), true));

        assertThat(all).hasSize(16);
    }

    @Test
    void seedLoadsScaFiguresAsPrinted() {
        UUID sca = competition("SCA Club League 2025 - Division 3");
        var all = players("sca", sca);
        assertThat(all).hasSize(21);

        var alok = named(all, "Alok Patra");
        assertThat(alok.batting().runs()).isEqualTo(360);
        assertThat(alok.batting().balls()).isEqualTo(316);
        assertThat(alok.batting().strikeRate()).isEqualTo(new Rate(new BigDecimal("113.92"), false));
        assertThat(alok.fielding().catches()).isEqualTo(4); // 0 catches + 4 as wicketkeeper
        assertThat(alok.recovered()).isEmpty();

        var alpin = named(all, "Alpin Mehta"); // "(hawks Club Admin)" dropped from the name
        assertThat(alpin.bowling().wickets()).isEqualTo(14);
        assertThat(alpin.bowling().balls()).isEqualTo(222);
    }

    @Test
    void allSourcesAddUpCountsAndRecalculateRates() {
        var all = players("all", null);

        // Batting in both: SCA 225 off 174 + BPL 81 off 72 (recovered) = 306 off 246.
        var shashank = named(all, "Shashank Patwal");
        assertThat(shashank.sources()).containsExactlyInAnyOrder("sca", "cricheroes");
        assertThat(shashank.batting().runs()).isEqualTo(306);
        assertThat(shashank.batting().balls()).isEqualTo(246);
        assertThat(shashank.batting().average()).isEqualTo(new Rate(new BigDecimal("21.86"), false));
        assertThat(shashank.batting().strikeRate()).isEqualTo(new Rate(new BigDecimal("124.39"), false));
        assertThat(shashank.coverage().batting()).containsExactlyInAnyOrder("sca", "cricheroes");

        // Bowling in both: 296 + 77 runs, 305 + 127 balls, 11 + 13 wickets.
        var shreyas = named(all, "Shreyas Puttur");
        assertThat(shreyas.bowling().wickets()).isEqualTo(24);
        assertThat(shreyas.bowling().balls()).isEqualTo(432);
        assertThat(shreyas.bowling().economy()).isEqualTo(new Rate(new BigDecimal("5.18"), false));
        // BPL doesn't list his batting, so the batting total is SCA's and says so.
        assertThat(shreyas.batting().runs()).isEqualTo(70);
        assertThat(shreyas.coverage().batting()).containsExactly("sca");

        assertThat(all).extracting(StatsViews.PlayerStats::name).doesNotHaveDuplicates();
    }

    @Test
    void standingsKeepSourceOrderAndMarkTheClub() {
        List<StatsViews.Standing> table = http.get()
                .uri("/api/stats/competitions/" + competition("BPL 2025") + "/standings")
                .retrieve().body(new ParameterizedTypeReference<>() {
                });
        assertThat(table).hasSize(10);
        var first = table.getFirst();
        assertThat(first.team()).isEqualTo("HAWKS CC");
        assertThat(first.clubTeam()).isTrue();
        assertThat(first.points()).isEqualTo(27);
        assertThat(first.netRunRate()).isEqualTo("2.529");
        assertThat(table).filteredOn(StatsViews.Standing::clubTeam).hasSize(1);
    }

    @Test
    void sourcesReportLastUpdate() {
        List<StatsViews.SourceStatus> sources = http.get().uri("/api/stats/sources").retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        assertThat(sources).extracting(StatsViews.SourceStatus::source).containsExactly("sca", "cricheroes");
        assertThat(sources.get(0).lastSucceededAt()).isNotNull();
        assertThat(sources.get(1).lastSucceededAt()).isNotNull();
    }

    // ---------- imports ----------

    @Test
    void adminImportsNeedTheStatsAdminRole() {
        assertThat(importCsv(competition("BPL 2025"), "batting", "Player,Runs\nX,1\n", false)).isEqualTo(401);
    }

    @Test
    void reimportingIdenticalContentChangesNothing() {
        UUID comp = newCompetition();
        String csv = "Player,Mat,Catches\nIdem Potent,1,1\n";

        assertThat(importCsv(comp, "fielding", csv, true)).isEqualTo(200);
        assertThat(lastRunStatus(comp)).isEqualTo("succeeded");
        assertThat(importCsv(comp, "fielding", csv.replace("\n", "\r\n"), true)).isEqualTo(200);
        assertThat(lastRunStatus(comp)).isEqualTo("unchanged");
        assertThat(jdbc.sql("select count(*) from source_records where competition_id = :c")
                .param("c", comp).query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void aNewTabImportMakesPlayersWhoDroppedOutUnknown() {
        UUID comp = newCompetition();
        assertThat(importCsv(comp, "batting", "Player,Inn,NO,Runs,Balls\nTop Bat,4,1,120,80\nFringe Bat,3,0,20,30\n",
                true)).isEqualTo(200);
        assertThat(importCsv(comp, "fielding", "Player,Mat,Catches\nFringe Bat,3,2\n", true)).isEqualTo(200);

        var before = named(players("cricheroes", comp), "Top Bat");
        assertThat(before.batting().average()).isEqualTo(new Rate(new BigDecimal("40.00"), false));
        assertThat(before.batting().strikeRate()).isEqualTo(new Rate(new BigDecimal("150.00"), false));

        // New batting list without Fringe Bat: their batting becomes unknown; fielding is untouched.
        assertThat(importCsv(comp, "batting", "Player,Inn,NO,Runs,Balls\nTop Bat,5,1,150,100\n", true))
                .isEqualTo(200);
        var after = players("cricheroes", comp);
        assertThat(named(after, "Top Bat").batting().runs()).isEqualTo(150);
        var fringe = named(after, "Fringe Bat");
        assertThat(fringe.batting()).isNull();
        assertThat(fringe.fielding().catches()).isEqualTo(2);
    }

    @Test
    void invalidContentIsRejectedWithReasonsAndRecordedAsAFailedRun() {
        UUID comp = newCompetition();
        Map<String, Object> problem = http.post()
                .uri("/api/admin/stats/competitions/" + comp + "/imports/batting")
                .contentType(MediaType.valueOf("text/csv"))
                .headers(h -> h.setBasicAuth(adminUser, adminPassword))
                .body("Runs,Balls\n1,2\n")
                .exchange((req, res) -> {
                    assertThat(res.getStatusCode().value()).isEqualTo(422);
                    return res.bodyTo(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
                });
        assertThat(problem).containsKey("errors");
        assertThat(lastRunStatus(comp)).isEqualTo("failed");
        assertThat(players("cricheroes", comp)).isEmpty();
    }

    @Test
    void browsersMayCallTheApiOnlyFromAllowedOrigins() {
        var allowed = http.get().uri("/api/stats/sources").header("Origin", "http://localhost:8081")
                .exchange((req, res) -> java.util.List.of(String.valueOf(res.getStatusCode().value()),
                        String.valueOf(res.getHeaders().getFirst("Access-Control-Allow-Origin"))));
        assertThat(allowed).containsExactly("200", "http://localhost:8081");

        int blocked = http.get().uri("/api/stats/sources").header("Origin", "https://evil.example")
                .exchange((req, res) -> res.getStatusCode().value());
        assertThat(blocked).isEqualTo(403);
    }

    @Test
    void linkingANameMovesItsFiguresOntoOnePlayer() {
        UUID ch = newCompetition("cricheroes");
        UUID sca = newCompetition("sca");
        assertThat(importCsv(ch, "batting", "Player,Inn,NO,Runs,Balls\nLink Testone,2,0,30,20\n", true)).isEqualTo(200);
        assertThat(importCsv(sca, "batting", "Player,Inn,NO,Runs,Balls\nTestone Link,3,1,50,40\n", true)).isEqualTo(200);
        assertThat(players("all", null)).extracting(StatsViews.PlayerStats::name)
                .contains("Link Testone", "Testone Link");

        assertThat(link("Link Testone", "sca", "Testone Link")).isEqualTo(200);
        assertThat(link("Link Testone", "sca", "Testone Link")).isEqualTo(200); // idempotent

        var all = players("all", null);
        assertThat(all).extracting(StatsViews.PlayerStats::name).doesNotContain("Testone Link");
        var p = named(all, "Link Testone");
        assertThat(p.sources()).containsExactlyInAnyOrder("cricheroes", "sca");
        assertThat(p.batting().runs()).isEqualTo(80);
        assertThat(p.batting().strikeRate()).isEqualTo(new Rate(new BigDecimal("133.33"), false));
        assertThat(p.batting().average()).isEqualTo(new Rate(new BigDecimal("20.00"), false));
    }

    @Test
    void linkingRefusesToDoubleCountACompetition() {
        UUID ch = newCompetition("cricheroes");
        assertThat(importCsv(ch, "batting", "Player,Runs\nDup Alpha\t1\nDup Beta,2\n".replace("\t", ","), true))
                .isEqualTo(200);
        // Both names have figures in the same competition: linking would count it twice.
        assertThat(link("Dup Alpha", "cricheroes", "Dup Beta")).isEqualTo(409);
        assertThat(players("cricheroes", ch)).extracting(StatsViews.PlayerStats::name)
                .containsExactlyInAnyOrder("Dup Alpha", "Dup Beta");
    }

    @Test
    void unknownCompetitionIs404() {
        assertThat(importCsv(UUID.randomUUID(), "batting", "Player,Runs\nX,1\n", true)).isEqualTo(404);
    }
}
