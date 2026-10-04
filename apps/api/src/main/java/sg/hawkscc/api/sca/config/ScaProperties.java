package sg.hawkscc.api.sca.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for the SCA (scores.cricketsingapore.com) ingestion. Bound from the {@code sca.*} keys in
 * application.yml.
 */
@ConfigurationProperties(prefix = "sca")
public record ScaProperties(
        @DefaultValue("true") boolean enabled,
        String baseUrl,
        long teamId,
        long clubId,
        /** Case-insensitive word identifying our team in result text, e.g. "Hawks won by 22 runs". */
        @DefaultValue("hawks") String teamNameKeyword,
        @DefaultValue("0 17 */6 * * *") String syncCron,
        @DefaultValue("Asia/Singapore") String syncZone,
        @DefaultValue("true") boolean syncOnStartup,
        String userAgent,
        @DefaultValue("3s") Duration minDelayBetweenRequests,
        @DefaultValue("10s") Duration connectTimeout,
        @DefaultValue("20s") Duration requestTimeout,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("2s") Duration initialBackoff,
        @DefaultValue("true") boolean respectRobotsTxt,
        @DefaultValue("true") boolean allowHtmlTableFallback,
        @DefaultValue("MDY") DateOrder dateOrder,
        @DefaultValue("7d") Duration staleAfter,
        @DefaultValue("./data/sca") String storageDir,
        String adminKey,
        @DefaultValue("") List<String> corsAllowedOrigins,
        @DefaultValue Leaders leaders) {

    public enum DateOrder { MDY, DMY }

    public record Leaders(
            @DefaultValue("60") int minBallsForStrikeRate,
            @DefaultValue("3") int minInningsForAverage,
            @DefaultValue("60") int minBallsForEconomy) {
    }

    public boolean adminEnabled() {
        return adminKey != null && !adminKey.isBlank();
    }
}
