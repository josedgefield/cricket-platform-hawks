package sg.hawkscc.platform.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import sg.hawkscc.platform.identity.AuthService;

/**
 * Turns {@code Authorization: Bearer <token>} into the signed-in member. A missing or invalid
 * token leaves the request anonymous; the authorisation rules then decide (401 where needed).
 * Not a Spring bean on purpose, so it runs only inside the security chain.
 */
class BearerTokenFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final AuthService auth;

    BearerTokenFilter(AuthService auth) {
        this.auth = auth;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            auth.authenticate(header.substring(PREFIX.length()).strip()).ifPresent(member -> {
                var authentication = new UsernamePasswordAuthenticationToken(member, null,
                        AuthorityUtils.createAuthorityList(member.role().authorities()));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }
        chain.doFilter(request, response);
    }
}
