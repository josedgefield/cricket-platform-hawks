package sg.hawkscc.platform.stats.importing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses a competition points table. Text columns (NRR, For, Against, Last 5) are
 * stored exactly as printed; we never recompute them.
 */
public final class StandingsCsvParser {

    public record Row(int position, String team, Integer matches, Integer won, Integer lost, Integer drawn,
                      Integer tied, Integer noResult, Integer points, String netRunRate, String runsFor,
                      String runsAgainst, String lastFive) {
    }

    public record Result(List<Row> rows, List<String> errors) {
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    private static final Map<String, List<String>> HEADERS = new LinkedHashMap<>();

    static {
        HEADERS.put("position", List.of("#", "pos", "position", "rank"));
        HEADERS.put("team", List.of("team", "name"));
        HEADERS.put("matches", List.of("m", "mat", "matches", "p", "played"));
        HEADERS.put("won", List.of("w", "won"));
        HEADERS.put("lost", List.of("l", "lost"));
        HEADERS.put("drawn", List.of("d", "drawn"));
        HEADERS.put("tied", List.of("t", "tied"));
        HEADERS.put("noResult", List.of("nr", "no result"));
        HEADERS.put("nrr", List.of("nrr", "net run rate"));
        HEADERS.put("for", List.of("for"));
        HEADERS.put("against", List.of("against"));
        HEADERS.put("points", List.of("pts", "pt.", "pt", "points"));
        HEADERS.put("lastFive", List.of("last 5", "last5", "form"));
    }

    private StandingsCsvParser() {
    }

    public static Result parse(String text) {
        List<List<String>> grid = CsvReader.read(text);
        if (grid.size() < 2) {
            return new Result(List.of(), List.of("Need a header row and at least one team row."));
        }
        List<String> head = grid.getFirst().stream().map(h -> h.strip().toLowerCase(Locale.ROOT)).toList();
        Map<String, Integer> col = new LinkedHashMap<>();
        HEADERS.forEach((key, aliases) -> {
            for (int i = 0; i < head.size(); i++) {
                if (aliases.contains(head.get(i))) {
                    col.put(key, i);
                    break;
                }
            }
        });
        if (!col.containsKey("team")) {
            return new Result(List.of(), List.of("No \"Team\" column found."));
        }
        List<Row> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (int r = 1; r < grid.size(); r++) {
            List<String> c = grid.get(r);
            int line = r + 1;
            String team = text(c, col.get("team"));
            if (team == null) {
                errors.add("Line " + line + ": no team name");
                continue;
            }
            Integer position = integer(c, col.get("position"), errors, line);
            rows.add(new Row(position == null ? r : position, team,
                    integer(c, col.get("matches"), errors, line), integer(c, col.get("won"), errors, line),
                    integer(c, col.get("lost"), errors, line), integer(c, col.get("drawn"), errors, line),
                    integer(c, col.get("tied"), errors, line), integer(c, col.get("noResult"), errors, line),
                    integer(c, col.get("points"), errors, line), text(c, col.get("nrr")),
                    text(c, col.get("for")), text(c, col.get("against")), text(c, col.get("lastFive"))));
        }
        long distinct = rows.stream().map(Row::position).distinct().count();
        if (distinct != rows.size()) {
            errors.add("Positions must be unique");
        }
        return new Result(List.copyOf(rows), List.copyOf(errors));
    }

    private static String text(List<String> cells, Integer index) {
        if (index == null || index >= cells.size()) {
            return null;
        }
        String s = cells.get(index).strip();
        return s.isEmpty() || s.equals("-") || s.equals("—") ? null : s;
    }

    private static Integer integer(List<String> cells, Integer index, List<String> errors, int line) {
        String s = text(cells, index);
        if (s == null) {
            return null;
        }
        if (!s.matches("\\d{1,6}")) {
            errors.add("Line " + line + ": \"" + s + "\" is not a whole number");
            return null;
        }
        return Integer.parseInt(s);
    }
}
