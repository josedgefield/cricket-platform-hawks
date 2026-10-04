package sg.hawkscc.platform.stats.importing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StandingsCsvParserTest {

    @Test
    void keepsTextColumnsExactlyAsPrinted() {
        var result = StandingsCsvParser.parse("""
                #,Team,M,W,L,D,T,NR,NRR,For,Against,Pt.,Last 5
                1,HAWKS CC,8,5,0,0,0,3,2.529,912/123.3,607/125,27,W-W-W-W-W
                5,GLORIOUS CRICKET CLUB,7,4,3,0,0,0,-0.506,934/146.1,1024/148.3,16,W-W-W-L-W
                """);
        assertThat(result.errors()).isEmpty();
        var hawks = result.rows().getFirst();
        assertThat(hawks.position()).isEqualTo(1);
        assertThat(hawks.points()).isEqualTo(27);
        assertThat(hawks.netRunRate()).isEqualTo("2.529");
        assertThat(hawks.runsFor()).isEqualTo("912/123.3");
        assertThat(result.rows().get(1).netRunRate()).isEqualTo("-0.506");
    }

    @Test
    void rejectsDuplicatePositionsAndBadNumbers() {
        var result = StandingsCsvParser.parse("#,Team,Pts\n1,A,x\n1,B,3\n");
        assertThat(result.errors()).anySatisfy(e -> assertThat(e).contains("not a whole number"));
        assertThat(result.errors()).anySatisfy(e -> assertThat(e).contains("unique"));
    }
}
