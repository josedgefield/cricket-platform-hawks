package sg.hawkscc.platform.stats.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Derived stats and cross-source totals, following the null rules in docs/06:
 * a rate is unknown when its inputs are unknown or its divisor is zero, and a
 * combined total is unknown when any contributing source lacks the value.
 */
public final class StatsCalculator {

    private static final int SCALE = 2;

    private StatsCalculator() {
    }

    /** Sums several sources' lines for one player. */
    public static StatLine combine(List<StatLine> lines) {
        if (lines.isEmpty()) {
            return StatLine.UNKNOWN;
        }
        if (lines.size() == 1) {
            return lines.getFirst();
        }
        // Per category, add up only the lines that list the player at all (a source that has no
        // batting row for someone says nothing about their batting). Within those lines, a
        // figure one of them lacks makes the total unknown: a partial sum would look complete.
        List<StatLine> bat = lines.stream().filter(l -> !l.batting().isUnknown()).toList();
        List<StatLine> bowl = lines.stream().filter(l -> !l.bowling().isUnknown()).toList();
        List<StatLine> field = lines.stream().filter(l -> !l.fielding().isUnknown()).toList();
        var batting = bat.isEmpty() ? StatLine.Batting.UNKNOWN : new StatLine.Batting(
                sum(bat, l -> l.batting().inns()),
                sum(bat, l -> l.batting().notOuts()),
                sum(bat, l -> l.batting().runs()),
                sum(bat, l -> l.batting().balls()),
                max(bat, l -> l.batting().highScore()),
                sum(bat, l -> l.batting().fours()),
                sum(bat, l -> l.batting().sixes()));
        var bowling = bowl.isEmpty() ? StatLine.Bowling.UNKNOWN : new StatLine.Bowling(
                sum(bowl, l -> l.bowling().inns()),
                sum(bowl, l -> l.bowling().balls()),
                sum(bowl, l -> l.bowling().maidens()),
                sum(bowl, l -> l.bowling().runs()),
                sum(bowl, l -> l.bowling().wickets()));
        var fielding = field.isEmpty() ? StatLine.Fielding.UNKNOWN : new StatLine.Fielding(
                sum(field, l -> l.fielding().catches()),
                sum(field, l -> l.fielding().stumpings()),
                sum(field, l -> l.fielding().runOuts()),
                sum(field, l -> l.fielding().dismissals()));
        // A source's published rate is only the total's rate when that source is the only one
        // in the category; otherwise rates come from the summed counts or stay unknown.
        var reported = new StatLine.Reported(
                bat.size() == 1 ? bat.getFirst().reported().battingAverage() : null,
                bat.size() == 1 ? bat.getFirst().reported().strikeRate() : null,
                bowl.size() == 1 ? bowl.getFirst().reported().economy() : null,
                bowl.size() == 1 ? bowl.getFirst().reported().bowlingAverage() : null);
        return new StatLine(sum(lines, StatLine::matches), batting, bowling, fielding, reported);
    }

    public static Rate battingAverage(StatLine s) {
        var b = s.batting();
        Integer dismissals = b.inns() == null || b.notOuts() == null ? null : b.inns() - b.notOuts();
        return orReported(divide(b.runs(), dismissals, 1), s.reported().battingAverage());
    }

    public static Rate strikeRate(StatLine s) {
        var b = s.batting();
        return orReported(divide(b.runs(), b.balls(), 100), s.reported().strikeRate());
    }

    public static Rate economy(StatLine s) {
        var b = s.bowling();
        return orReported(divide(b.runs(), b.balls(), 6), s.reported().economy());
    }

    public static Rate bowlingAverage(StatLine s) {
        var b = s.bowling();
        return orReported(divide(b.runs(), b.wickets(), 1), s.reported().bowlingAverage());
    }

    /** Balls per wicket. Sources don't publish this, so it is only ever calculated. */
    public static Rate bowlingStrikeRate(StatLine s) {
        var b = s.bowling();
        return Rate.ofCalculated(divide(b.balls(), b.wickets(), 1));
    }

    /** The source's dismissal total if published, else catches + stumpings when both are known. */
    public static Integer fieldingDismissals(StatLine s) {
        var f = s.fielding();
        if (f.dismissals() != null) {
            return f.dismissals();
        }
        return f.catches() == null || f.stumpings() == null ? null : f.catches() + f.stumpings();
    }

    private static Rate orReported(BigDecimal calculated, BigDecimal reported) {
        return calculated != null ? Rate.ofCalculated(calculated) : Rate.ofReported(reported);
    }

    private static BigDecimal divide(Integer numerator, Integer denominator, int multiplier) {
        if (numerator == null || denominator == null || denominator == 0) {
            return null;
        }
        return BigDecimal.valueOf((long) numerator * multiplier)
                .divide(BigDecimal.valueOf(denominator), SCALE, RoundingMode.HALF_UP);
    }

    private static Integer sum(List<StatLine> lines, Function<StatLine, Integer> field) {
        int total = 0;
        for (StatLine line : lines) {
            Integer v = field.apply(line);
            if (v == null) {
                return null;
            }
            total += v;
        }
        return total;
    }

    private static Integer max(List<StatLine> lines, Function<StatLine, Integer> field) {
        List<Integer> values = lines.stream().map(field).toList();
        if (values.stream().anyMatch(Objects::isNull)) {
            return null;
        }
        return values.stream().mapToInt(Integer::intValue).max().orElseThrow();
    }
}
