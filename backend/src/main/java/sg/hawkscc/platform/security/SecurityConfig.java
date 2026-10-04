package sg.hawkscc.platform.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Default deny. Public: stats reads, health, and API docs (docs are switched off outside dev).
 * Admin endpoints need a role. Until the identity module (email one-time codes + MFA)
 * exists, only the dev/test profiles have a user; in production nobody can sign in,
 * which keeps admin endpoints closed rather than open.
 */
@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        http
                // Stateless JSON API: no session cookie, so no CSRF exposure.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/stats/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/admin/stats/**").hasRole("STATS_ADMIN")
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    /**
     * Lets web pages on other origins (e.g. the prototype on localhost:8000) read public stats.
     * No origins are allowed unless hawks.cors.allowed-origins lists them.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${hawks.cors.allowed-origins:}") List<String> allowedOrigins) {
        CorsConfiguration stats = new CorsConfiguration();
        stats.setAllowedOrigins(allowedOrigins.stream().filter(o -> !o.isBlank()).toList());
        stats.setAllowedMethods(List.of("GET"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/stats/**", stats);
        return source;
    }

    @Bean
    @Profile({"dev", "test"})
    UserDetailsService devUsers(@Value("${hawks.dev.admin-username}") String username,
                                @Value("${hawks.dev.admin-password}") String password) {
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password("{noop}" + password)
                .roles("STATS_ADMIN")
                .build());
    }

    @Bean
    @Profile("!dev & !test")
    UserDetailsService noUsersYet() {
        return new InMemoryUserDetailsManager();
    }
}
