package sg.hawkscc.platform.identity.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import sg.hawkscc.platform.identity.AuthService;
import sg.hawkscc.platform.identity.CurrentMember;
import sg.hawkscc.platform.identity.MemberService;
import sg.hawkscc.platform.identity.MemberView;

/** The signed-in member's own account. */
@RestController
@RequestMapping("/api/me")
class MeController {

    private final MemberService members;
    private final AuthService auth;

    MeController(MemberService members, AuthService auth) {
        this.members = members;
        this.auth = auth;
    }

    record ProfileChanges(@Size(min = 1, max = 80) String displayName, @Size(max = 30) String phone) {
    }

    record PasswordChange(@NotBlank @Size(max = 200) String currentPassword,
                          @NotBlank @Size(max = 200) String newPassword) {
    }

    @GetMapping
    MemberView me(@AuthenticationPrincipal CurrentMember me) {
        return members.me(me);
    }

    @PatchMapping
    MemberView update(@AuthenticationPrincipal CurrentMember me, @Valid @RequestBody ProfileChanges body) {
        return members.updateMe(me, new MemberService.Changes(null, body.displayName(), body.phone()));
    }

    /** Signs out your other devices; this one stays signed in. */
    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@AuthenticationPrincipal CurrentMember me, @Valid @RequestBody PasswordChange body) {
        auth.changePassword(me, body.currentPassword(), body.newPassword());
    }
}
