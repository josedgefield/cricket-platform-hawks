package sg.hawkscc.platform;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/** Signs in through the real API and returns the bearer token, for integration tests. */
public final class SignIn {

    private SignIn() {
    }

    public record Response(String token) {
    }

    public static String token(RestClient http, String email, String password) {
        Response r = http.post().uri("/api/auth/sign-in")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", password))
                .retrieve().body(Response.class);
        if (r == null || r.token() == null) {
            throw new IllegalStateException("Sign-in returned no token for " + email);
        }
        return r.token();
    }
}
