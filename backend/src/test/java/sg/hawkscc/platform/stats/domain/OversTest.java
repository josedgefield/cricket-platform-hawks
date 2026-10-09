package sg.hawkscc.platform.stats.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OversTest {

    @Test
    void convertsOversToBallsAndBack() {
        assertThat(Overs.toBalls("3.4")).isEqualTo(22);
        assertThat(Overs.toBalls("4")).isEqualTo(24);
        assertThat(Overs.toBalls(" 21.1 ")).isEqualTo(127);
        assertThat(Overs.format(22)).isEqualTo("3.4");
        assertThat(Overs.format(24)).isEqualTo("4");
    }

    @Test
    void blankIsUnknownNotZero() {
        assertThat(Overs.toBalls("")).isNull();
        assertThat(Overs.toBalls("-")).isNull();
        assertThat(Overs.toBalls(null)).isNull();
        assertThat(Overs.format(null)).isNull();
    }

    @Test
    void rejectsImpossibleBallPart() {
        assertThatThrownBy(() -> Overs.toBalls("3.6")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Overs.toBalls("three")).isInstanceOf(IllegalArgumentException.class);
    }
}
