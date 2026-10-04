package sg.hawkscc.api.sca.store;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import sg.hawkscc.api.sca.config.ScaProperties;
import sg.hawkscc.api.sca.model.Dataset;
import sg.hawkscc.api.sca.model.ScaRecords.DatasetSnapshot;
import sg.hawkscc.api.sca.model.ScaRecords.DatasetStatus;
import sg.hawkscc.api.sca.model.ScaRecords.SyncState;

/**
 * Holds the latest snapshot of each dataset and its sync status, persisted as JSON so a restart keeps serving the
 * last good data. Every distinct raw export is also archived as CSV (named by fetch time and content hash), so the
 * normalised data can always be rebuilt from what the source actually provided.
 *
 * <p>File storage is deliberate for this first slice; the planned Postgres tables ({@code source_records},
 * {@code sync_runs}, see docs/04) replace it when the database lands.
 */
@Component
public class SnapshotStore {

    private static final Logger log = LoggerFactory.getLogger(SnapshotStore.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private record State(Map<Dataset, DatasetSnapshot> snapshots, Map<Dataset, DatasetStatus> statuses) {
    }

    private final ObjectMapper mapper;
    private final Path dir;
    private final Map<Dataset, DatasetSnapshot> snapshots = new EnumMap<>(Dataset.class);
    private final Map<Dataset, DatasetStatus> statuses = new EnumMap<>(Dataset.class);

    public SnapshotStore(ObjectMapper mapper, ScaProperties props) {
        this.mapper = mapper;
        this.dir = Path.of(props.storageDir());
    }

    @PostConstruct
    public void load() {
        for (Dataset d : Dataset.values()) {
            statuses.put(d, new DatasetStatus(d, SyncState.NEVER_RUN, null, null, null, null, null));
        }
        Path file = dir.resolve("snapshot.json");
        if (!Files.exists(file)) {
            return;
        }
        try {
            State state = mapper.readValue(file.toFile(), new TypeReference<State>() {
            });
            if (state.snapshots() != null) {
                snapshots.putAll(state.snapshots());
            }
            if (state.statuses() != null) {
                statuses.putAll(state.statuses());
            }
            log.info("Loaded SCA snapshot with {} datasets from {}", snapshots.size(), file);
        } catch (IOException e) {
            log.error("Could not read {}; starting empty. The file is left in place for inspection.", file, e);
        }
    }

    public synchronized Optional<DatasetSnapshot> snapshot(Dataset d) {
        return Optional.ofNullable(snapshots.get(d));
    }

    public synchronized DatasetStatus status(Dataset d) {
        return statuses.get(d);
    }

    public synchronized Map<Dataset, DatasetStatus> statuses() {
        return new EnumMap<>(statuses);
    }

    /** Stores a new snapshot (and archives its raw CSV) together with its status, then persists. */
    public synchronized void save(DatasetSnapshot snapshot, DatasetStatus status) throws IOException {
        archive(snapshot);
        snapshots.put(snapshot.dataset(), snapshot);
        statuses.put(snapshot.dataset(), status);
        persist();
    }

    /** Records a status without replacing the last good data (used for failures and unchanged runs). */
    public synchronized void saveStatus(DatasetStatus status) {
        statuses.put(status.dataset(), status);
        try {
            persist();
        } catch (IOException e) {
            log.error("Could not persist SCA sync status", e);
        }
    }

    private void archive(DatasetSnapshot s) throws IOException {
        Path folder = dir.resolve("raw").resolve(s.dataset().slug());
        Files.createDirectories(folder);
        Path file = folder.resolve(STAMP.format(s.fetchedAt()) + "-" + s.contentSha256().substring(0, 12) + ".csv");
        Files.writeString(file, s.raw().toCsv(), StandardCharsets.UTF_8);
    }

    private void persist() throws IOException {
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, "snapshot", ".json.tmp");
        mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(),
                new State(new EnumMap<>(snapshots), new EnumMap<>(statuses)));
        Files.move(tmp, dir.resolve("snapshot.json"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
