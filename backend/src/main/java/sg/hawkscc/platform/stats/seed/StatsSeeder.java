package sg.hawkscc.platform.stats.seed;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import sg.hawkscc.platform.stats.StatsImportService;
import sg.hawkscc.platform.stats.domain.Source;
import sg.hawkscc.platform.stats.domain.StatKind;

/**
 * Loads the bundled CricHeroes BPL 2025 tables through the normal import path.
 * Safe on every startup: identical content is recorded as "unchanged".
 * A failed seed is logged and recorded as a failed sync run; it doesn't stop the app.
 */
@Component
@ConditionalOnProperty(name = "hawks.stats.seed", havingValue = "true")
class StatsSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StatsSeeder.class);
    private static final String DIR = "seed/cricheroes/bpl-2025/";
    private static final LocalDate CAPTURED_ON = LocalDate.of(2026, 10, 4);
    private static final String ACTOR = "seed";

    private final StatsImportService imports;

    StatsSeeder(StatsImportService imports) {
        this.imports = imports;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            UUID bpl = imports.ensureCompetition(Source.CRICHEROES, "BPL 2025", "2025", "1500354");
            leaderboard(bpl, StatKind.BATTING, "batting.csv", "hawks-cc-batting-leaderboard.pdf");
            leaderboard(bpl, StatKind.BOWLING, "bowling.csv", "hawks-cc-bowling-leaderboard.pdf");
            leaderboard(bpl, StatKind.FIELDING, "fielding.csv", "hawks-cc-fielding-leaderboard.pdf");
            imports.importStandings(new StatsImportService.StandingsImport(bpl, "Supreme (league matches)",
                    read("standings-supreme.csv"), "points_table_BPL_2025.pdf", CAPTURED_ON, ACTOR));
        } catch (RuntimeException e) {
            log.error("Seeding CricHeroes BPL 2025 stats failed; the app continues without it", e);
        }
    }

    private void leaderboard(UUID competitionId, StatKind kind, String file, String origin) {
        var result = imports.importLeaderboard(new StatsImportService.LeaderboardImport(competitionId, kind,
                read(file), origin, CAPTURED_ON, ACTOR));
        log.info("seed {}: {}", file, result.status());
    }

    private static String read(String file) {
        try {
            return new ClassPathResource(DIR + file).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
