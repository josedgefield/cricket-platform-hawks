package sg.hawkscc.api.sca;

import java.time.Duration;
import java.util.List;

import sg.hawkscc.api.sca.config.ScaProperties;

final class TestProps {

    private TestProps() {
    }

    static ScaProperties defaults(String storageDir) {
        return withBase("https://scores.example.test/SingaporeCricketAssoc", storageDir);
    }

    static ScaProperties withBase(String baseUrl, String storageDir) {
        return new ScaProperties(true, baseUrl, 2291, 7683, "hawks", "0 17 */6 * * *", "Asia/Singapore", false,
                "HawksCC-StatsSync/test", Duration.ZERO, Duration.ofSeconds(5), Duration.ofSeconds(5), 2,
                Duration.ofMillis(1), true, true, ScaProperties.DateOrder.MDY, Duration.ofDays(7), storageDir,
                "test-admin-key", List.of(), new ScaProperties.Leaders(60, 3, 60));
    }
}
