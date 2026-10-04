package sg.hawkscc.platform.stats;

import java.util.List;
import java.util.UUID;

/**
 * Outcome of an import. {@code newPlayers} are source names seen for the first time;
 * they form the stats admin's mapping queue (link to a member, or merge duplicates).
 */
public record ImportResult(Status status, String kind, int rowsApplied, int playersCreated,
                           List<String> newPlayers, List<String> warnings, UUID sourceRecordId) {

    public enum Status {
        IMPORTED,
        /** Identical content was imported before; nothing changed. */
        UNCHANGED
    }
}
