package sg.hawkscc.api.sca.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Normalised SCA records. Every numeric field is nullable: a value the source did not provide stays {@code null}
 * and is never estimated (CLAUDE.md principle 8). {@code extra} keeps any source column we do not map, so nothing
 * the export contained is lost.
 */
public final class ScaRecords {

    private ScaRecords() {
    }

    public record Player(
            String playerId,
            String name,
            String role,
            String battingStyle,
            String bowlingStyle,
            Map<String, String> extra) {
    }

    public record BattingStat(
            String playerId,
            String name,
            Integer matches,
            Integer innings,
            Integer notOuts,
            Integer runs,
            Integer balls,
            String highestScore,
            BigDecimal average,
            BigDecimal strikeRate,
            Integer fifties,
            Integer hundreds,
            Integer fours,
            Integer sixes,
            Integer ducks,
            Map<String, String> extra) {
    }

    public record BowlingStat(
            String playerId,
            String name,
            Integer matches,
            Integer innings,
            /** Overs as printed by the source, e.g. "23.4". */
            String overs,
            /** The same overs as legal balls (23.4 overs = 142 balls). */
            Integer balls,
            Integer maidens,
            Integer runsConceded,
            Integer wickets,
            String bestBowling,
            BigDecimal average,
            BigDecimal economy,
            BigDecimal strikeRate,
            Integer wides,
            Integer noBalls,
            Integer fourWickets,
            Integer fiveWickets,
            Map<String, String> extra) {
    }

    public record FieldingStat(
            String playerId,
            String name,
            Integer matches,
            Integer catches,
            Integer wicketKeeperCatches,
            Integer stumpings,
            Integer runOuts,
            Integer totalDismissals,
            Map<String, String> extra) {
    }

    public record MatchResult(
            String matchId,
            LocalDate date,
            String dateText,
            String competition,
            String teamOne,
            String teamOneScore,
            String teamTwo,
            String teamTwoScore,
            String result,
            /** WON / LOST / TIED / NO_RESULT / ABANDONED, or null when the result text is not conclusive. */
            String outcome,
            String venue,
            Map<String, String> extra) {
    }

    public record Fixture(
            String matchId,
            LocalDate date,
            String dateText,
            String time,
            String competition,
            String teamOne,
            String teamTwo,
            String venue,
            Map<String, String> extra) {
    }

    /** One leaderboard entry. {@code value} is the ranked number; {@code detail} adds context like "avg 46.5". */
    public record Leader(String playerId, String name, BigDecimal value, String detail) {
    }

    public record LeaderBoard(String key, String label, String qualification, List<Leader> leaders) {
    }

    /** The parsed result of one dataset sync, plus where and how it was obtained. */
    public record DatasetSnapshot(
            Dataset dataset,
            String sourceUrl,
            /** csv-export, html-table, or manual-upload. */
            String method,
            Instant fetchedAt,
            String contentSha256,
            RawTable raw) {
    }

    public enum SyncState { OK, UNCHANGED, FAILED, SKIPPED_ROBOTS, NEVER_RUN }

    public record DatasetStatus(
            Dataset dataset,
            SyncState state,
            Instant lastAttemptAt,
            Instant lastSuccessAt,
            String method,
            Integer rowCount,
            String message) {
    }
}
