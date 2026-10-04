package sg.hawkscc.platform.stats.domain;

import java.util.Arrays;

/** Where cricket data comes from. Stored as {@link #code()}. */
public enum Source {
    SCA("sca"),
    CRICHEROES("cricheroes"),
    MANUAL("manual");

    private final String code;

    Source(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Source fromCode(String code) {
        return Arrays.stream(values())
                .filter(s -> s.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown source: " + code));
    }
}
