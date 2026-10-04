package sg.hawkscc.api.sca.service;

import java.time.Instant;
import java.util.List;

import sg.hawkscc.api.sca.model.Dataset;
import sg.hawkscc.api.sca.model.ScaRecords.SyncState;

/** Every SCA endpoint returns the data together with where it came from and how fresh it is. */
public record ApiResponse<T>(T data, Meta meta) {

    public record Meta(List<Source> sources) {
    }

    /**
     * @param method   csv-export, html-table or manual-upload
     * @param syncedAt when the data was last fetched successfully; null if never
     * @param stale    true when syncedAt is older than sca.stale-after, or there is no data yet
     */
    public record Source(Dataset dataset, String url, String method, Instant syncedAt, boolean stale,
            SyncState lastSyncState, String lastSyncMessage) {
    }
}
