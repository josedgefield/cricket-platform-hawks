package sg.hawkscc.platform.stats.domain;

import java.util.Arrays;

/** A kind of source record. Leaderboard tabs plus a competition table. */
public enum StatKind {
    BATTING,
    BOWLING,
    FIELDING,
    STANDINGS;

    public String code() {
        return name().toLowerCase();
    }

    public static StatKind fromCode(String code) {
        return Arrays.stream(values())
                .filter(k -> k.code().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown kind: " + code));
    }
}
