package sg.hawkscc.api.sca.sync;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import sg.hawkscc.api.sca.client.RobotsPolicy;
import sg.hawkscc.api.sca.client.ScaHttpClient;
import sg.hawkscc.api.sca.config.ScaProperties;
import sg.hawkscc.api.sca.extract.CsvTableReader;
import sg.hawkscc.api.sca.extract.ExportLinkFinder;
import sg.hawkscc.api.sca.extract.HtmlTableExtractor;
import sg.hawkscc.api.sca.model.Dataset;
import sg.hawkscc.api.sca.model.RawTable;
import sg.hawkscc.api.sca.model.ScaRecords.DatasetSnapshot;
import sg.hawkscc.api.sca.model.ScaRecords.DatasetStatus;
import sg.hawkscc.api.sca.model.ScaRecords.SyncState;
import sg.hawkscc.api.sca.store.SnapshotStore;

/**
 * Pulls the six Hawks team datasets from the SCA site using each page's CSV export.
 *
 * <p>Per dataset: load the page → follow its CSV export link if it has a real URL → otherwise (when allowed) read
 * the same table the CSV button exports → archive the raw table → keep the last good data if anything fails.
 * Unchanged content (same SHA-256) is recorded as UNCHANGED and not re-stored.
 */
@Service
public class ScaSyncService {

    public static final String METHOD_CSV = "csv-export";
    public static final String METHOD_HTML = "html-table";
    public static final String METHOD_UPLOAD = "manual-upload";

    private static final Logger log = LoggerFactory.getLogger(ScaSyncService.class);

