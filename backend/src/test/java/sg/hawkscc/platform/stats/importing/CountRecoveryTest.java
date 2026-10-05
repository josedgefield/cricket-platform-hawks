package sg.hawkscc.platform.stats.importing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import sg.hawkscc.platform.stats.domain.StatKind;

class CountRecoveryTest {

    private static Map<StatColumn, Object> values(Object... pairs) {
        Map<StatColumn, Object> v = new EnumMap<>(StatColumn.class);
        for (int i = 0; i < pairs.length; i += 2) {
            v.put((StatColumn) pairs[i], pairs[i + 1] instanceof String s ? new BigDecimal(s) : pairs[i + 1]);
        }
        return v;
    }

    @Test
    void recoversBallsAndNotOutsWhenExactlyOneValueFits() {
        var v = values(StatColumn.BAT_INNS, 4, StatColumn.BAT_RUNS, 149,
                StatColumn.REPORTED_BAT_AVG, "49.67", StatColumn.REPORTED_BAT_SR, "131.86");
        assertThat(CountRecovery.recover(StatKind.BATTING, v))
                .containsExactly(StatColumn.BAT_BALLS, StatColumn.BAT_NOT_OUTS);
        assertThat(v).containsEntry(StatColumn.BAT_BALLS, 113).containsEntry(StatColumn.BAT_NOT_OUTS, 1);
    }

    @Test
    void recoversBowlingRunsThenBalls() {
        var v = values(StatColumn.BOWL_WICKETS, 13, StatColumn.REPORTED_BOWL_AVG, "5.92",
                StatColumn.REPORTED_ECON, "3.64");
        assertThat(CountRecovery.recover(StatKind.BOWLING, v))
                .containsExactly(StatColumn.BOWL_RUNS, StatColumn.BOWL_BALLS);
        assertThat(v).containsEntry(StatColumn.BOWL_RUNS, 77).containsEntry(StatColumn.BOWL_BALLS, 127);
    }

    @Test
    void anAveragePrintedWithoutDecimalsFitsSeveralTotalsSoStaysUnknown() {
        // "12" from 7 wickets fits 81 to 87 runs.
        var v = values(StatColumn.BOWL_WICKETS, 7, StatColumn.REPORTED_BOWL_AVG, "12",
                StatColumn.REPORTED_ECON, "4.75");
        assertThat(CountRecovery.recover(StatKind.BOWLING, v)).isEmpty();
        assertThat(v).doesNotContainKey(StatColumn.BOWL_RUNS).doesNotContainKey(StatColumn.BOWL_BALLS);
    }

    @Test
    void neverOverwritesAPrintedCount() {
        var v = values(StatColumn.BAT_INNS, 4, StatColumn.BAT_RUNS, 149, StatColumn.BAT_BALLS, 100,
                StatColumn.REPORTED_BAT_SR, "131.86");
        CountRecovery.recover(StatKind.BATTING, v);
        assertThat(v).containsEntry(StatColumn.BAT_BALLS, 100);
    }

    @Test
    void zeroRunsCanNotBeRecovered() {
        var v = values(StatColumn.BAT_INNS, 1, StatColumn.BAT_RUNS, 0, StatColumn.REPORTED_BAT_SR, "0.00");
        assertThat(CountRecovery.recover(StatKind.BATTING, v)).isEmpty();
    }
}
