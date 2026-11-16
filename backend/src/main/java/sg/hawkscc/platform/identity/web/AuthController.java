package sg.hawkscc.platform.identity.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import sg.hawkscc.platform.identity.AuthService;
import sg.hawkscc.platform.identity.CurrentMember;

/**
 * Signing in and the links sent by email. There is no sign-up endpoint: members only join
 * through an invite. Send the returned token as {@code Authorization: Bearer <token>}.
 */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthService auth;

    AuthController(AuthService auth) {
        this.auth = auth;
    }

    record SignIn(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 200) String password) {
    }

    record NewPassword(@NotBlank @Size(max = 200) String password) {
    }

    record ResetRequest(@NotBlank @Size(max = 254) String email) {
    }

    @PostMapping("/sign-in")
    AuthService.SignedIn signIn(@Valid @RequestBody SignIn body, HttpServletRequest request) {
        return auth.signIn(body.email(), body.password(), request.getRemoteAddr(), request.getHeader(HttpHeaders.USER_AGENT));
    }

    @PostMapping("/sign-out")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void signOut(@AuthenticationPrincipal CurrentMember me) {
        auth.signOut(me);
    }

    /** Who the invite is for. 404 for an unknown link, 410 if expired or already used. */
    @GetMapping("/invitations/{token}")
    AuthService.Invitation invitation(@PathVariable String token) {
        return auth.invitation(token);
    }

    @PostMapping("/invitations/{token}/accept")
    AuthService.SignedIn accept(@PathVariable String token, @Valid @RequestBody NewPassword body,
                                HttpServletRequest request) {
        return auth.acceptInvite(token, body.password(), request.getHeader(HttpHeaders.USER_AGENT));
    }

    /** Always 202, whether or not the email belongs to a member. */
    @PostMapping("/password-reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void requestReset(@Valid @RequestBody ResetRequest body, HttpServletRequest request) {
        auth.requestPasswordReset(body.email(), request.getRemoteAddr());
    }

    @PostMapping("/password-reset/{token}")
    AuthService.SignedIn reset(@PathVariable String token, @Valid @RequestBody NewPassword body,
                               HttpServletRequest request) {
        return auth.resetPassword(token, body.password(), request.getHeader(HttpHeaders.USER_AGENT));
    }
}
