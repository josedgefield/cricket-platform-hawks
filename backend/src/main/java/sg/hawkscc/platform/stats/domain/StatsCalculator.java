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
        var batting = new StatLine.Batting(
                sum(lines, l -> l.batting().inns()),
                sum(lines, l -> l.batting().notOuts()),
                sum(lines, l -> l.batting().runs()),
                sum(lines, l -> l.batting().balls()),
                max(lines, l -> l.batting().highScore()),
                sum(lines, l -> l.batting().fours()),
                sum(lines, l -> l.batting().sixes()));
        var bowling = new StatLine.Bowling(
                sum(lines, l -> l.bowling().inns()),
                sum(lines, l -> l.bowling().balls()),
                sum(lines, l -> l.bowling().maidens()),
                sum(lines, l -> l.bowling().runs()),
                sum(lines, l -> l.bowling().wickets()));
        var fielding = new StatLine.Fielding(
                sum(lines, l -> l.fielding().catches()),
                sum(lines, l -> l.fielding().stumpings()),
                sum(lines, l -> l.fielding().runOuts()),
                sum(lines, l -> l.fielding().dismissals()));
        // One source's published rate is not the combined rate, so it is dropped.
        return new StatLine(sum(lines, StatLine::matches), batting, bowling, fielding, StatLine.Reported.NONE);
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
