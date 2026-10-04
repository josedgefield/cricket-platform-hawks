package sg.hawkscc.api.sca;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import sg.hawkscc.api.sca.model.ScaRecords.Fixture;
import sg.hawkscc.api.sca.model.ScaRecords.MatchResult;
import sg.hawkscc.api.sca.model.ScaRecords.Player;
import sg.hawkscc.api.sca.normalize.ScaNormalizer;

/**
 * Pins the parser to the real CSV exports from the SCA site (SCA Clubs Division 3 - 2025, HAWKS CC), as
 * downloaded with each page's CSV button and supplied by the club.
 */
class RealScaExportTest {

    private final ScaNormalizer normalizer = new ScaNormalizer(TestProps.defaults("./build/unused"));

    @Test
    void schedule() throws IOException {
        List<Fixture> rows = normalizer.schedule(ScaNormalizerTest.fixture("sca-2025-div3/schedule.csv"));
        assertThat(rows).hasSize(14);
        Fixture first = rows.get(0);
        assertThat(first.date()).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(first.time()).isEqualTo("12:45 PM");
        assertThat(first.teamOne()).isEqualTo("HAWKS CC");
        assertThat(first.teamTwo()).isEqualTo("CHAMPION CC - FRIENDS SQUAD");
        assertThat(first.venue()).isEqualTo("Dempsey");
        assertThat(first.competition()).isEqualTo("SCA Clubs Division 3 - 2025");
        assertThat(first.extra()).containsEntry("Series", "SCA Club League - 2025").containsEntry("Match Type", "League");
        assertThat(rows.get(1).date()).isEqualTo(LocalDate.of(2025, 11, 22));
    }

    @Test
    void resultsSplitTheScoreSummary() throws IOException {
        List<MatchResult> rows = normalizer.results(ScaNormalizerTest.fixture("sca-2025-div3/results.csv"));
        assertThat(rows).hasSize(12);

        MatchResult first = rows.get(0);
        assertThat(first.date()).isEqualTo(LocalDate.of(2026, 8, 30));
        assertThat(first.teamOne()).isEqualTo("HAWKS CC");
        assertThat(first.teamOneScore()).isEqualTo("282/4 (28.0)");
        assertThat(first.teamTwo()).isEqualTo("WARRIORS CC 2");
        assertThat(first.teamTwoScore()).isEqualTo("152/10 (22.2)");
        assertThat(first.result()).isEqualTo("HAWKS CC won by 130 Runs");
        assertThat(first.competition()).isEqualTo("League");

        MatchResult bengal = rows.get(7);
        assertThat(bengal.teamOne()).isEqualTo("BENGAL CC MERLION");
        assertThat(bengal.teamTwoScore()).isEqualTo("186/4 (26.5)");

        MatchResult abandoned = rows.get(3);
        assertThat(abandoned.outcome()).isEqualTo("ABANDONED");
        assertThat(abandoned.teamOneScore()).isEqualTo("0/0 (0.0)");
        assertThat(abandoned.teamTwoScore()).isNull();

        assertThat(rows).extracting(MatchResult::outcome)
                .containsExactly("WON", "LOST", "WON", "ABANDONED", "WON", "WON", "WON", "WON", "LOST", "WON", "LOST", "LOST");
    }

    @Test
    void squadReadsPlayerIdColumn() throws IOException {
        List<Player> rows = normalizer.players(ScaNormalizerTest.fixture("sca-2025-div3/players.csv"));
        assertThat(rows).hasSize(26);
        assertThat(rows.get(0).playerId()).isEqualTo("1014329");
        assertThat(rows.get(0).name()).isEqualTo("Niraj Parmar");
        assertThat(rows.get(13).extra()).containsEntry("Jersey Number", "9");
    }
}
