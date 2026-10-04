package sg.hawkscc.api.sca;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import sg.hawkscc.api.sca.client.RobotsPolicy;

class RobotsPolicyTest {

    private static final String UA = "HawksCC-StatsSync/0.1";

    @Test
    void emptyDisallowAllowsEverything() {
        RobotsPolicy p = RobotsPolicy.parse("User-agent: *\nDisallow:\n", UA);
        assertThat(p.isAllowed("/SingaporeCricketAssoc/teamBatting.do?teamId=1")).isTrue();
    }

    @Test
    void disallowedPrefixIsBlocked() {
        RobotsPolicy p = RobotsPolicy.parse("User-agent: *\nDisallow: /SingaporeCricketAssoc/admin\n", UA);
        assertThat(p.isAllowed("/SingaporeCricketAssoc/admin/x")).isFalse();
        assertThat(p.isAllowed("/SingaporeCricketAssoc/teamBatting.do")).isTrue();
    }

    @Test
    void longestMatchWinsAndAllowBeatsDisallowOnTie() {
        RobotsPolicy p = RobotsPolicy.parse("""
                User-agent: *
                Disallow: /
                Allow: /SingaporeCricketAssoc/team
                """, UA);
        assertThat(p.isAllowed("/SingaporeCricketAssoc/teamResults.do")).isTrue();
        assertThat(p.isAllowed("/other")).isFalse();
    }

    @Test
    void specificAgentGroupOverridesStar() {
        RobotsPolicy p = RobotsPolicy.parse("""
                User-agent: *
                Disallow: /

                User-agent: hawkscc-statssync
                Disallow: /private
                """, UA);
        assertThat(p.isAllowed("/SingaporeCricketAssoc/teamBatting.do")).isTrue();
        assertThat(p.isAllowed("/private/x")).isFalse();
    }

    @Test
    void wildcardRulesMatchTheirLiteralPrefix() {
        RobotsPolicy p = RobotsPolicy.parse("User-agent: *\nDisallow: /*.csv$\nDisallow: /export*\n", UA);
        assertThat(p.isAllowed("/exportCsv.do")).isFalse();
    }
}
