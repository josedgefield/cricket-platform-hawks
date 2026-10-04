package sg.hawkscc.api.sca.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ScaConfig {

    @Bean
    Clock clock(ScaProperties props) {
        return Clock.system(ZoneId.of(props.syncZone()));
    }

    /** Read-only GET access for the web/app front ends listed in sca.cors-allowed-origins. */
    @Bean
    WebMvcConfigurer scaCors(ScaProperties props) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                String[] origins = props.corsAllowedOrigins().stream().filter(o -> !o.isBlank()).toArray(String[]::new);
                if (origins.length > 0) {
                    registry.addMapping("/api/sca/**").allowedOrigins(origins).allowedMethods("GET");
                }
            }
        };
    }
}
