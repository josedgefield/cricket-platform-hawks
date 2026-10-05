package sg.hawkscc.platform.stats.importing;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import sg.hawkscc.platform.stats.domain.Source;

/**
 * Reads a player-link list: which source names are the same person.
 * Columns (by header): {@code Player} (the name the app shows), {@code Source}
 * ({@code sca} or {@code cricheroes}) and {@code Source name} (as that source prints it).
 */
public final class PlayerLinksCsv {

    public record Link(int line, String player, Source source, String sourceName) {
    }

    public record Result(List<Link> links, List<String> errors) {
    }

    private PlayerLinksCsv() {
    }

    public static Result parse(String text) {
        List<List<String>> grid = CsvReader.read(text);
        if (grid.isEmpty()) {
            return new Result(List.of(), List.of("The list is empty."));
        }
        List<String> head = grid.getFirst().stream().map(h -> h.strip().toLowerCase(Locale.ROOT)).toList();
        int player = head.indexOf("player");
        int source = head.indexOf("source");
        int sourceName = head.indexOf("source name");
        if (player < 0 || source < 0 || sourceName < 0) {
            return new Result(List.of(), List.of("Need the columns Player, Source and Source name."));
        }
        List<Link> links = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (int r = 1; r < grid.size(); r++) {
            List<String> c = grid.get(r);
            int line = r + 1;
            String p = cell(c, player);
            String s = cell(c, source);
            String n = cell(c, sourceName);
            if (p.isEmpty() || s.isEmpty() || n.isEmpty()) {
                errors.add("Line " + line + ": Player, Source and Source name are all required");
                continue;
            }
            try {
                links.add(new Link(line, p, Source.fromCode(s), n));
            } catch (IllegalArgumentException e) {
                errors.add("Line " + line + ": " + e.getMessage());
            }
        }
        return new Result(List.copyOf(links), List.copyOf(errors));
    }

    private static String cell(List<String> cells, int i) {
        return i < cells.size() ? cells.get(i).strip() : "";
    }
}
