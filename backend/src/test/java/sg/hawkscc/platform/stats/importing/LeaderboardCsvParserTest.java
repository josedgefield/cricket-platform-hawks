package sg.hawkscc.platform.stats.importing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import sg.hawkscc.platform.stats.domain.StatKind;

class LeaderboardCsvParserTest {

    @Test
    void parsesCricHeroesBattingColumnsAndKeepsRatesAsReported() {
        var result = LeaderboardCsvParser.parse("""
                Player,Inn,Runs,Avg,SR
                Sandeep Chandrasekharan Nair Roja,4,149,49.67,131.86
                """, StatKind.BATTING);

        assertThat(result.errors()).isEmpty();
        var row = result.rows().getFirst();
        assertThat(row.sourceName()).isEqualTo("Sandeep Chandrasekharan Nair Roja");
        assertThat(row.values())
                .containsEntry(StatColumn.BAT_INNS, 4)
                .containsEntry(StatColumn.BAT_RUNS, 149)
                .containsEntry(StatColumn.REPORTED_BAT_SR, new BigDecimal("131.86"))
                // Not printed → not present → stays unknown.
                .doesNotContainKey(StatColumn.BAT_BALLS)
                .doesNotContainKey(StatColumn.BAT_NOT_OUTS);
    }

    @Test
    void handlesQuotesTabsHighScoreAsteriskAndBlanks() {
        var result = LeaderboardCsvParser.parse(
                "Player\tInns\tNO\tRuns\tHS\tBalls\n\"Shelat, Hardik\"\t3\t\t64\t40*\t33\n", StatKind.BATTING);
        var v = result.rows().getFirst().values();
        assertThat(result.rows().getFirst().sourceName()).isEqualTo("Shelat, Hardik");
        assertThat(v).containsEntry(StatColumn.BAT_HIGH_SCORE, 40).containsEntry(StatColumn.BAT_NOT_OUTS, null);
    }

    @Test
    void warnsWhenThePublishedRateDisagreesWithTheCounts() {
        var result = LeaderboardCsvParser.parse("Player,Runs,Balls,SR\nVishal,56,77,99.00\n", StatKind.BATTING);
        assertThat(result.rows().getFirst().warnings()).singleElement().asString().contains("Source SR 99.00");
    }

    @Test
    void bowlingConvertsOversAndFlagsImpossibleOvers() {
        var result = LeaderboardCsvParser.parse("""
                Player,Inn,Overs,Maidens,Runs,W,Eco
                Puttur Shreyas,5,21.1,1,77,13,3.64
                Alpin Mehta,1,4.7,0,18,2,4.50
                """, StatKind.BOWLING);
        assertThat(result.rows().get(0).values()).containsEntry(StatColumn.BOWL_BALLS, 127);
        assertThat(result.rows().get(0).warnings()).isEmpty();
        assertThat(result.rows().get(1).values().get(StatColumn.BOWL_BALLS)).isNull();
        assertThat(result.rows().get(1).warnings()).singleElement().asString().contains("not valid");
    }

    @Test
    void fieldingMapsDismissalAndRunOuts() {
        var result = LeaderboardCsvParser.parse("Player,Mat,Dismissal,Catches,R/O\nRahul Singh,1,1,1,0\n",
                StatKind.FIELDING);
        assertThat(result.rows().getFirst().values())
                .containsEntry(StatColumn.MATCHES, 1)
                .containsEntry(StatColumn.FIELD_DISMISSALS, 1)
                .containsEntry(StatColumn.FIELD_RUN_OUTS, 0)
                .doesNotContainKey(StatColumn.FIELD_STUMPINGS);
    }

    @Test
    void marksDuplicatesByNormalisedName() {
        var result = LeaderboardCsvParser.parse("Player,Runs\nHardik Shelat,64\nhardik  shelat,1\n",
                StatKind.BATTING);
        assertThat(result.rows().get(0).duplicate()).isFalse();
        assertThat(result.rows().get(1).duplicate()).isTrue();
    }

    @Test
    void missingPlayerColumnIsAClearError() {
        var result = LeaderboardCsvParser.parse("Runs,Balls\n1,2\n", StatKind.BATTING);
        assertThat(result.errors()).singleElement().asString().contains("No \"Player\" column");
    }

    @Test
    void nonNumericCountsAreBlankWithAWarning() {
        var result = LeaderboardCsvParser.parse("Player,Runs\nVishal,lots\n", StatKind.BATTING);
        assertThat(result.rows().getFirst().values()).containsEntry(StatColumn.BAT_RUNS, null);
        assertThat(result.rows().getFirst().warnings()).hasSize(1);
    }
}
