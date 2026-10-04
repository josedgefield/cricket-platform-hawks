package sg.hawkscc.platform.stats.importing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import sg.hawkscc.platform.stats.domain.NameKey;
import sg.hawkscc.platform.stats.domain.Overs;
import sg.hawkscc.platform.stats.domain.StatKind;

/**
 * Parses a leaderboard tab (CricHeroes, SCA or similar) pasted or exported as CSV/TSV.
 * Columns are matched by header name. Values that aren't printed stay absent (unknown);
 * published rates are kept as "reported" and cross-checked against our own calculation.
 * A table copied from the SCA website (headers marked with sort arrows, each player
 * spread over several lines) is joined back into one row per player first.
 */
public final class LeaderboardCsvParser {

    private static final String PLAYER = "player";
    private static final String OVERS = "overs";
    private static final String SORT_ARROW = "↓";

    private static final Map<StatKind, Map<Object, List<String>>> HEADERS = Map.of(
            StatKind.BATTING, headers(
                    PLAYER, List.of("player", "name", "player name", "batter"),
                    StatColumn.MATCHES, List.of("mat", "m", "matches"),
                    StatColumn.BAT_INNS, List.of("inns", "innings", "inn", "ins"),
                    StatColumn.BAT_NOT_OUTS, List.of("no", "not out", "not outs"),
                    StatColumn.BAT_RUNS, List.of("runs", "r"),
                    StatColumn.BAT_BALLS, List.of("balls", "b", "bf", "balls faced"),
                    StatColumn.BAT_HIGH_SCORE, List.of("hs", "highest", "highest score", "best"),
                    StatColumn.BAT_FOURS, List.of("4s", "4's", "fours"),
                    StatColumn.BAT_SIXES, List.of("6s", "6's", "sixes"),
                    StatColumn.REPORTED_BAT_AVG, List.of("avg", "average"),
                    StatColumn.REPORTED_BAT_SR, List.of("sr", "strike rate")),
            StatKind.BOWLING, headers(
                    PLAYER, List.of("player", "name", "player name", "bowler"),
                    StatColumn.MATCHES, List.of("mat", "matches"),
                    StatColumn.BOWL_INNS, List.of("inns", "innings", "inn"),
                    OVERS, List.of("overs", "o", "ov"),
                    StatColumn.BOWL_BALLS, List.of("balls", "b"),
                    StatColumn.BOWL_MAIDENS, List.of("maidens", "mdns", "md"),
                    StatColumn.BOWL_RUNS, List.of("runs", "r", "runs conceded"),
                    StatColumn.BOWL_WICKETS, List.of("wkts", "w", "wickets"),
                    StatColumn.REPORTED_ECON, List.of("econ", "economy", "eco"),
                    StatColumn.REPORTED_BOWL_AVG, List.of("avg", "ave", "average")),
            StatKind.FIELDING, headers(
                    PLAYER, List.of("player", "name", "player name", "fielder"),
                    StatColumn.MATCHES, List.of("mat", "m", "matches"),
                    StatColumn.FIELD_CATCHES, List.of("ct", "catches", "c"),
                    StatColumn.FIELD_STUMPINGS, List.of("st", "stumpings"),
                    StatColumn.FIELD_RUN_OUTS, List.of("ro", "r/o", "run outs", "run out", "runouts"),
                    StatColumn.FIELD_DISMISSALS, List.of("dismissal", "dismissals", "dis", "total")));

    /**
     * Columns a source splits into parts (SCA: catches vs WK catches, direct vs indirect
     * run outs). When every part's header is present, the stored value is their sum, and
     * unknown if any part is blank.
     */
    private static final Map<StatKind, Map<StatColumn, List<String>>> SUMS = Map.of(
            StatKind.FIELDING, Map.of(
                    StatColumn.FIELD_CATCHES, List.of("catches", "wk catches"),
                    StatColumn.FIELD_RUN_OUTS, List.of("direct ro", "indirect ro")));

    public record Row(int line, String sourceName, Map<StatColumn, Object> values, List<String> warnings,
                      boolean duplicate) {
    }

    public record Result(StatKind kind, List<Row> rows, List<String> errors) {
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    private LeaderboardCsvParser() {
    }

