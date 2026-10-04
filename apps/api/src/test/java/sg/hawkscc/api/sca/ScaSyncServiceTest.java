package sg.hawkscc.api.sca;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpServer;

import sg.hawkscc.api.sca.client.ScaHttpClient;
import sg.hawkscc.api.sca.config.ScaProperties;
import sg.hawkscc.api.sca.model.Dataset;
import sg.hawkscc.api.sca.model.ScaRecords.DatasetStatus;
import sg.hawkscc.api.sca.model.ScaRecords.SyncState;
import sg.hawkscc.api.sca.store.SnapshotStore;
import sg.hawkscc.api.sca.sync.ScaSyncService;

/** Runs the real sync pipeline against a local HTTP server serving synthetic SCA-like pages. */
class ScaSyncServiceTest {

    @TempDir
    Path tmp;

    private HttpServer server;
    private final Map<String, Page> pages = new ConcurrentHashMap<>();
    private final AtomicInteger teamPageHits = new AtomicInteger();

    private record Page(int status, String contentType, String body) {
    }

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("viewTeam.do")) {
                teamPageHits.incrementAndGet();
            }
            Page page = pages.getOrDefault(path, new Page(404, "text/plain", "not found"));
            byte[] body = page.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", page.contentType());
            exchange.sendResponseHeaders(page.status(), body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();

        String p = "/SingaporeCricketAssoc/";
        pages.put("/robots.txt", new Page(200, "text/plain", "User-agent: *\nDisallow: /SingaporeCricketAssoc/admin\n"));
        // Batting: a real CSV link.
        pages.put(p + "teamBatting.do", html("""
                <a class="btn" href="exportTeamBatting.do?teamId=2291&clubId=7683">CSV</a>
                <table><thead><tr><th>Player</th><th>Runs</th></tr></thead><tbody>
                <tr><td><a href="viewPlayer.do?playerId=11">Player Alpha</a></td><td>312</td></tr>
                <tr><td><a href="viewPlayer.do?playerId=12">Player Bravo</a></td><td>205</td></tr>
                </tbody></table>"""));
        pages.put(p + "exportTeamBatting.do", new Page(200, "text/csv", "Player,Runs,Balls\nPlayer Alpha,312,240\nPlayer Bravo,205,190\n"));
        // Bowling: client-side DataTables CSV button only → table fallback.
        pages.put(p + "teamBowling.do", html("""
                <button class="dt-button buttons-csv">CSV</button>
                <table><thead><tr><th>Player</th><th>Overs</th><th>Wkts</th></tr></thead><tbody>
                <tr><td>Player Echo</td><td>28.4</td><td>14</td></tr></tbody></table>"""));
        // Fielding: export link returns an HTML login page → table fallback.
        pages.put(p + "teamFielding.do", html("""
                <a href="exportFielding.do">Export</a>
                <table><thead><tr><th>Player</th><th>Catches</th></tr></thead><tbody>
                <tr><td>Player Hotel</td><td>10</td></tr></tbody></table>"""));
        pages.put(p + "exportFielding.do", new Page(200, "text/html", "<!DOCTYPE html><html>Please log in</html>"));
        // Players: server error on every attempt.
        pages.put(p + "viewTeam.do", new Page(503, "text/html", "down"));
        pages.put(p + "teamResults.do", html("""
                <table><thead><tr><th>Date</th><th>Team</th><th>Team</th><th>Result</th></tr></thead><tbody>
                <tr><td>09/27/2026</td><td>Hawks</td><td>Kallang Kings XI</td><td>Hawks won by 22 runs</td></tr></tbody></table>"""));
        pages.put(p + "teamSchedule.do", html("""
                <table><thead><tr><th>Date</th><th>Team One</th><th>Team Two</th><th>Ground</th></tr></thead><tbody>
                <tr><td>10/11/2026</td><td>Hawks</td><td>Marina Mariners XI</td><td>Ground A</td></tr></tbody></table>"""));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static Page html(String body) {
        return new Page(200, "text/html; charset=utf-8", "<!DOCTYPE html><html><body>" + body + "</body></html>");
    }

    private ScaSyncService service(SnapshotStore store) {
        ScaProperties props = TestProps.withBase(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/SingaporeCricketAssoc", tmp.toString());
        Clock clock = Clock.fixed(Instant.parse("2026-10-04T02:00:00Z"), ZoneId.of("Asia/Singapore"));
        return new ScaSyncService(props, new ScaHttpClient(props), store, clock);
    }

    private SnapshotStore store() {
        ScaProperties props = TestProps.defaults(tmp.toString());
        SnapshotStore store = new SnapshotStore(new ObjectMapper().registerModule(new JavaTimeModule()), props);
        store.load();
        return store;
    }

    @Test
    void syncsEachDatasetByTheBestAvailableMethod() throws IOException {
        SnapshotStore store = store();
        List<DatasetStatus> report = service(store).syncAll().orElseThrow();
        Map<Dataset, DatasetStatus> byDataset = report.stream().collect(Collectors.toMap(DatasetStatus::dataset, s -> s));

        assertThat(byDataset.get(Dataset.BATTING).state()).isEqualTo(SyncState.OK);
        assertThat(byDataset.get(Dataset.BATTING).method()).isEqualTo(ScaSyncService.METHOD_CSV);
        assertThat(byDataset.get(Dataset.BOWLING).method()).isEqualTo(ScaSyncService.METHOD_HTML);
        assertThat(byDataset.get(Dataset.FIELDING).method()).isEqualTo(ScaSyncService.METHOD_HTML);
        assertThat(byDataset.get(Dataset.RESULTS).state()).isEqualTo(SyncState.OK);
        assertThat(byDataset.get(Dataset.SCHEDULE).rowCount()).isEqualTo(1);

        DatasetStatus players = byDataset.get(Dataset.PLAYERS);
        assertThat(players.state()).isEqualTo(SyncState.FAILED);
        assertThat(players.message()).contains("503");
        assertThat(teamPageHits.get()).isEqualTo(2); // retried up to max-attempts

        // CSV rows borrow player ids from the on-page table when rows line up.
        var batting = store.snapshot(Dataset.BATTING).orElseThrow();
        assertThat(batting.raw().headers()).containsExactly("Player", "Runs", "Balls");
        assertThat(batting.raw().idsFor(0)).containsEntry("playerid", "11");

        // Raw exports are archived and the snapshot is persisted.
        assertThat(Files.list(tmp.resolve("raw").resolve("batting")).count()).isEqualTo(1);
        assertThat(tmp.resolve("snapshot.json")).exists();
    }

    @Test
    void unchangedContentIsNotRestoredAndFailuresKeepLastGoodData() throws IOException {
        SnapshotStore store = store();
        ScaSyncService sync = service(store);
        sync.syncAll();

        pages.put("/SingaporeCricketAssoc/teamResults.do", new Page(500, "text/html", "oops"));
        List<DatasetStatus> second = sync.syncAll().orElseThrow();

        assertThat(second).filteredOn(s -> s.dataset() == Dataset.BATTING)
                .extracting(DatasetStatus::state).containsExactly(SyncState.UNCHANGED);
        assertThat(Files.list(tmp.resolve("raw").resolve("batting")).count()).isEqualTo(1);

        DatasetStatus results = store.status(Dataset.RESULTS);
        assertThat(results.state()).isEqualTo(SyncState.FAILED);
        assertThat(results.lastSuccessAt()).isNotNull();
        assertThat(store.snapshot(Dataset.RESULTS)).isPresent();

        // A fresh store reloads the persisted snapshot after a restart.
        assertThat(store().snapshot(Dataset.RESULTS)).isPresent();
    }

    @Test
    void unreachableSiteFailsEveryDatasetButKeepsData() {
        SnapshotStore store = store();
        ScaSyncService sync = service(store);
        sync.syncAll();
        server.stop(0);

        List<DatasetStatus> report = sync.syncAll().orElseThrow();
        assertThat(report).allSatisfy(s -> {
            assertThat(s.state()).isEqualTo(SyncState.FAILED);
            assertThat(s.message()).startsWith("SCA site unreachable");
        });
        assertThat(store.snapshot(Dataset.BATTING)).isPresent();
    }

    @Test
    void robotsDisallowSkipsDataset() {
        pages.put("/robots.txt", new Page(200, "text/plain", "User-agent: *\nDisallow: /SingaporeCricketAssoc/teamSchedule.do\n"));
        List<DatasetStatus> report = service(store()).syncAll().orElseThrow();
        assertThat(report).filteredOn(s -> s.dataset() == Dataset.SCHEDULE)
                .extracting(DatasetStatus::state).containsExactly(SyncState.SKIPPED_ROBOTS);
    }
}
