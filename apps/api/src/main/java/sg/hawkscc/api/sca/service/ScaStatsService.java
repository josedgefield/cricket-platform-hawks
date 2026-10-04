package sg.hawkscc.api.sca.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import sg.hawkscc.api.sca.config.ScaProperties;
import sg.hawkscc.api.sca.model.Dataset;
import sg.hawkscc.api.sca.model.RawTable;
import sg.hawkscc.api.sca.model.ScaRecords.BattingStat;
import sg.hawkscc.api.sca.model.ScaRecords.BowlingStat;
import sg.hawkscc.api.sca.model.ScaRecords.DatasetSnapshot;
import sg.hawkscc.api.sca.model.ScaRecords.DatasetStatus;
import sg.hawkscc.api.sca.model.ScaRecords.FieldingStat;
import sg.hawkscc.api.sca.model.ScaRecords.Fixture;
import sg.hawkscc.api.sca.model.ScaRecords.Leader;
import sg.hawkscc.api.sca.model.ScaRecords.LeaderBoard;
import sg.hawkscc.api.sca.model.ScaRecords.MatchResult;
import sg.hawkscc.api.sca.model.ScaRecords.Player;
import sg.hawkscc.api.sca.normalize.ScaNormalizer;
import sg.hawkscc.api.sca.store.SnapshotStore;
import sg.hawkscc.api.sca.sync.ScaSyncService;

/** Read-side queries over the latest SCA snapshots. Nothing here fabricates values: missing stays null. */
@Service
public class ScaStatsService {

    private final SnapshotStore store;
    private final ScaNormalizer normalizer;
    private final ScaSyncService sync;
    private final ScaProperties props;
    private final Clock clock;
    private final Map<Dataset, Cached> cache = new EnumMap<>(Dataset.class);

    private record Cached(String sha, List<?> records) {
    }

