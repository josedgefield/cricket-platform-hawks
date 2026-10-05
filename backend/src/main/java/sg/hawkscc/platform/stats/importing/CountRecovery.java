package sg.hawkscc.platform.stats.importing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

import sg.hawkscc.platform.stats.domain.StatKind;

/**
 * Fills a missing count only when exactly one whole number reproduces the rate the source
 * published, rounded to the decimals it printed. Example: 149 runs at SR 131.86 can only be
 * 113 balls. If no value or several values fit (an average printed as "12" fits several run
 * totals), the count stays unknown. This recovers what the source already implies; it never
 * estimates. Recovered columns are recorded so the API can say so.
 */
final class CountRecovery {

    /** Upper bound for the search; far above any club-cricket season total. */
    private static final int MAX_COUNT = 5_000;

    private CountRecovery() {
    }

    /** Fills recoverable gaps in {@code v} in place and returns the columns it filled. */
    static List<StatColumn> recover(StatKind kind, Map<StatColumn, Object> v) {
        List<StatColumn> recovered = new ArrayList<>();
        if (kind == StatKind.BATTING) {
            Integer runs = integer(v, StatColumn.BAT_RUNS);
            BigDecimal sr = decimal(v, StatColumn.REPORTED_BAT_SR);
            if (v.get(StatColumn.BAT_BALLS) == null && runs != null && runs > 0 && positive(sr)) {
                Integer balls = unique(1, MAX_COUNT, b -> fits(sr, (long) runs * 100, b));
                put(v, StatColumn.BAT_BALLS, balls, recovered);
            }
            Integer inns = integer(v, StatColumn.BAT_INNS);
            BigDecimal avg = decimal(v, StatColumn.REPORTED_BAT_AVG);
            if (v.get(StatColumn.BAT_NOT_OUTS) == null && inns != null && runs != null && runs > 0 && positive(avg)) {
                Integer dismissals = unique(1, inns, d -> fits(avg, runs, d));
                put(v, StatColumn.BAT_NOT_OUTS, dismissals == null ? null : inns - dismissals, recovered);
            }
        } else if (kind == StatKind.BOWLING) {
            Integer wickets = integer(v, StatColumn.BOWL_WICKETS);
            BigDecimal avg = decimal(v, StatColumn.REPORTED_BOWL_AVG);
            if (v.get(StatColumn.BOWL_RUNS) == null && wickets != null && wickets > 0 && positive(avg)) {
                Integer runs = unique(0, MAX_COUNT, r -> fits(avg, r, wickets));
                put(v, StatColumn.BOWL_RUNS, runs, recovered);
            }
            Integer runs = integer(v, StatColumn.BOWL_RUNS);
            BigDecimal econ = decimal(v, StatColumn.REPORTED_ECON);
            if (v.get(StatColumn.BOWL_BALLS) == null && runs != null && runs > 0 && positive(econ)) {
                Integer balls = unique(1, MAX_COUNT, b -> fits(econ, (long) runs * 6, b));
                put(v, StatColumn.BOWL_BALLS, balls, recovered);
            }
        }
        return List.copyOf(recovered);
    }

    /** Does numerator / denominator, rounded half-up to the published value's decimals, equal it? */
    private static boolean fits(BigDecimal published, long numerator, int denominator) {
        int scale = Math.max(0, published.scale());
        BigDecimal ours = BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), scale, RoundingMode.HALF_UP);
        return ours.compareTo(published) == 0;
    }

    /** The only value in [from, to] that satisfies the test, or null if none or several do. */
    private static Integer unique(int from, int to, IntPredicate test) {
        Integer found = null;
        for (int n = from; n <= to; n++) {
            if (test.test(n)) {
                if (found != null) {
                    return null;
                }
                found = n;
            }
        }
        return found;
    }

    private static void put(Map<StatColumn, Object> v, StatColumn column, Integer value, List<StatColumn> recovered) {
        if (value != null) {
            v.put(column, value);
            recovered.add(column);
        }
    }

    private static boolean positive(BigDecimal d) {
        return d != null && d.signum() > 0;
    }

    private static Integer integer(Map<StatColumn, Object> v, StatColumn c) {
        return v.get(c) instanceof Integer i ? i : null;
    }

    private static BigDecimal decimal(Map<StatColumn, Object> v, StatColumn c) {
        return v.get(c) instanceof BigDecimal d ? d : null;
    }
}
