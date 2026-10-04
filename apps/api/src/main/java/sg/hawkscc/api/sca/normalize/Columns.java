package sg.hawkscc.api.sca.normalize;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Maps source column headers to our field names using alias lists, so the parser does not depend on the exact
 * header spelling of the SCA export. Supports repeated headers (e.g. two "Team" columns) by occurrence.
 */
final class Columns {

    private final List<String> headers;
    private final List<String> normalised;
    private final Set<Integer> claimed = new HashSet<>();

    Columns(List<String> headers) {
        this.headers = headers;
        this.normalised = headers.stream().map(Columns::norm).toList();
    }

    static String norm(String header) {
        return header == null ? "" : header.toLowerCase(Locale.ROOT)
                .replace("'", "")
                .replace(".", "")
                .replace("_", " ")
                .replaceAll("[^a-z0-9/ ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** Index of the first unclaimed column matching any alias (in alias priority order), or -1. */
    int claim(String... aliases) {
        for (String alias : aliases) {
            for (int i = 0; i < normalised.size(); i++) {
                if (!claimed.contains(i) && normalised.get(i).equals(alias)) {
                    claimed.add(i);
                    return i;
                }
            }
        }
        return -1;
    }

    /** Like {@link #claim} but matches headers that contain the alias as a whole word sequence. */
    int claimContaining(String... aliases) {
        int exact = claim(aliases);
        if (exact >= 0) {
            return exact;
        }
        for (String alias : aliases) {
            for (int i = 0; i < normalised.size(); i++) {
                if (!claimed.contains(i) && (" " + normalised.get(i) + " ").contains(" " + alias + " ")) {
                    claimed.add(i);
                    return i;
                }
            }
        }
        return -1;
    }

    static String cell(List<String> row, int index) {
        if (index < 0 || index >= row.size()) {
            return null;
        }
        String v = row.get(index);
        return v == null || v.isBlank() ? null : v.trim();
    }

    /** Every column not claimed by a field, keyed by its original header, so the export loses nothing. */
    Map<String, String> extra(List<String> row) {
        Map<String, String> extra = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            if (!claimed.contains(i)) {
                String value = cell(row, i);
                if (value != null) {
                    String key = headers.get(i).isBlank() ? "column" + (i + 1) : headers.get(i);
                    extra.merge(key, value, (a, b) -> a + " | " + b);
                }
            }
        }
        return extra;
    }

}
