package sg.hawkscc.api.sca;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import sg.hawkscc.api.sca.extract.CsvTableReader;
import sg.hawkscc.api.sca.model.RawTable;
import sg.hawkscc.api.sca.model.ScaRecords.BattingStat;
import sg.hawkscc.api.sca.model.ScaRecords.BowlingStat;
import sg.hawkscc.api.sca.model.ScaRecords.FieldingStat;
import sg.hawkscc.api.sca.model.ScaRecords.Fixture;
import sg.hawkscc.api.sca.model.ScaRecords.MatchResult;
import sg.hawkscc.api.sca.normalize.ScaNormalizer;

class ScaNormalizerTest {

    private final ScaNormalizer normalizer = new ScaNormalizer(TestProps.defaults("./build/unused"));

    static RawTable fixture(String name) throws IOException {
        try (InputStream in = ScaNormalizerTest.class.getResourceAsStream("/fixtures/" + name)) {
            return CsvTableReader.read(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void battingMapsHeadersSkipsTotalsAndKeepsUnknownsNull() throws IOException {
        List<BattingStat> rows = normalizer.batting(fixture("synthetic-batting.csv"));
        assertThat(rows).hasSize(4);
        BattingStat alpha = rows.get(0);
        assertThat(alpha.name()).isEqualTo("Player Alpha");
        assertThat(alpha.runs()).isEqualTo(312);
        assertThat(alpha.notOuts()).isEqualTo(1);
        assertThat(alpha.highestScore()).isEqualTo("88*");
        assertThat(alpha.strikeRate()).isEqualByComparingTo(new BigDecimal("130.00"));
        assertThat(alpha.extra()).containsEntry("#", "1");
        BattingStat delta = rows.get(3);
        assertThat(delta.balls()).isNull();
        assertThat(delta.strikeRate()).isNull();
    }

    @Test
    void bowlingConvertsOversAndTreatsDashesAsUnknown() throws IOException {
        List<BowlingStat> rows = normalizer.bowling(fixture("synthetic-bowling.csv"));
        BowlingStat echo = rows.get(0);
        assertThat(echo.overs()).isEqualTo("28.4");
        assertThat(echo.balls()).isEqualTo(172);
        assertThat(echo.maidens()).isEqualTo(2);
        assertThat(echo.wickets()).isEqualTo(14);
        assertThat(echo.bestBowling()).isEqualTo("4/21");
        BowlingStat golf = rows.get(3);
        assertThat(golf.wickets()).isNull();
        assertThat(golf.balls()).isNull();
    }

    @Test
    void fieldingSeparatesKeeperCatches() throws IOException {
        List<FieldingStat> rows = normalizer.fielding(fixture("synthetic-fielding.csv"));
        FieldingStat hotel = rows.get(0);
        assertThat(hotel.catches()).isEqualTo(1);
        assertThat(hotel.wicketKeeperCatches()).isEqualTo(9);
        assertThat(hotel.stumpings()).isEqualTo(3);
        assertThat(hotel.totalDismissals()).isEqualTo(14);
        assertThat(rows.get(1).totalDismissals()).isNull();
    }

    @Test
    void resultsHandleRepeatedTeamAndScoreColumnsAndOutcome() throws IOException {
        List<MatchResult> rows = normalizer.results(fixture("synthetic-results.csv"));
        MatchResult first = rows.get(0);
        assertThat(first.date()).isEqualTo(LocalDate.of(2026, 9, 27));
        assertThat(first.teamOne()).isEqualTo("Hawks");
        assertThat(first.teamOneScore()).isEqualTo("184/6 (40)");
        assertThat(first.teamTwo()).isEqualTo("Kallang Kings XI");
        assertThat(first.teamTwoScore()).isEqualTo("162 (37.2)");
        assertThat(first.competition()).isEqualTo("Division 2");
        assertThat(first.venue()).isEqualTo("Ground A");
        assertThat(rows).extracting(MatchResult::outcome).containsExactly("WON", "WON", "LOST", "ABANDONED");
        assertThat(rows.get(3).teamOneScore()).isNull();
    }

    @Test
    void scheduleKeepsUnparseableDatesAsText() throws IOException {
        List<Fixture> rows = normalizer.schedule(fixture("synthetic-schedule.csv"));
        assertThat(rows).hasSize(4);
        assertThat(rows.get(0).date()).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(rows.get(0).time()).isEqualTo("09:30 AM");
        assertThat(rows.get(0).teamTwo()).isEqualTo("Marina Mariners XI");
        assertThat(rows.get(3).date()).isNull();
        assertThat(rows.get(3).dateText()).isEqualTo("TBC");
    }

    @Test
    void matchColumnIsSplitOnVersus() {
        RawTable t = RawTable.of(List.of("Date", "Match", "Venue"),
                List.of(List.of("10/11/2026", "Hawks vs Marina Mariners XI", "Ground A")));
        Fixture f = normalizer.schedule(t).get(0);
        assertThat(f.teamOne()).isEqualTo("Hawks");
        assertThat(f.teamTwo()).isEqualTo("Marina Mariners XI");
    }
}
