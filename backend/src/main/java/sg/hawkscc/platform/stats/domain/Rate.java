package sg.hawkscc.platform.stats.domain;

import java.math.BigDecimal;

/**
 * A derived rate. {@code reported} is true when the value is the source's own figure
 * because the counts needed to calculate it are missing.
 */
public record Rate(BigDecimal value, boolean reported) {

    public static final Rate UNKNOWN = new Rate(null, false);

    public static Rate ofCalculated(BigDecimal value) {
        return value == null ? UNKNOWN : new Rate(value, false);
    }

    public static Rate ofReported(BigDecimal value) {
        return value == null ? UNKNOWN : new Rate(value, true);
    }
}