    public static Result parse(String text, StatKind kind) {
        Map<Object, List<String>> spec = HEADERS.get(kind);
        if (spec == null) {
            return new Result(kind, List.of(), List.of("A leaderboard can't be of kind " + kind.code()));
        }
        List<List<String>> grid = CsvReader.read(text);
        if (grid.size() < 2) {
            return new Result(kind, List.of(), List.of("Need a header row and at least one player row."));
        }
        if (grid.getFirst().stream().anyMatch(h -> h.contains(SORT_ARROW))) {
            grid = joinWrappedRows(grid);
        }
        List<String> head = grid.getFirst().stream().map(LeaderboardCsvParser::headerKey).toList();
        Map<Object, Integer> col = new LinkedHashMap<>();
        spec.forEach((key, aliases) -> {
            for (int i = 0; i < head.size(); i++) {
                if (aliases.contains(head.get(i))) {
                    col.put(key, i);
                    break;
                }
            }
        });
        if (!col.containsKey(PLAYER)) {
            return new Result(kind, List.of(),
                    List.of("No \"Player\" column found. Headers seen: " + String.join(", ", grid.getFirst())));
        }

        List<Row> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, Integer> firstLineByName = new HashMap<>();
        for (int r = 1; r < grid.size(); r++) {
            List<String> cells = grid.get(r);
            int line = r + 1;
            List<String> warnings = new ArrayList<>();
            String name = playerName(cell(cells, col.get(PLAYER)));
            if (name.isEmpty()) {
                errors.add("Line " + line + ": no player name");
                continue;
            }
            Map<StatColumn, Object> values = new EnumMap<>(StatColumn.class);
            for (Map.Entry<Object, Integer> e : col.entrySet()) {
                if (e.getKey() instanceof StatColumn c) {
                    Object v = c.isDecimal()
                            ? decimalOrNull(cell(cells, e.getValue()))
                            : intOrNull(cell(cells, e.getValue()), c, warnings);
                    values.put(c, v);
                }
            }
            SUMS.getOrDefault(kind, Map.of()).forEach((c, parts) -> {
                if (parts.stream().allMatch(head::contains)) {
                    Integer total = 0;
                    for (String part : parts) {
                        Integer v = intOrNull(cell(cells, head.indexOf(part)), c, warnings);
                        total = total == null || v == null ? null : total + v;
                    }
                    values.put(c, total);
                }
            });
            if (kind == StatKind.BOWLING && values.get(StatColumn.BOWL_BALLS) == null && col.containsKey(OVERS)) {
                try {
                    values.put(StatColumn.BOWL_BALLS, Overs.toBalls(cell(cells, col.get(OVERS))));
                } catch (IllegalArgumentException ex) {
                    warnings.add(ex.getMessage() + "; balls left blank");
                }
            }
            dropRatesOverZero(values);
            crossCheck(kind, values, warnings);

            String key = NameKey.of(name);
            Integer first = firstLineByName.putIfAbsent(key, line);
            boolean duplicate = first != null;
            if (duplicate) {
                warnings.add("Duplicate of line " + first + "; this line is ignored");
            }
            rows.add(new Row(line, name, values, List.copyOf(warnings), duplicate));
        }
        return new Result(kind, List.copyOf(rows), List.copyOf(errors));
    }

    /**
     * A rate over zero (no dismissals, balls or wickets) doesn't exist. SCA still prints
     * a placeholder such as "0" or "0.00" there, which must not be kept as a real figure.
     */
    private static void dropRatesOverZero(Map<StatColumn, Object> v) {
        Object inns = v.get(StatColumn.BAT_INNS);
        Object notOuts = v.get(StatColumn.BAT_NOT_OUTS);
        if (inns instanceof Integer i && notOuts instanceof Integer no && i - no == 0) {
            v.replace(StatColumn.REPORTED_BAT_AVG, null);
        }
        if (Integer.valueOf(0).equals(v.get(StatColumn.BAT_BALLS))) {
            v.replace(StatColumn.REPORTED_BAT_SR, null);
        }
        if (Integer.valueOf(0).equals(v.get(StatColumn.BOWL_BALLS))) {
            v.replace(StatColumn.REPORTED_ECON, null);
        }
        if (Integer.valueOf(0).equals(v.get(StatColumn.BOWL_WICKETS))) {
            v.replace(StatColumn.REPORTED_BOWL_AVG, null);
        }
    }

