package sg.hawkscc.platform.stats.web;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import sg.hawkscc.platform.stats.ImportResult;
import sg.hawkscc.platform.stats.PlayerLinkService;
import sg.hawkscc.platform.stats.StatsImportService;
import sg.hawkscc.platform.stats.domain.Source;
import sg.hawkscc.platform.stats.domain.StatKind;

/** Stats admin: create competitions and import source tables. Requires ROLE_STATS_ADMIN. */
@RestController
@RequestMapping("/api/admin/stats")
class StatsAdminController {

    private final StatsImportService imports;
    private final PlayerLinkService links;

    StatsAdminController(StatsImportService imports, PlayerLinkService links) {
        this.imports = imports;
        this.links = links;
    }

    record NewLink(@NotBlank @Size(max = 120) String player, @NotBlank String source,
                   @NotBlank @Size(max = 120) String sourceName) {
    }

    /**
     * Says that a name as one source prints it is this player (e.g. CricHeroes "Puttur Shreyas"
     * is "Shreyas Puttur"), so their figures add up. Stats already stored under that name move
     * across; 409 if both have figures for the same competition.
     */
    @PostMapping(path = "/player-links", consumes = MediaType.APPLICATION_JSON_VALUE)
    PlayerLinkService.LinkResult link(@Valid @RequestBody NewLink body, Principal principal) {
        return links.link(body.player(), Source.fromCode(body.source()), body.sourceName(), principal.getName());
    }

    /** The same, for a whole list as CSV with the columns Player, Source and Source name. */
    @PostMapping(path = "/player-links", consumes = {"text/csv", MediaType.TEXT_PLAIN_VALUE})
    List<PlayerLinkService.LinkResult> linkAll(@RequestBody String csv, Principal principal) {
        return links.linkAll(csv, principal.getName());
    }

    record NewCompetition(@NotBlank String source, @NotBlank @Size(max = 120) String name,
                          @Size(max = 40) String season, @Size(max = 80) String externalId) {
    }

    record CreatedCompetition(UUID id) {
    }

    @PostMapping("/competitions")
    @ResponseStatus(HttpStatus.CREATED)
    CreatedCompetition createCompetition(@Valid @RequestBody NewCompetition body) {
        return new CreatedCompetition(imports.ensureCompetition(Source.fromCode(body.source()), body.name().strip(),
                body.season(), body.externalId()));
    }

    /**
     * Import one leaderboard tab or a points table, sent as CSV or tab-separated text
     * (for example a table copied from the source website).
     *
     * @param kind       batting, bowling, fielding or standings
     * @param group      required for standings, e.g. "Supreme (league matches)"
     * @param origin     where it came from, e.g. the downloaded file name
     * @param capturedOn when the source page was captured
     */
    @PostMapping(path = "/competitions/{competitionId}/imports/{kind}",
            consumes = {"text/csv", MediaType.TEXT_PLAIN_VALUE})
    ImportResult importTable(@PathVariable UUID competitionId, @PathVariable String kind,
                             @RequestParam(required = false) String group,
                             @RequestParam(required = false) String origin,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                             LocalDate capturedOn,
                             @RequestBody String content, Principal principal) {
        StatKind statKind = StatKind.fromCode(kind);
        String actor = principal.getName();
        if (statKind == StatKind.STANDINGS) {
            return imports.importStandings(new StatsImportService.StandingsImport(competitionId, group, content,
                    origin, capturedOn, actor));
        }
        return imports.importLeaderboard(new StatsImportService.LeaderboardImport(competitionId, statKind, content,
                origin, capturedOn, actor));
    }
}
