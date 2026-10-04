package sg.hawkscc.api.sca;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import sg.hawkscc.api.sca.config.ScaProperties.DateOrder;
import sg.hawkscc.api.sca.normalize.CricketValues;

class CricketValuesTest {

    @Test
    void integersKeepUnknownsAsNull() {
        assertThat(CricketValues.integer("45")).isEqualTo(45);
        assertThat(CricketValues.integer("45*")).isEqualTo(45);
        assertThat(CricketValues.integer("1,204")).isEqualTo(1204);
        assertThat(CricketValues.integer("-")).isNull();
        assertThat(CricketValues.integer("")).isNull();
        assertThat(CricketValues.integer("DNB")).isNull();
        assertThat(CricketValues.integer(null)).isNull();
        assertThat(CricketValues.integer("abc")).isNull();
    }

    @Test
    void decimals() {
        assertThat(CricketValues.decimal("46.50")).isEqualByComparingTo(new BigDecimal("46.5"));
        assertThat(CricketValues.decimal("N/A")).isNull();
    }

    @Test
    void oversAreConvertedToBalls() {
        assertThat(CricketValues.oversToBalls("23.4")).isEqualTo(142);
        assertThat(CricketValues.oversToBalls("10")).isEqualTo(60);
        assertThat(CricketValues.oversToBalls("0.5")).isEqualTo(5);
        assertThat(CricketValues.oversToBalls("3.7")).isNull();
        assertThat(CricketValues.oversToBalls("-")).isNull();
    }

    @Test
    void numericDatesFollowConfiguredOrder() {
        assertThat(CricketValues.date("10/11/2026", DateOrder.MDY)).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(CricketValues.date("10/11/2026", DateOrder.DMY)).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(CricketValues.date("2026-10-11", DateOrder.DMY)).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(CricketValues.date("10/11/26", DateOrder.MDY)).isEqualTo(LocalDate.of(2026, 10, 11));
    }

    @Test
    void textualDates() {
        assertThat(CricketValues.date("11 Oct 2026", DateOrder.MDY)).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(CricketValues.date("Sat, Oct 11, 2026", DateOrder.DMY)).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(CricketValues.date("September 3rd 2026", DateOrder.DMY)).isEqualTo(LocalDate.of(2026, 9, 3));
        assertThat(CricketValues.date("TBC", DateOrder.MDY)).isNull();
        assertThat(CricketValues.date("13/13/2026", DateOrder.MDY)).isNull();
    }
}