    private final ScaProperties props;
    private final ScaHttpClient client;
    private final SnapshotStore store;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ScaSyncService(ScaProperties props, ScaHttpClient client, SnapshotStore store, Clock clock) {
        this.props = props;
        this.client = client;
        this.store = store;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    void syncOnStartup() {
        if (props.enabled() && props.syncOnStartup()) {
            CompletableFuture.runAsync(this::syncAllQuietly);
        }
    }

    @Scheduled(cron = "${sca.sync-cron}", zone = "${sca.sync-zone}")
    void scheduledSync() {
        if (props.enabled()) {
            syncAllQuietly();
        }
    }

    private void syncAllQuietly() {
        try {
            syncAll();
        } catch (RuntimeException e) {
            log.error("SCA sync crashed", e);
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    /** Syncs every dataset in turn. Returns empty if a sync is already in progress. */
    public Optional<List<DatasetStatus>> syncAll() {
        if (!running.compareAndSet(false, true)) {
            return Optional.empty();
        }
        try {
            List<DatasetStatus> report = new ArrayList<>();
            RobotsPolicy robots;
            try {
                robots = loadRobots();
            } catch (IOException e) {
                Instant now = clock.instant();
                for (Dataset d : Dataset.values()) {
                    report.add(fail(d, SyncState.FAILED, now, "SCA site unreachable: " + e.getMessage()));
                }
                return Optional.of(report);
            }
            for (Dataset d : Dataset.values()) {
                report.add(syncOne(d, robots));
            }
            return Optional.of(report);
        } finally {
            running.set(false);
        }
    }

    public URI pageUri(Dataset d) {
        return URI.create("%s/%s?teamId=%d&clubId=%d".formatted(props.baseUrl(), d.page(), props.teamId(), props.clubId()));
    }

    DatasetStatus syncOne(Dataset d, RobotsPolicy robots) {
        URI page = pageUri(d);
        Instant now = clock.instant();
        String pathAndQuery = page.getRawPath() + (page.getRawQuery() == null ? "" : "?" + page.getRawQuery());
        if (!robots.isAllowed(pathAndQuery)) {
            return fail(d, SyncState.SKIPPED_ROBOTS, now, "robots.txt disallows " + page.getRawPath());
        }
        try {
            ScaHttpClient.Response res = client.get(page, "text/html,application/xhtml+xml");
            if (!res.ok()) {
                return fail(d, SyncState.FAILED, now, "Page returned HTTP " + res.status());
            }
            Document doc = Jsoup.parse(res.body(), res.finalUri().toString());
            Optional<RawTable> pageTable = HtmlTableExtractor.extract(doc, d.tableHints());

            RawTable table = null;
            String method = null;
            Optional<URI> export = ExportLinkFinder.find(doc);
            if (export.isPresent() && robots.isAllowed(export.get().getRawPath())) {
                ScaHttpClient.Response csv = client.get(export.get(), "text/csv,application/csv,*/*;q=0.5");
                if (csv.ok() && CsvTableReader.looksLikeCsv(csv.body(), csv.contentType())) {
                    table = withRowIds(CsvTableReader.read(csv.body()), pageTable);
                    method = METHOD_CSV;
                } else {
                    log.warn("SCA {} export link {} did not return CSV (HTTP {}, {})", d, export.get(), csv.status(),
                            csv.contentType());
                }
            }
            if (table == null && props.allowHtmlTableFallback() && pageTable.isPresent()) {
                table = pageTable.get();
                method = METHOD_HTML;
            }
            if (table == null || table.headers().isEmpty()) {
                return fail(d, SyncState.FAILED, now, export.isPresent()
                        ? "CSV export did not return CSV and no usable table was found"
                        : "No CSV export link and no data table found on the page");
            }
            return store(d, table, page.toString(), method, now);
        } catch (IOException e) {
            return fail(d, SyncState.FAILED, now, "Network error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fail(d, SyncState.FAILED, now, "Interrupted");
        } catch (RuntimeException e) {
            log.error("SCA {} sync failed unexpectedly", d, e);
            return fail(d, SyncState.FAILED, now, "Unexpected error: " + e.getClass().getSimpleName());
        }
    }

    /** Stores a CSV a stats admin downloaded and uploaded by hand (same pipeline as an automatic export). */
    public DatasetStatus importCsv(Dataset d, String csv) throws IOException {
        RawTable table = CsvTableReader.read(csv);
        if (table.headers().isEmpty()) {
            throw new IllegalArgumentException("The file has no header row");
        }
        return store(d, table, pageUri(d).toString(), METHOD_UPLOAD, clock.instant());
    }

    private DatasetStatus store(Dataset d, RawTable table, String source, String method, Instant now) throws IOException {
        String sha = table.sha256();
        Optional<DatasetSnapshot> previous = store.snapshot(d);
        if (previous.isPresent() && previous.get().contentSha256().equals(sha)) {
            DatasetStatus status = new DatasetStatus(d, SyncState.UNCHANGED, now, now, method, table.rows().size(),
                    "No changes since " + previous.get().fetchedAt());
            store.saveStatus(status);
            return status;
        }
        DatasetStatus status = new DatasetStatus(d, SyncState.OK, now, now, method, table.rows().size(), null);
        store.save(new DatasetSnapshot(d, source, method, now, sha, table), status);
        log.info("SCA {} synced via {}: {} rows", d, method, table.rows().size());
        return status;
    }

    private DatasetStatus fail(Dataset d, SyncState state, Instant now, String message) {
        DatasetStatus previous = store.status(d);
        DatasetStatus status = new DatasetStatus(d, state, now, previous == null ? null : previous.lastSuccessAt(),
                previous == null ? null : previous.method(), previous == null ? null : previous.rowCount(), message);
        store.saveStatus(status);
        log.warn("SCA {} sync {}: {}", d, state, message);
        return status;
    }

    /** CSV downloads carry no links; borrow player/match ids from the on-page table when the rows line up. */
    private static RawTable withRowIds(RawTable csv, Optional<RawTable> page) {
        if (page.isPresent() && page.get().rows().size() == csv.rows().size() && !page.get().rowIds().isEmpty()) {
            return new RawTable(csv.headers(), csv.rows(), page.get().rowIds());
        }
        return csv;
    }

    /** Reads robots.txt. Throws when the site cannot be reached at all, so the run is reported as a failure. */
    private RobotsPolicy loadRobots() throws IOException {
        if (!props.respectRobotsTxt()) {
            return RobotsPolicy.allowAll();
        }
        URI robots = URI.create(props.baseUrl()).resolve("/robots.txt");
        try {
            ScaHttpClient.Response res = client.get(robots, "text/plain");
            if (res.ok()) {
                return RobotsPolicy.parse(res.body(), props.userAgent());
            }
            // Common crawler convention: 4xx = no restrictions, 5xx = treat the site as off-limits for now.
            if (res.status() >= 500) {
                log.warn("{} returned HTTP {}; skipping this run", robots, res.status());
                return RobotsPolicy.parse("User-agent: *\nDisallow: /", props.userAgent());
            }
            return RobotsPolicy.allowAll();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while reading robots.txt", e);
        }
    }
}
