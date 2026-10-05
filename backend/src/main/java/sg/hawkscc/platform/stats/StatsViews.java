package sg.hawkscc.platform.stats;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import sg.hawkscc.platform.stats.domain.Rate;

/** Read models returned by the stats API. Null always means "unknown". */
public final class StatsViews {

    private StatsViews() {
    }

    /**
     * One player, from one source or all sources added together. {@code recovered} lists the
     * counts (e.g. "batting.balls") that include a value a source didn't print but that exactly
     * one whole number reproduces from its published rate. Clients mark these counts, and the rates
     * calculated from them, so readers can tell them from printed figures.
     */
    public record PlayerStats(UUID playerId, String name, List<String> sources, Integer matches, Batting batting,
                              Bowling bowling, Fielding fielding, List<String> recovered, Coverage coverage) {
    }

    /**
     * Which sources each category's totals include. A source in {@code sources} but missing
     * here didn't list the player in that category (e.g. outside a top-10 leaderboard), so
     * the total covers the listed sources only.
     */
    public record Coverage(List<String> batting, List<String> bowling, List<String> fielding) {
    }

    public record Batting(Integer inns, Integer notOuts, Integer runs, Integer balls, Integer highScore,
                          Integer fours, Integer sixes, Rate average, Rate strikeRate) {
    }

    /** {@code overs} is formatted from balls, e.g. "21.1". */
    public record Bowling(Integer inns, String overs, Integer balls, Integer maidens, Integer runs, Integer wickets,
                          Rate average, Rate economy, Rate strikeRate) {
    }

    public record Fielding(Integer catches, Integer stumpings, Integer runOuts, Integer dismissals) {
    }

    public record Competition(UUID id, String source, String name, String season, String externalId) {
    }

    public record Standing(String group, int position, String team, boolean clubTeam, Integer matches, Integer won,
                           Integer lost, Integer drawn, Integer tied, Integer noResult, Integer points,
                           String netRunRate, String runsFor, String runsAgainst, String lastFive) {
    }

    /** Drives "last updated" and stale badges in the UI. */
    public record SourceStatus(String source, Instant lastSucceededAt, Instant lastRunAt, String lastRunStatus,
                               String lastError) {
    }
}
