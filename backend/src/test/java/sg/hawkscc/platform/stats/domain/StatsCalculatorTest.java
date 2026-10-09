package sg.hawkscc.platform.stats.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class StatsCalculatorTest {

    private static StatLine batting(Integer inns, Integer notOuts, Integer runs, Integer balls, Integer hs,
                                    StatLine.Reported reported) {
        return new StatLine(null, new StatLine.Batting(inns, notOuts, runs, balls, hs, null, null),
                StatLine.Bowling.UNKNOWN, StatLine.Fielding.UNKNOWN, reported);
    }

    @Test
    void calculatesRatesFromCounts() {
        StatLine s = batting(4, 1, 149, 113, 60, StatLine.Reported.NONE);
        assertThat(StatsCalculator.battingAverage(s)).isEqualTo(new Rate(new BigDecimal("49.67"), false));
        assertThat(StatsCalculator.strikeRate(s)).isEqualTo(new Rate(new BigDecimal("131.86"), false));
    }

    @Test
    void zeroDivisorsAreUnknownNotInfinity() {
        StatLine s = batting(2, 2, 50, 0, 30, StatLine.Reported.NONE);
        assertThat(StatsCalculator.battingAverage(s)).isEqualTo(Rate.UNKNOWN);
        assertThat(StatsCalculator.strikeRate(s)).isEqualTo(Rate.UNKNOWN);
    }

    @Test
    void fallsBackToThePublishedRateAndSaysSo() {
        var reported = new StatLine.Reported(new BigDecimal("64.00"), new BigDecimal("193.94"), null, null);
        StatLine s = batting(3, null, 64, null, null, reported);
        assertThat(StatsCalculator.strikeRate(s)).isEqualTo(new Rate(new BigDecimal("193.94"), true));
        assertThat(StatsCalculator.battingAverage(s)).isEqualTo(new Rate(new BigDecimal("64.00"), true));
    }

    @Test
    void combinedTotalIsUnknownWhenAnySourceLacksTheValue() {
        StatLine sca = batting(3, 1, 90, 60, 45, StatLine.Reported.NONE);
        StatLine cricheroes = batting(4, null, 100, null, null,
                new StatLine.Reported(null, new BigDecimal("120.00"), null, null));
        StatLine combined = StatsCalculator.combine(List.of(sca, cricheroes));

        assertThat(combined.batting().runs()).isEqualTo(190);
        assertThat(combined.batting().inns()).isEqualTo(7);
        assertThat(combined.batting().balls()).isNull();
        assertThat(combined.batting().highScore()).isNull();
        // One source's published SR is not the combined SR.
        assertThat(StatsCalculator.strikeRate(combined)).isEqualTo(Rate.UNKNOWN);
    }

    @Test
    void aSourceThatDoesNotListThePlayerInACategoryIsLeftOutOfThatTotal() {
        // SCA lists his batting and bowling; the BPL leaderboard only lists his bowling.
        var sca = new StatLine(11, new StatLine.Batting(5, 2, 70, 67, 31, 6, 3),
                new StatLine.Bowling(11, 305, 5, 296, 11), StatLine.Fielding.UNKNOWN, StatLine.Reported.NONE);
        var bpl = new StatLine(null, StatLine.Batting.UNKNOWN, new StatLine.Bowling(5, 127, null, 77, 13),
                StatLine.Fielding.UNKNOWN,
                new StatLine.Reported(null, null, new BigDecimal("3.64"), new BigDecimal("5.92")));
        StatLine c = StatsCalculator.combine(List.of(sca, bpl));

        assertThat(c.batting().runs()).isEqualTo(70);
        assertThat(StatsCalculator.strikeRate(c)).isEqualTo(new Rate(new BigDecimal("104.48"), false));
        assertThat(c.bowling().wickets()).isEqualTo(24);
        assertThat(c.bowling().balls()).isEqualTo(432);
        assertThat(c.bowling().maidens()).isNull(); // listed by both, printed by one: unknown
        assertThat(StatsCalculator.economy(c)).isEqualTo(new Rate(new BigDecimal("5.18"), false));
        assertThat(c.matches()).isNull();
    }

    @Test
    void aSingleContributingSourceKeepsItsPublishedRate() {
        var sca = new StatLine(null, StatLine.Batting.UNKNOWN, new StatLine.Bowling(1, 6, 0, 10, 0),
                StatLine.Fielding.UNKNOWN, StatLine.Reported.NONE);
        var bpl = new StatLine(null, new StatLine.Batting(3, null, 64, null, null, null, null),
                StatLine.Bowling.UNKNOWN, StatLine.Fielding.UNKNOWN,
                new StatLine.Reported(new BigDecimal("64.00"), new BigDecimal("193.94"), null, null));
        StatLine c = StatsCalculator.combine(List.of(sca, bpl));
        assertThat(StatsCalculator.strikeRate(c)).isEqualTo(new Rate(new BigDecimal("193.94"), true));
    }

    @Test
    void combinesHighScoreAsMaximum() {
        StatLine a = batting(1, 0, 40, 30, 40, StatLine.Reported.NONE);
        StatLine b = batting(1, 0, 71, 58, 71, StatLine.Reported.NONE);
        assertThat(StatsCalculator.combine(List.of(a, b)).batting().highScore()).isEqualTo(71);
    }

    @Test
    void fieldingDismissalsPreferThePublishedTotal() {
        var published = new StatLine(null, StatLine.Batting.UNKNOWN, StatLine.Bowling.UNKNOWN,
                new StatLine.Fielding(0, null, 0, 7), StatLine.Reported.NONE);
        var counted = new StatLine(null, StatLine.Batting.UNKNOWN, StatLine.Bowling.UNKNOWN,
                new StatLine.Fielding(3, 2, 1, null), StatLine.Reported.NONE);
        var unknown = new StatLine(null, StatLine.Batting.UNKNOWN, StatLine.Bowling.UNKNOWN,
                new StatLine.Fielding(3, null, 1, null), StatLine.Reported.NONE);
        assertThat(StatsCalculator.fieldingDismissals(published)).isEqualTo(7);
        assertThat(StatsCalculator.fieldingDismissals(counted)).isEqualTo(5);
        assertThat(StatsCalculator.fieldingDismissals(unknown)).isNull();
    }

    @Test
    void bowlingRates() {
        var s = new StatLine(null, StatLine.Batting.UNKNOWN, new StatLine.Bowling(5, 127, 1, 77, 13),
                StatLine.Fielding.UNKNOWN, StatLine.Reported.NONE);
        assertThat(StatsCalculator.economy(s).value()).isEqualByComparingTo("3.64");
        assertThat(StatsCalculator.bowlingAverage(s).value()).isEqualByComparingTo("5.92");
        assertThat(StatsCalculator.bowlingStrikeRate(s).value()).isEqualByComparingTo("9.77");
    }
}
