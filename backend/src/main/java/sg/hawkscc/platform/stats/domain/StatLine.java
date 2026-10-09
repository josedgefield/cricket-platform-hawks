package sg.hawkscc.platform.stats.domain;

import java.math.BigDecimal;

/**
 * Raw counts for one player, from one source or combined. Every value is nullable:
 * null means "not published", which is different from 0.
 */
public record StatLine(Integer matches, Batting batting, Bowling bowling, Fielding fielding, Reported reported) {

    public record Batting(Integer inns, Integer notOuts, Integer runs, Integer balls, Integer highScore,
                          Integer fours, Integer sixes) {
        public static final Batting UNKNOWN = new Batting(null, null, null, null, null, null, null);

        public boolean isUnknown() {
            return equals(UNKNOWN);
        }
    }

    public record Bowling(Integer inns, Integer balls, Integer maidens, Integer runs, Integer wickets) {
        public static final Bowling UNKNOWN = new Bowling(null, null, null, null, null);

        public boolean isUnknown() {
            return equals(UNKNOWN);
        }
    }

    public record Fielding(Integer catches, Integer stumpings, Integer runOuts, Integer dismissals) {
        public static final Fielding UNKNOWN = new Fielding(null, null, null, null);

        public boolean isUnknown() {
            return equals(UNKNOWN);
        }
    }

    /** Rates exactly as a source published them. Only meaningful for that single source. */
    public record Reported(BigDecimal battingAverage, BigDecimal strikeRate, BigDecimal economy,
                           BigDecimal bowlingAverage) {
        public static final Reported NONE = new Reported(null, null, null, null);
    }

    public static final StatLine UNKNOWN =
            new StatLine(null, Batting.UNKNOWN, Bowling.UNKNOWN, Fielding.UNKNOWN, Reported.NONE);
}
