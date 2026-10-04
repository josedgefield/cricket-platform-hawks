package sg.hawkscc.api.sca.web;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import sg.hawkscc.api.sca.config.ScaProperties;
import sg.hawkscc.api.sca.model.Dataset;
import sg.hawkscc.api.sca.model.RawTable;
import sg.hawkscc.api.sca.model.ScaRecords.BattingStat;
import sg.hawkscc.api.sca.model.ScaRecords.BowlingStat;
import sg.hawkscc.api.sca.model.ScaRecords.DatasetStatus;
import sg.hawkscc.api.sca.model.ScaRecords.FieldingStat;
import sg.hawkscc.api.sca.model.ScaRecords.Fixture;
import sg.hawkscc.api.sca.model.ScaRecords.LeaderBoard;
import sg.hawkscc.api.sca.model.ScaRecords.MatchResult;
import sg.hawkscc.api.sca.model.ScaRecords.Player;
import sg.hawkscc.api.sca.service.ApiResponse;
import sg.hawkscc.api.sca.service.ScaStatsService;
import sg.hawkscc.api.sca.service.ScaStatsService.PlayerProfile;
import sg.hawkscc.api.sca.service.ScaStatsService.SeasonRecord;
import sg.hawkscc.api.sca.sync.ScaSyncService;

/**
 * Query API over the Hawks team data exported from the SCA site (scores.cricketsingapore.com). Read endpoints are
 * public to the app; sync and import require the {@code X-Admin-Key} header.
 */
@RestController
@RequestMapping(path = "/api/sca", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "SCA stats", description = "Hawks players, results, schedule, batting, bowling and fielding from the SCA site")
public class ScaStatsController {

    private static final String ADMIN_HEADER = "X-Admin-Key";

    private final ScaStatsService stats;
    private final ScaSyncService sync;
    private final ScaProperties props;

    public ScaStatsController(ScaStatsService stats, ScaSyncService sync, ScaProperties props) {
        this.stats = stats;
        this.sync = sync;
        this.props = props;
    }

    // ---------------------------------------------------------------- players & stats

    @Operation(summary = "Squad list from the team page")
    @GetMapping("/players")
    public ApiResponse<List<Player>> players() {
        return stats.players();
    }

    @Operation(summary = "One player's combined profile, batting, bowling and fielding (by SCA playerId or name)")
    @GetMapping("/players/{idOrName}")
    public ApiResponse<PlayerProfile> player(@PathVariable String idOrName) {
        return stats.player(idOrName).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No player matching '" + idOrName + "'"));
    }

    @Operation(summary = "Season batting stats")
    @GetMapping("/batting")
    public ApiResponse<List<BattingStat>> batting(
            @Parameter(description = "runs, average, strikerate, innings, fours, sixes, name")
            @RequestParam(defaultValue = "runs") String sort,
            @RequestParam(defaultValue = "0") @Min(0) @Max(500) int limit) {
        return stats.batting(sort, limit);
    }

    @Operation(summary = "Season bowling stats")
    @GetMapping("/bowling")
    public ApiResponse<List<BowlingStat>> bowling(
            @Parameter(description = "wickets, economy, average, strikerate, overs, maidens, name")
            @RequestParam(defaultValue = "wickets") String sort,
            @RequestParam(defaultValue = "0") @Min(0) @Max(500) int limit) {
        return stats.bowling(sort, limit);
    }

    @Operation(summary = "Season fielding stats")
    @GetMapping("/fielding")
    public ApiResponse<List<FieldingStat>> fielding(
            @Parameter(description = "catches, dismissals, stumpings, runouts, name")
            @RequestParam(defaultValue = "catches") String sort,
            @RequestParam(defaultValue = "0") @Min(0) @Max(500) int limit) {
        return stats.fielding(sort, limit);
    }

    @Operation(summary = "Season leaders: runs, average, strike rate, wickets, economy, catches, dismissals")
    @GetMapping("/leaders")
    public ApiResponse<List<LeaderBoard>> leaders(@RequestParam(defaultValue = "3") @Min(1) @Max(20) int top) {
        return stats.leaders(top);
    }

    // ---------------------------------------------------------------- matches

    @Operation(summary = "Match results this season, newest first")
    @GetMapping("/results")
    public ApiResponse<List<MatchResult>> results(
            @RequestParam(required = false) String competition,
            @RequestParam(required = false) String opponent,
            @RequestParam(defaultValue = "0") @Min(0) @Max(500) int limit) {
        return stats.results(competition, opponent, limit);
    }

    @Operation(summary = "Season win/loss record and last-five form")
    @GetMapping("/record")
    public ApiResponse<SeasonRecord> record() {
        return stats.record();
    }

    @Operation(summary = "Upcoming matches (Singapore date), soonest first")
    @GetMapping("/schedule")
    public ApiResponse<List<Fixture>> schedule(
            @RequestParam(defaultValue = "false") boolean includePast,
            @RequestParam(defaultValue = "0") @Min(0) @Max(500) int limit) {
        return stats.schedule(includePast, limit);
    }

    @Operation(summary = "The next upcoming match, or 204 when none is scheduled")
    @GetMapping("/schedule/next")
    public ResponseEntity<ApiResponse<Fixture>> nextMatch() {
        ApiResponse<List<Fixture>> upcoming = stats.schedule(false, 1);
        if (upcoming.data().isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(new ApiResponse<>(upcoming.data().get(0), upcoming.meta()));
    }

    // ---------------------------------------------------------------- source & sync

    @Operation(summary = "The table exactly as exported by the SCA site (all columns)")
    @GetMapping("/raw/{dataset}")
    public ApiResponse<RawTable> raw(@PathVariable String dataset) {
        return stats.raw(dataset(dataset));
    }

    @Operation(summary = "Last sync result per dataset")
    @GetMapping("/sync/status")
    public ApiResponse<Map<Dataset, DatasetStatus>> syncStatus() {
        return stats.status();
    }

    @Operation(summary = "Start a sync now (admin). Returns 202; poll /api/sca/sync/status")
    @PostMapping("/sync")
    public ResponseEntity<Map<String, String>> syncNow(@RequestHeader(name = ADMIN_HEADER, required = false) String key) {
        requireAdmin(key);
        if (sync.isRunning()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("status", "already-running"));
        }
        CompletableFuture.runAsync(sync::syncAll);
        return ResponseEntity.accepted().location(URI.create("/api/sca/sync/status")).body(Map.of("status", "started"));
    }

    @Operation(summary = "Upload a CSV downloaded from the SCA page's CSV button (admin)")
    @PostMapping(path = "/import/{dataset}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DatasetStatus importCsv(@PathVariable String dataset, @RequestPart("file") MultipartFile file,
            @RequestHeader(name = ADMIN_HEADER, required = false) String key) throws IOException {
        requireAdmin(key);
        if (file.isEmpty()) {
            throw new IllegalArgumentException("The uploaded file is empty");
        }
        return sync.importCsv(dataset(dataset), new String(file.getBytes(), StandardCharsets.UTF_8));
    }

    private static Dataset dataset(String slug) {
        return Dataset.fromSlug(slug).orElseThrow(() -> new IllegalArgumentException(
                "Unknown dataset '" + slug + "'. Use one of players, results, schedule, batting, bowling, fielding"));
    }

    private void requireAdmin(String key) {
        if (!props.adminEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Admin endpoints are disabled: set SCA_ADMIN_KEY to enable them");
        }
        if (key == null || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8),
                props.adminKey().getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid " + ADMIN_HEADER);
        }
    }
}