    public ScaStatsService(SnapshotStore store, ScaNormalizer normalizer, ScaSyncService sync, ScaProperties props,
            Clock clock) {
        this.store = store;
        this.normalizer = normalizer;
        this.sync = sync;
        this.props = props;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- datasets

    public ApiResponse<List<Player>> players() {
        return respond(records(Dataset.PLAYERS, normalizer::players), Dataset.PLAYERS);
    }

    public ApiResponse<List<BattingStat>> batting(String sort, int limit) {
        List<BattingStat> rows = sorted(records(Dataset.BATTING, normalizer::batting), sort, BATTING_SORTS, "runs");
        return respond(limit(rows, limit), Dataset.BATTING);
    }

    public ApiResponse<List<BowlingStat>> bowling(String sort, int limit) {
        List<BowlingStat> rows = sorted(records(Dataset.BOWLING, normalizer::bowling), sort, BOWLING_SORTS, "wickets");
        return respond(limit(rows, limit), Dataset.BOWLING);
    }

    public ApiResponse<List<FieldingStat>> fielding(String sort, int limit) {
        List<FieldingStat> rows = sorted(records(Dataset.FIELDING, normalizer::fielding), sort, FIELDING_SORTS, "catches");
        return respond(limit(rows, limit), Dataset.FIELDING);
    }

    /** Results, newest first. Optional case-insensitive filters on competition and opponent text. */
    public ApiResponse<List<MatchResult>> results(String competition, String opponent, int limit) {
        List<MatchResult> rows = records(Dataset.RESULTS, normalizer::results).stream()
                .filter(r -> contains(r.competition(), competition))
                .filter(r -> opponent == null || contains(r.teamOne(), opponent) || contains(r.teamTwo(), opponent))
                .sorted(Comparator.comparing(MatchResult::date, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return respond(limit(rows, limit), Dataset.RESULTS);
    }

    /** Fixtures from today (Singapore time) onward, soonest first. includePast=true returns the whole schedule. */
    public ApiResponse<List<Fixture>> schedule(boolean includePast, int limit) {
        LocalDate today = LocalDate.now(clock);
        List<Fixture> rows = records(Dataset.SCHEDULE, normalizer::schedule).stream()
                .filter(f -> includePast || (f.date() != null && !f.date().isBefore(today)))
                .sorted(Comparator.comparing(Fixture::date, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return respond(limit(rows, limit), Dataset.SCHEDULE);
    }

    /** The table exactly as exported (headers + rows), for screens that show every source column. */
    public ApiResponse<RawTable> raw(Dataset d) {
        return respond(store.snapshot(d).map(DatasetSnapshot::raw).orElse(RawTable.of(List.of(), List.of())), d);
    }

    // ---------------------------------------------------------------- derived views

    public record SeasonRecord(int played, int won, int lost, int tied, int noResult, int abandoned, int undetermined,
            List<String> form) {
    }

    /** Win/loss record from the results export; "form" is the last five outcomes, newest first (W/L/T/NR/A/?). */
    public ApiResponse<SeasonRecord> record() {
        List<MatchResult> rows = records(Dataset.RESULTS, normalizer::results).stream()
                .sorted(Comparator.comparing(MatchResult::date, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        int won = 0, lost = 0, tied = 0, nr = 0, ab = 0, unknown = 0;
        List<String> form = new ArrayList<>();
        for (MatchResult r : rows) {
            String o = r.outcome() == null ? "?" : r.outcome();
            switch (o) {
                case "WON" -> won++;
                case "LOST" -> lost++;
                case "TIED" -> tied++;
                case "NO_RESULT" -> nr++;
                case "ABANDONED" -> ab++;
                default -> unknown++;
            }
            if (form.size() < 5) {
                form.add(switch (o) {
                    case "WON" -> "W";
                    case "LOST" -> "L";
                    case "TIED" -> "T";
                    case "NO_RESULT" -> "NR";
                    case "ABANDONED" -> "A";
                    default -> "?";
                });
            }
        }
        return respond(new SeasonRecord(rows.size(), won, lost, tied, nr, ab, unknown, form), Dataset.RESULTS);
    }

    /** Season leaderboards across batting, bowling and fielding. Qualification thresholds come from config. */
    public ApiResponse<List<LeaderBoard>> leaders(int top) {
        ScaProperties.Leaders q = props.leaders();
        List<BattingStat> bat = records(Dataset.BATTING, normalizer::batting);
        List<BowlingStat> bowl = records(Dataset.BOWLING, normalizer::bowling);
        List<FieldingStat> field = records(Dataset.FIELDING, normalizer::fielding);

        List<LeaderBoard> boards = new ArrayList<>();
        boards.add(board("mostRuns", "Most runs", null, bat.stream(), b -> dec(b.runs()), true, top,
                b -> b.playerId(), BattingStat::name, b -> join("avg " + str(b.average()), "SR " + str(b.strikeRate()))));
        boards.add(board("bestBattingAverage", "Best batting average", "min " + q.minInningsForAverage() + " innings",
                bat.stream().filter(b -> b.innings() != null && b.innings() >= q.minInningsForAverage()),
                BattingStat::average, true, top, BattingStat::playerId, BattingStat::name,
                b -> join(str(b.runs()) + " runs", str(b.innings()) + " inns")));
        boards.add(board("bestStrikeRate", "Best strike rate", "min " + q.minBallsForStrikeRate() + " balls",
                bat.stream().filter(b -> b.balls() != null && b.balls() >= q.minBallsForStrikeRate()),
                BattingStat::strikeRate, true, top, BattingStat::playerId, BattingStat::name,
                b -> str(b.runs()) + " off " + str(b.balls())));
        boards.add(board("mostWickets", "Most wickets", null, bowl.stream(), b -> dec(b.wickets()), true, top,
                BowlingStat::playerId, BowlingStat::name, b -> join("econ " + str(b.economy()), "best " + str(b.bestBowling()))));
        boards.add(board("bestEconomy", "Best economy", "min " + q.minBallsForEconomy() / 6 + " overs",
                bowl.stream().filter(b -> b.balls() != null && b.balls() >= q.minBallsForEconomy()),
                BowlingStat::economy, false, top, BowlingStat::playerId, BowlingStat::name,
                b -> join(str(b.overs()) + " ov", str(b.wickets()) + " wkts")));
        boards.add(board("mostCatches", "Most catches", null, field.stream(), f -> dec(catches(f)), true, top,
                FieldingStat::playerId, FieldingStat::name, f -> str(f.matches()) + " matches"));
        boards.add(board("mostDismissals", "Most dismissals", "catches + stumpings + run outs", field.stream(),
                f -> dec(dismissals(f)), true, top, FieldingStat::playerId, FieldingStat::name,
                f -> join("ct " + str(catches(f)), "st " + str(f.stumpings()), "ro " + str(f.runOuts()))));

        return respond(boards, Dataset.BATTING, Dataset.BOWLING, Dataset.FIELDING);
    }

    public record PlayerProfile(String playerId, String name, Player player, BattingStat batting,
            BowlingStat bowling, FieldingStat fielding) {
    }

    /** One player's combined record, looked up by SCA playerId or by name (case-insensitive). */
    public Optional<ApiResponse<PlayerProfile>> player(String idOrName) {
        String key = idOrName.trim().toLowerCase(Locale.ROOT);
        Player p = find(records(Dataset.PLAYERS, normalizer::players), Player::playerId, Player::name, key);
        BattingStat bat = find(records(Dataset.BATTING, normalizer::batting), BattingStat::playerId, BattingStat::name, key);
        BowlingStat bowl = find(records(Dataset.BOWLING, normalizer::bowling), BowlingStat::playerId, BowlingStat::name, key);
        FieldingStat field = find(records(Dataset.FIELDING, normalizer::fielding), FieldingStat::playerId, FieldingStat::name, key);
        if (p == null && bat == null && bowl == null && field == null) {
            return Optional.empty();
        }
        String id = Stream.of(p == null ? null : p.playerId(), bat == null ? null : bat.playerId(),
                bowl == null ? null : bowl.playerId(), field == null ? null : field.playerId())
                .filter(Objects::nonNull).findFirst().orElse(null);
        String name = Stream.of(p == null ? null : p.name(), bat == null ? null : bat.name(),
                bowl == null ? null : bowl.name(), field == null ? null : field.name())
                .filter(Objects::nonNull).findFirst().orElse(idOrName);
        return Optional.of(respond(new PlayerProfile(id, name, p, bat, bowl, field), Dataset.PLAYERS,
                Dataset.BATTING, Dataset.BOWLING, Dataset.FIELDING));
    }

    public ApiResponse<Map<Dataset, DatasetStatus>> status() {
        return respond(store.statuses(), Dataset.values());
    }

    // ---------------------------------------------------------------- helpers

    private static final Map<String, Comparator<BattingStat>> BATTING_SORTS = Map.of(
            "runs", desc(b -> dec(b.runs())),
            "average", desc(BattingStat::average),
            "strikerate", desc(BattingStat::strikeRate),
            "innings", desc(b -> dec(b.innings())),
            "sixes", desc(b -> dec(b.sixes())),
            "fours", desc(b -> dec(b.fours())),
            "name", Comparator.comparing(b -> b.name().toLowerCase(Locale.ROOT)));

    private static final Map<String, Comparator<BowlingStat>> BOWLING_SORTS = Map.of(
            "wickets", desc(b -> dec(b.wickets())),
            "economy", asc(BowlingStat::economy),
            "average", asc(BowlingStat::average),
            "strikerate", asc(BowlingStat::strikeRate),
            "overs", desc(b -> dec(b.balls())),
            "maidens", desc(b -> dec(b.maidens())),
            "name", Comparator.comparing(b -> b.name().toLowerCase(Locale.ROOT)));

    private static final Map<String, Comparator<FieldingStat>> FIELDING_SORTS = Map.of(
            "catches", desc(f -> dec(catches(f))),
            "dismissals", desc(f -> dec(dismissals(f))),
            "stumpings", desc(f -> dec(f.stumpings())),
            "runouts", desc(f -> dec(f.runOuts())),
            "name", Comparator.comparing(f -> f.name().toLowerCase(Locale.ROOT)));

    public static List<String> battingSorts() {
        return BATTING_SORTS.keySet().stream().sorted().toList();
    }

    public static List<String> bowlingSorts() {
        return BOWLING_SORTS.keySet().stream().sorted().toList();
    }

    public static List<String> fieldingSorts() {
        return FIELDING_SORTS.keySet().stream().sorted().toList();
    }

    private static <T> Comparator<T> desc(Function<T, BigDecimal> f) {
        return Comparator.comparing(f, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private static <T> Comparator<T> asc(Function<T, BigDecimal> f) {
        return Comparator.comparing(f, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static <T> List<T> sorted(List<T> rows, String sort, Map<String, Comparator<T>> sorts, String fallback) {
        String key = sort == null ? fallback : sort.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        Comparator<T> cmp = sorts.get(key);
        if (cmp == null) {
            throw new IllegalArgumentException("Unknown sort '" + sort + "'. Allowed: "
                    + sorts.keySet().stream().sorted().toList());
        }
        return rows.stream().sorted(cmp).toList();
    }

    @SuppressWarnings("unchecked")
    private synchronized <T> List<T> records(Dataset d, Function<RawTable, List<T>> parse) {
        Optional<DatasetSnapshot> snap = store.snapshot(d);
        if (snap.isEmpty()) {
            return List.of();
        }
        Cached c = cache.get(d);
        if (c == null || !c.sha().equals(snap.get().contentSha256())) {
            c = new Cached(snap.get().contentSha256(), parse.apply(snap.get().raw()));
            cache.put(d, c);
        }
        return (List<T>) c.records();
    }

    private <T> ApiResponse<T> respond(T data, Dataset... datasets) {
        Instant now = clock.instant();
        List<ApiResponse.Source> sources = Arrays.stream(datasets).map(d -> {
            DatasetStatus st = store.status(d);
            Optional<DatasetSnapshot> snap = store.snapshot(d);
            Instant synced = st == null ? null : st.lastSuccessAt();
            boolean stale = synced == null || synced.plus(props.staleAfter()).isBefore(now);
            return new ApiResponse.Source(d, snap.map(DatasetSnapshot::sourceUrl).orElse(sync.pageUri(d).toString()),
                    snap.map(DatasetSnapshot::method).orElse(null), synced, stale,
                    st == null ? null : st.state(), st == null ? null : st.message());
        }).toList();
        return new ApiResponse<>(data, new ApiResponse.Meta(sources));
    }

    private static <T> LeaderBoard board(String key, String label, String qualification, Stream<T> rows,
            Function<T, BigDecimal> value, boolean highestFirst, int top, Function<T, String> id,
            Function<T, String> name, Function<T, String> detail) {
        Comparator<T> cmp = Comparator.comparing(value, highestFirst ? Comparator.reverseOrder() : Comparator.naturalOrder());
        List<Leader> leaders = rows.filter(r -> value.apply(r) != null)
                .sorted(cmp)
                .limit(Math.max(1, top))
                .map(r -> new Leader(id.apply(r), name.apply(r), value.apply(r), detail.apply(r)))
                .toList();
        return new LeaderBoard(key, label, qualification, leaders);
    }

    private static <T> T find(List<T> rows, Function<T, String> id, Function<T, String> name, String key) {
        for (T r : rows) {
            if (key.equals(id.apply(r))) {
                return r;
            }
        }
        for (T r : rows) {
            String n = name.apply(r);
            if (n != null && n.trim().toLowerCase(Locale.ROOT).equals(key)) {
                return r;
            }
        }
        return null;
    }

    /** Outfield + keeper catches when both are known; otherwise whichever the source gave. */
    static Integer catches(FieldingStat f) {
        if (f.catches() == null) {
            return f.wicketKeeperCatches();
        }
        return f.wicketKeeperCatches() == null ? f.catches() : f.catches() + f.wicketKeeperCatches();
    }

    /** Source total when given; otherwise the sum of the known components (null if none are known). */
    static Integer dismissals(FieldingStat f) {
        if (f.totalDismissals() != null) {
            return f.totalDismissals();
        }
        Integer c = catches(f);
        if (c == null && f.stumpings() == null && f.runOuts() == null) {
            return null;
        }
        return (c == null ? 0 : c) + (f.stumpings() == null ? 0 : f.stumpings()) + (f.runOuts() == null ? 0 : f.runOuts());
    }

    private static <T> List<T> limit(List<T> rows, int limit) {
        return limit > 0 && rows.size() > limit ? rows.subList(0, limit) : rows;
    }

    private static boolean contains(String haystack, String needle) {
        return needle == null || needle.isBlank()
                || (haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT)));
    }

    private static BigDecimal dec(Integer i) {
        return i == null ? null : BigDecimal.valueOf(i);
    }

    private static String str(Object o) {
        if (o instanceof BigDecimal b) {
            return b.setScale(Math.min(2, Math.max(0, b.scale())), RoundingMode.HALF_UP).toPlainString();
        }
        return o == null ? "—" : o.toString();
    }

    private static String join(String... parts) {
        return String.join(" · ", parts);
    }
}
