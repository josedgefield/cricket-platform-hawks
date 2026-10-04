package sg.hawkscc.api.sca.model;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** The six Hawks team pages on the SCA site that carry a CSV export button. */
public enum Dataset {
    PLAYERS("viewTeam.do", List.of("player", "name")),
    RESULTS("teamResults.do", List.of("result", "won", "match")),
    SCHEDULE("teamSchedule.do", List.of("date", "ground", "venue", "time")),
    BATTING("teamBatting.do", List.of("runs", "player")),
    BOWLING("teamBowling.do", List.of("wickets", "overs", "player")),
    FIELDING("teamFielding.do", List.of("catches", "player"));

    private final String page;
    /** Header words that identify this dataset's table when a page holds several tables. */
    private final List<String> tableHints;

    Dataset(String page, List<String> tableHints) {
        this.page = page;
        this.tableHints = tableHints;
    }

    public String page() {
        return page;
    }

    public List<String> tableHints() {
        return tableHints;
    }

    public String slug() {
        return name().toLowerCase();
    }

    public static Optional<Dataset> fromSlug(String slug) {
        return Arrays.stream(values()).filter(d -> d.slug().equalsIgnoreCase(slug)).findFirst();
    }
}
