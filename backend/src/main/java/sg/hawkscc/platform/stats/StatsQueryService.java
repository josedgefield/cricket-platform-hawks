package sg.hawkscc.platform.stats;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import sg.hawkscc.platform.club.ClubContext;
import sg.hawkscc.platform.stats.domain.Overs;
import sg.hawkscc.platform.stats.domain.Source;
import sg.hawkscc.platform.stats.domain.StatLine;
import sg.hawkscc.platform.stats.domain.StatsCalculator;
import sg.hawkscc.platform.stats.persistence.StatsRepository;
import sg.hawkscc.platform.stats.persistence.StatsRepository.PlayerStatsRow;

@Service
public class StatsQueryService {

    private final StatsRepository repo;
    private final ClubContext club;

    public StatsQueryService(StatsRepository repo, ClubContext club) {
        this.repo = repo;
        this.club = club;
    }

    /**
     * Per-player stats, one source or all of them combined.
     *
     * @param source        null for all sources
     * @param competitionId null for every competition
     */
    public List<StatsViews.PlayerStats> players(Source source, UUID competitionId) {
        Map<UUID, List<PlayerStatsRow>> byPlayer = new LinkedHashMap<>();
        for (PlayerStatsRow row : repo.playerStats(club.clubId(), source, competitionId)) {
            byPlayer.computeIfAbsent(row.playerId(), id -> new ArrayList<>()).add(row);
        }
        return byPlayer.values().stream().map(StatsQueryService::toView).toList();
    }

    public List<StatsViews.Competition> competitions() {
        return repo.competitions(club.clubId()).stream()
                .map(c -> new StatsViews.Competition(c.id(), c.source().code(), c.name(), c.season(), c.externalId()))
                .toList();
    }

    public List<StatsViews.Standing> standings(UUID competitionId) {
        repo.findCompetition(club.clubId(), competitionId)
                .orElseThrow(() -> new NotFoundException("Competition " + competitionId + " not found"));
        return repo.standings(club.clubId(), competitionId).stream()
                .map(s -> new StatsViews.Standing(s.group(), s.position(), s.team(), s.clubTeam(), s.matches(),
                        s.won(), s.lost(), s.drawn(), s.tied(), s.noResult(), s.points(), s.netRunRate(),
                        s.runsFor(), s.runsAgainst(), s.lastFive()))
                .toList();
    }

    public List<StatsViews.SourceStatus> sources() {
        var known = repo.sourceStatuses(club.clubId());
        List<StatsViews.SourceStatus> out = new ArrayList<>();
        for (Source s : List.of(Source.SCA, Source.CRICHEROES)) {
            out.add(known.stream().filter(k -> k.source() == s).findFirst()
                    .map(k -> new StatsViews.SourceStatus(s.code(), k.lastSucceededAt(), k.lastRunAt(),
                            k.lastRunStatus(), k.lastError()))
                    .orElse(new StatsViews.SourceStatus(s.code(), null, null, null, null)));
        }
        return out;
    }

    /** "bat_balls" → "batting.balls", matching the JSON field names. */
    private static String apiField(String column) {
        return switch (column) {
            case "bat_balls" -> "batting.balls";
            case "bat_not_outs" -> "batting.notOuts";
            case "bowl_runs" -> "bowling.runs";
            case "bowl_balls" -> "bowling.balls";
            default -> column;
        };
    }

    private static StatsViews.PlayerStats toView(List<PlayerStatsRow> rows) {
        // Each row is one (competition, source) line for this player; StatsCalculator.combine adds
        // them up per category (see its rules), and coverage says which sources each total includes.
        StatLine s = StatsCalculator.combine(rows.stream().map(PlayerStatsRow::line).toList());
        List<String> sources = rows.stream().map(r -> r.source().code()).distinct().toList();
        var b = s.batting();
        var w = s.bowling();
        var f = s.fielding();
        return new StatsViews.PlayerStats(
                rows.getFirst().playerId(),
                rows.getFirst().displayName(),
                sources,
                s.matches(),
                b.isUnknown() ? null : new StatsViews.Batting(b.inns(), b.notOuts(), b.runs(), b.balls(),
                        b.highScore(), b.fours(), b.sixes(), StatsCalculator.battingAverage(s),
                        StatsCalculator.strikeRate(s)),
                w.isUnknown() ? null : new StatsViews.Bowling(w.inns(), Overs.format(w.balls()), w.balls(),
                        w.maidens(), w.runs(), w.wickets(), StatsCalculator.bowlingAverage(s),
                        StatsCalculator.economy(s), StatsCalculator.bowlingStrikeRate(s)),
                f.isUnknown() ? null : new StatsViews.Fielding(f.catches(), f.stumpings(), f.runOuts(),
                        StatsCalculator.fieldingDismissals(s)),
                rows.stream().flatMap(r -> r.recovered().stream()).map(StatsQueryService::apiField)
                        .distinct().sorted().toList(),
                new StatsViews.Coverage(
                        covered(rows, l -> !l.batting().isUnknown()),
                        covered(rows, l -> !l.bowling().isUnknown()),
                        covered(rows, l -> !l.fielding().isUnknown())));
    }

    private static List<String> covered(List<PlayerStatsRow> rows, java.util.function.Predicate<StatLine> listed) {
        return rows.stream().filter(r -> listed.test(r.line())).map(r -> r.source().code()).distinct().toList();
    }
}
