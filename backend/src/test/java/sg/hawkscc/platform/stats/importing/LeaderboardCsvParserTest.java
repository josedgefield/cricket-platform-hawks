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

    // A table copied from the SCA website: sort arrows in the header, and each player
    // printed as "rank<TAB>name<TAB>" / "<TAB>team" / figures, with CRLF line endings.
    private static String scaCopy(String header, String... players) {
        StringBuilder s = new StringBuilder(header).append("\r\n");
        for (String p : players) {
            String[] parts = p.split("\\|");
            s.append(parts[0]).append("\t ").append(parts[1]).append("\t\r\n\t HAWKS CC\r\n").append(parts[2])
                    .append("\r\n");
        }
        return s.toString();
    }

    @Test
    void parsesScaBattingCopiedFromTheWebsite() {
        var result = LeaderboardCsvParser.parse(scaCopy(
                "# ↓\tPlayer  ↓\tTeam  ↓\tMat  ↓\tIns  ↓\tNo  ↓\tRuns  ↓\tBalls  ↓\tAvg  ↓\tSr  ↓\tHs  ↓\t100's  ↓"
                        + "\t75's  ↓\t50's  ↓\t25's  ↓\t0  ↓\t6's  ↓\t4's  ↓\t",
                "1|Alok Patra|9\t9\t0\t360\t316\t40.00\t113.92\t85\t0\t1\t3\t3\t0\t8\t47",
                "20|Kamal Raj|1\t1\t0\t0\t0\t--\t0.00\t0\t0\t0\t0\t0\t1\t0\t0"), StatKind.BATTING);

        assertThat(result.errors()).isEmpty();
        assertThat(result.rows()).hasSize(2);
        var alok = result.rows().getFirst();
        assertThat(alok.sourceName()).isEqualTo("Alok Patra");
        assertThat(alok.warnings()).isEmpty();
        assertThat(alok.values())
                .containsEntry(StatColumn.MATCHES, 9)
                .containsEntry(StatColumn.BAT_INNS, 9)
                .containsEntry(StatColumn.BAT_NOT_OUTS, 0)
                .containsEntry(StatColumn.BAT_RUNS, 360)
                .containsEntry(StatColumn.BAT_BALLS, 316)
                .containsEntry(StatColumn.BAT_HIGH_SCORE, 85)
                .containsEntry(StatColumn.BAT_SIXES, 8)
                .containsEntry(StatColumn.BAT_FOURS, 47)
                .containsEntry(StatColumn.REPORTED_BAT_AVG, new BigDecimal("40.00"));
        // SCA prints "--" for an average it can't calculate: unknown, not an error.
        assertThat(result.rows().get(1).values()).containsEntry(StatColumn.REPORTED_BAT_AVG, null);
        assertThat(result.rows().get(1).warnings()).isEmpty();
    }

    @Test
    void parsesScaBowlingAndDropsTheRoleNoteFromTheName() {
        var result = LeaderboardCsvParser.parse(scaCopy(
                "# ↓\tPlayer  ↓\tTeam  ↓\tMat ↓\tInns ↓\tOvers ↓\tRuns ↓\tWkts ↓\tBBf ↓\tMdns ↓\tDots ↓\tEcon ↓"
                        + "\tAve ↓\tSR ↓\tHat-trick ↓\t4W ↓\t5W ↓\tWides ↓\tNb ↓",
                "1|Alpin Mehta (hawks Club Admin)|9\t9\t37.0\t206\t14\t 35/ 5\t3\t138\t5.57\t14.71\t15.9\t0\t0\t1\t15\t0"),
                StatKind.BOWLING);

        var row = result.rows().getFirst();
        assertThat(row.sourceName()).isEqualTo("Alpin Mehta");
        assertThat(row.warnings()).isEmpty();
        assertThat(row.values())
                .containsEntry(StatColumn.BOWL_INNS, 9)
                .containsEntry(StatColumn.BOWL_BALLS, 222)
                .containsEntry(StatColumn.BOWL_RUNS, 206)
                .containsEntry(StatColumn.BOWL_WICKETS, 14)
                .containsEntry(StatColumn.BOWL_MAIDENS, 3)
                .containsEntry(StatColumn.REPORTED_ECON, new BigDecimal("5.57"))
                .containsEntry(StatColumn.REPORTED_BOWL_AVG, new BigDecimal("14.71"));
    }

    @Test
    void placeholderRatesOverZeroAreUnknown() {
        // SCA prints Ave "0" with no wickets and SR "0.00" with no balls faced.
        var bowling = LeaderboardCsvParser.parse(
                "Player,Overs,Runs,Wkts,Econ,Ave\nAditya Chandrasekhar,2.0,10,0,5.00,0\n", StatKind.BOWLING);
        assertThat(bowling.rows().getFirst().values())
                .containsEntry(StatColumn.REPORTED_BOWL_AVG, null)
                .containsEntry(StatColumn.REPORTED_ECON, new BigDecimal("5.00"));

        var batting = LeaderboardCsvParser.parse(
                "Player,Ins,No,Runs,Balls,Avg,Sr\nKamal Raj,1,1,0,0,0.00,0.00\n", StatKind.BATTING);
        assertThat(batting.rows().getFirst().values())
                .containsEntry(StatColumn.REPORTED_BAT_AVG, null)
                .containsEntry(StatColumn.REPORTED_BAT_SR, null);
    }

    @Test
    void scaFieldingAddsWicketkeeperCatchesAndBothKindsOfRunOut() {
        var result = LeaderboardCsvParser.parse(scaCopy(
                "# ↓\tPlayer  ↓\tTeam  ↓\tCatches  ↓\tWK Catches  ↓\tDirect RO  ↓\tIndirect RO  ↓\tStumpings  ↓"
                        + "\tTotal  ↓\t",
                "2|Alok Patra|0\t4\t2\t1\t1\t8",
                "3|Wrong Total|1\t0\t0\t0\t0\t5"), StatKind.FIELDING);

        var alok = result.rows().getFirst();
        assertThat(alok.warnings()).isEmpty();
        assertThat(alok.values())
                .containsEntry(StatColumn.FIELD_CATCHES, 4)
                .containsEntry(StatColumn.FIELD_RUN_OUTS, 3)
                .containsEntry(StatColumn.FIELD_STUMPINGS, 1)
                .containsEntry(StatColumn.FIELD_DISMISSALS, 8);
        assertThat(result.rows().get(1).warnings()).singleElement().asString().contains("Source total 5");
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
