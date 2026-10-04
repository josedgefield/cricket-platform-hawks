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
        String name = "Test " + UUID.randomUUID();
        http.post().uri("/api/admin/stats/competitions")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> h.setBasicAuth(adminUser, adminPassword))
                .body(Map.of("source", "cricheroes", "name", name))
                .retrieve().toBodilessEntity();
        return competition(name);
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
        assertThat(sandeep.batting().balls()).isNull();
        assertThat(sandeep.batting().strikeRate()).isEqualTo(new Rate(new BigDecimal("131.86"), true));
        assertThat(sandeep.fielding().dismissals()).isEqualTo(2);
        assertThat(sandeep.fielding().stumpings()).isNull();

        var shreyas = named(all, "Puttur Shreyas");
        assertThat(shreyas.bowling().wickets()).isEqualTo(13);
        assertThat(shreyas.batting()).isNull(); // not in the batting top 10: unknown, not zero

        assertThat(all).hasSize(16);
        assertThat(players("sca", null)).isEmpty();
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
        assertThat(sources.get(0).lastSucceededAt()).isNull();
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
    void unknownCompetitionIs404() {
        assertThat(importCsv(UUID.randomUUID(), "batting", "Player,Runs\nX,1\n", true)).isEqualTo(404);
    }
}
