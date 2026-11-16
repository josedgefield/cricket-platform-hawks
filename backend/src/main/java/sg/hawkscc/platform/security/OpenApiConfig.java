package sg.hawkscc.platform.security;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

import org.springframework.context.annotation.Configuration;

/**
 * Lets Swagger UI (dev only) send the session token: sign in with POST /api/auth/sign-in,
 * click Authorize and paste the token.
 */
@Configuration
@OpenAPIDefinition(info = @Info(title = "Hawks CC API"), security = @SecurityRequirement(name = "session"))
@SecurityScheme(name = "session", type = SecuritySchemeType.HTTP, scheme = "bearer",
        description = "The token from POST /api/auth/sign-in")
class OpenApiConfig {
}
