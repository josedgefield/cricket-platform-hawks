package sg.hawkscc.platform.stats.domain;

import java.text.Normalizer;
import java.util.Locale;

/** Normalises a player name as a source printed it, for alias matching. */
public final class NameKey {

    private NameKey() {
    }

    public static String of(String sourceName) {
        String ascii = Normalizer.normalize(sourceName == null ? "" : sourceName, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "");
        return ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z ]", "")
                .replaceAll("\\s+", " ")
                .strip();
    }
}
