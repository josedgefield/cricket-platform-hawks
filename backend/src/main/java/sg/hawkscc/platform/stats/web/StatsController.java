package sg.hawkscc.platform.stats.web;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import sg.hawkscc.platform.stats.StatsQueryService;
import sg.hawkscc.platform.stats.StatsViews;
import sg.hawkscc.platform.stats.domain.Source;

/** Public, read-only stats. */
@RestController
@RequestMapping("/api/stats")
class StatsController {

    private final StatsQueryService stats;

    StatsController(StatsQueryService stats) {
        this.stats = stats;
    }

    /**
     * @param source        "sca", "cricheroes" or "all" (default)
     * @param competitionId optional; all competitions when omitted
     */
    @GetMapping("/players")
    List<StatsViews.PlayerStats> players(@RequestParam(defaultValue = "all") String source,
                                         @RequestParam(required = false) UUID competitionId) {
        return stats.players("all".equalsIgnoreCase(source) ? null : Source.fromCode(source), competitionId);
    }

    @GetMapping("/competitions")
    List<StatsViews.Competition> competitions() {
        return stats.competitions();
    }

    @GetMapping("/competitions/{competitionId}/standings")
    List<StatsViews.Standing> standings(@PathVariable UUID competitionId) {
        return stats.standings(competitionId);
    }

    @GetMapping("/sources")
    List<StatsViews.SourceStatus> sources() {
        return stats.sources();
    }
}
