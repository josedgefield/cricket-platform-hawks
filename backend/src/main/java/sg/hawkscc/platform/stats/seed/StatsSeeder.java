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

import sg.hawkscc.platform.stats.PlayerLinkService;
import sg.hawkscc.platform.stats.StatsImportService;
import sg.hawkscc.platform.stats.domain.Source;
import sg.hawkscc.platform.stats.domain.StatKind;

/**
 * Loads the bundled public stats (see resources/seed/README.md) through the normal import path:
 * first the player links, so one person's SCA and CricHeroes names land on one player, then
 * each source's tables. Safe on every startup: unchanged files are recorded as "unchanged".
 * A failed seed is logged and recorded as a failed sync run; it doesn't stop the app.
 */
@Component
@ConditionalOnProperty(name = "hawks.stats.seed", havingValue = "true")
class StatsSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StatsSeeder.class);
    private static final String ACTOR = "seed";

    private static final String BPL_DIR = "seed/cricheroes/bpl-2025/";
    private static final LocalDate BPL_CAPTURED_ON = LocalDate.of(2026, 10, 4);

    private static final String SCA_DIR = "seed/sca/club-league-2025-div3/";
    private static final LocalDate SCA_CAPTURED_ON = LocalDate.of(2026, 10, 5);
    /** Shared with backend/scripts/import-sca.sh so both land in one competition. */
    static final String SCA_COMPETITION = "SCA Club League 2025 - Division 3";

    private final StatsImportService imports;
    private final PlayerLinkService links;

    StatsSeeder(StatsImportService imports, PlayerLinkService links) {
        this.imports = imports;
        this.links = links;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            links.linkAll(read("seed/player-links.csv"), ACTOR);
        } catch (RuntimeException e) {
            log.error("Applying seed player links failed; players may appear once per source", e);
        }
        seedBpl();
        seedSca();
    }

    private void seedBpl() {
        try {
            UUID bpl = imports.ensureCompetition(Source.CRICHEROES, "BPL 2025", "2025", "1500354");
            leaderboard(bpl, StatKind.BATTING, BPL_DIR + "bpl-2025-batting.csv",
                    "hawks-cc-batting-leaderboard.pdf", BPL_CAPTURED_ON);
            leaderboard(bpl, StatKind.BOWLING, BPL_DIR + "bpl-2025-bowling.csv",
                    "hawks-cc-bowling-leaderboard.pdf", BPL_CAPTURED_ON);
            leaderboard(bpl, StatKind.FIELDING, BPL_DIR + "bpl-2025-fielding.csv",
                    "hawks-cc-fielding-leaderboard.pdf", BPL_CAPTURED_ON);
            imports.importStandings(new StatsImportService.StandingsImport(bpl, "Supreme (league matches)",
                    read(BPL_DIR + "bpl-2025-points-table-supreme.csv"), "points_table_BPL_2025.pdf",
                    BPL_CAPTURED_ON, ACTOR));
        } catch (RuntimeException e) {
            log.error("Seeding CricHeroes BPL 2025 stats failed; the app continues without it", e);
        }
    }

    private void seedSca() {
        try {
            UUID sca = imports.ensureCompetition(Source.SCA, SCA_COMPETITION, "2025", "clubId=7683;teamId=2291");
            String page = "scores.cricketsingapore.com/SingaporeCricketAssoc/";
            String team = "?teamId=2291&clubId=7683";
            leaderboard(sca, StatKind.BATTING, SCA_DIR + "sca-div3-2025-batting.csv",
                    page + "teamBatting.do" + team, SCA_CAPTURED_ON);
            leaderboard(sca, StatKind.BOWLING, SCA_DIR + "sca-div3-2025-bowling.csv",
                    page + "teamBowling.do" + team, SCA_CAPTURED_ON);
            leaderboard(sca, StatKind.FIELDING, SCA_DIR + "sca-div3-2025-fielding.csv",
                    page + "teamFielding.do" + team, SCA_CAPTURED_ON);
        } catch (RuntimeException e) {
            log.error("Seeding SCA Club League 2025 stats failed; the app continues without it", e);
        }
    }

    private void leaderboard(UUID competitionId, StatKind kind, String file, String origin, LocalDate capturedOn) {
        var result = imports.importLeaderboard(new StatsImportService.LeaderboardImport(competitionId, kind,
                read(file), origin, capturedOn, ACTOR));
        log.info("seed {}: {}", file, result.status());
    }

    private static String read(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
