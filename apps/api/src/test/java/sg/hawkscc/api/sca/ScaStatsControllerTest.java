package sg.hawkscc.api.sca;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/** End to end: upload synthetic CSVs through the admin import endpoint, then query every read endpoint. */
@SpringBootTest(properties = {
        "sca.enabled=false",
        "sca.sync-on-startup=false",
        "sca.admin-key=test-admin-key",
        "sca.storage-dir=${java.io.tmpdir}/hawks-api-test-${random.uuid}"
})
@AutoConfigureMockMvc
@DirtiesContext
class ScaStatsControllerTest {

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-10-04T02:00:00Z"), ZoneId.of("Asia/Singapore"));
        }
    }

    @Autowired
    MockMvc mvc;

    @BeforeEach
    void load() throws Exception {
        for (String d : new String[] {"players", "batting", "bowling", "fielding", "results", "schedule"}) {
            try (InputStream in = getClass().getResourceAsStream("/fixtures/synthetic-" + d + ".csv")) {
                mvc.perform(multipart("/api/sca/import/" + d)
                        .file(new MockMultipartFile("file", d + ".csv", "text/csv", in.readAllBytes()))
                        .header("X-Admin-Key", "test-admin-key"))
                        .andExpect(status().isOk());
            }
        }
    }

    @Test
    void battingSortedWithMeta() throws Exception {
        mvc.perform(get("/api/sca/batting").param("sort", "strikeRate").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].name").value("Player Charlie"))
                .andExpect(jsonPath("$.meta.sources[0].dataset").value("BATTING"))
                .andExpect(jsonPath("$.meta.sources[0].method").value("manual-upload"))
                .andExpect(jsonPath("$.meta.sources[0].stale").value(false));
    }

    @Test
    void leadersApplyQualificationsAndSkipUnknowns() throws Exception {
        mvc.perform(get("/api/sca/leaders").param("top", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.key=='mostRuns')].leaders[0].name").value(contains("Player Alpha")))
                .andExpect(jsonPath("$.data[?(@.key=='bestStrikeRate')].leaders[0].name").value(contains("Player Charlie")))
                .andExpect(jsonPath("$.data[?(@.key=='mostWickets')].leaders[0].name").value(contains("Player Echo")))
                .andExpect(jsonPath("$.data[?(@.key=='bestEconomy')].leaders[0].name").value(contains("Player Echo")))
                .andExpect(jsonPath("$.data[?(@.key=='mostDismissals')].leaders[0].name").value(contains("Player Hotel")))
                .andExpect(jsonPath("$.data[?(@.key=='mostCatches')].leaders[0].name").value(contains("Player Hotel")));
    }

    @Test
    void resultsRecordAndUpcomingSchedule() throws Exception {
        mvc.perform(get("/api/sca/results"))
                .andExpect(jsonPath("$.data", hasSize(4)))
                .andExpect(jsonPath("$.data[0].dateText").value("09/27/2026"));
        mvc.perform(get("/api/sca/record"))
                .andExpect(jsonPath("$.data.played").value(4))
                .andExpect(jsonPath("$.data.won").value(2))
                .andExpect(jsonPath("$.data.lost").value(1))
                .andExpect(jsonPath("$.data.abandoned").value(1))
                .andExpect(jsonPath("$.data.form[0]").value("W"));
        mvc.perform(get("/api/sca/schedule"))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].teamTwo").value("Marina Mariners XI"));
        mvc.perform(get("/api/sca/schedule/next"))
                .andExpect(jsonPath("$.data.date").value("2026-10-11"));
        mvc.perform(get("/api/sca/schedule").param("includePast", "true"))
                .andExpect(jsonPath("$.data", hasSize(4)));
    }

    @Test
    void playerProfileCombinesDatasets() throws Exception {
        mvc.perform(get("/api/sca/players/player alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batting.runs").value(312))
                .andExpect(jsonPath("$.data.fielding.catches").value(5))
                .andExpect(jsonPath("$.data.bowling").value(nullValue()));
        mvc.perform(get("/api/sca/players/nobody")).andExpect(status().isNotFound());
    }

    @Test
    void rawAndValidation() throws Exception {
        mvc.perform(get("/api/sca/raw/fielding"))
                .andExpect(jsonPath("$.data.headers[3]").value("WK Catches"));
        mvc.perform(get("/api/sca/raw/umpires")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/sca/batting").param("sort", "vibes")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/sca/leaders").param("top", "0")).andExpect(status().isBadRequest());
    }

    @Test
    void adminEndpointsNeedTheKey() throws Exception {
        mvc.perform(post("/api/sca/sync")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/sca/sync").header("X-Admin-Key", "wrong")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/sca/sync/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.BATTING.rowCount").value(5))
                .andExpect(jsonPath("$.data.BATTING.lastSuccessAt").exists());
    }
}