    private static void crossCheck(StatKind kind, Map<StatColumn, Object> v, List<String> warnings) {
        if (kind == StatKind.BATTING) {
            BigDecimal ours = ratio(v.get(StatColumn.BAT_RUNS), v.get(StatColumn.BAT_BALLS), 100);
            compare("SR", (BigDecimal) v.get(StatColumn.REPORTED_BAT_SR), ours, new BigDecimal("0.6"), warnings);
        } else if (kind == StatKind.BOWLING) {
            BigDecimal ours = ratio(v.get(StatColumn.BOWL_RUNS), v.get(StatColumn.BOWL_BALLS), 6);
            compare("economy", (BigDecimal) v.get(StatColumn.REPORTED_ECON), ours, new BigDecimal("0.06"), warnings);
        } else if (kind == StatKind.FIELDING
                && v.get(StatColumn.FIELD_DISMISSALS) instanceof Integer total
                && v.get(StatColumn.FIELD_CATCHES) instanceof Integer ct
                && v.get(StatColumn.FIELD_STUMPINGS) instanceof Integer st
                && v.get(StatColumn.FIELD_RUN_OUTS) instanceof Integer ro
                && total != ct + st + ro) {
            warnings.add("Source total " + total + " differs from catches + stumpings + run outs (" + (ct + st + ro)
                    + ")");
        }
    }

    private static void compare(String label, BigDecimal reported, BigDecimal ours, BigDecimal tolerance,
                                List<String> warnings) {
        if (reported != null && ours != null && reported.subtract(ours).abs().compareTo(tolerance) > 0) {
            warnings.add("Source " + label + " " + reported.toPlainString() + " differs from calculated "
                    + ours.toPlainString());
        }
    }

    private static BigDecimal ratio(Object numerator, Object denominator, int multiplier) {
        if (!(numerator instanceof Integer n) || !(denominator instanceof Integer d) || d == 0) {
            return null;
        }
        return BigDecimal.valueOf((long) n * multiplier).divide(BigDecimal.valueOf(d), 2, RoundingMode.HALF_UP);
    }

    /**
     * A copied SCA table prints each player as "rank, name" / "team" / "figures" on
     * separate lines. Joins the pieces until a row is as wide as the header; the blank
     * cells left at each line break are dropped.
     */
    private static List<List<String>> joinWrappedRows(List<List<String>> grid) {
        List<String> header = grid.getFirst();
        int width = header.size();
        while (width > 0 && header.get(width - 1).isBlank()) {
            width--;
        }
        List<List<String>> out = new ArrayList<>();
        out.add(header);
        List<String> pending = new ArrayList<>();
        for (List<String> piece : grid.subList(1, grid.size())) {
            List<String> cells = new ArrayList<>(piece);
            if (!pending.isEmpty()) {
                while (!pending.isEmpty() && pending.getLast().isBlank()) {
                    pending.removeLast();
                }
                while (!cells.isEmpty() && cells.getFirst().isBlank()) {
                    cells.removeFirst();
                }
            }
            pending.addAll(cells);
            if (pending.size() >= width) {
                out.add(pending);
                pending = new ArrayList<>();
            }
        }
        if (!pending.isEmpty()) {
            out.add(pending);
        }
        return out;
    }

    /** "Player  ↓" → "player". */
    private static String headerKey(String header) {
        return header.replace(SORT_ARROW, "").strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** Drops a trailing role note such as "(hawks Club Admin)"; it isn't part of the name. */
    private static String playerName(String raw) {
        return raw.strip().replaceFirst("\\s*\\([^()]*\\)$", "").strip();
    }

    private static boolean isBlank(String s) {
        return s.isEmpty() || s.equals("-") || s.equals("--") || s.equals("—");
    }

    private static Integer intOrNull(String raw, StatColumn column, List<String> warnings) {
        String s = raw.strip();
        if (isBlank(s)) {
            return null;
        }
        if (column == StatColumn.BAT_HIGH_SCORE && s.endsWith("*")) {
            s = s.substring(0, s.length() - 1);
        }
        if (!s.matches("\\d{1,6}")) {
            warnings.add("\"" + raw + "\" is not a whole number for " + column.dbName() + "; left blank");
            return null;
        }
        return Integer.parseInt(s);
    }

    private static BigDecimal decimalOrNull(String raw) {
        String s = raw.strip();
        if (isBlank(s)) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String cell(List<String> cells, Integer index) {
        return index == null || index >= cells.size() ? "" : cells.get(index);
    }

    private static Map<Object, List<String>> headers(Object... pairs) {
        Map<Object, List<String>> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            @SuppressWarnings("unchecked")
            List<String> aliases = (List<String>) pairs[i + 1];
            m.put(pairs[i], aliases);
        }
        return m;
    }
}
