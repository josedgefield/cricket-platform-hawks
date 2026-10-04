package sg.hawkscc.platform.stats.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Overs are stored as balls (3.4 overs = 22 balls) so arithmetic is exact. */
public final class Overs {

    private static final Pattern OVERS = Pattern.compile("^(\\d+)(?:\\.(\\d))?$");

    private Overs() {
    }

    /**
     * Parses "3.4" to 22. Blank or "-" returns null (unknown).
     *
     * @throws IllegalArgumentException if malformed or the ball part is greater than 5
     */
    public static Integer toBalls(String overs) {
        if (overs == null || overs.isBlank() || overs.strip().equals("-") || overs.strip().equals("—")) {
            return null;
        }
        Matcher m = OVERS.matcher(overs.strip());
        if (!m.matches()) {
            throw new IllegalArgumentException("Overs \"" + overs + "\" is not valid");
        }
        int balls = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
        if (balls > 5) {
            throw new IllegalArgumentException("Overs \"" + overs + "\" is not valid (ball part must be 0-5)");
        }
        return Integer.parseInt(m.group(1)) * 6 + balls;
    }

    /** 22 balls → "3.4"; 24 → "4"; null → null. */
    public static String format(Integer balls) {
        if (balls == null) {
            return null;
        }
        return balls % 6 == 0 ? String.valueOf(balls / 6) : (balls / 6) + "." + (balls % 6);
    }
}
