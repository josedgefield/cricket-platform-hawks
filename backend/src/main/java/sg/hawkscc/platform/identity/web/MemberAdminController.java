package sg.hawkscc.platform.identity.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import sg.hawkscc.platform.identity.CurrentMember;
import sg.hawkscc.platform.identity.MemberService;
import sg.hawkscc.platform.identity.MemberStatus;
import sg.hawkscc.platform.identity.MemberView;
import sg.hawkscc.platform.identity.Role;

/**
 * Member management for admins and superusers (ROLE_ADMIN). Which members an admin may change
 * is decided in {@link MemberService}; each view says whether the caller may manage it.
 */
@RestController
@RequestMapping("/api/admin/members")
class MemberAdminController {

    private final MemberService members;

    MemberAdminController(MemberService members) {
        this.members = members;
    }

    record Invite(@NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 80) String displayName,
                  @Size(max = 30) String phone, String role) {
    }

    record Edit(@Email @Size(max = 254) String email, @Size(min = 1, max = 80) String displayName,
                @Size(max = 30) String phone) {
    }

    record RoleChange(@NotBlank String role) {
    }

    /** @param status invited, active or deactivated; all if omitted. @param q name or email contains */
    @GetMapping
    List<MemberView> list(@AuthenticationPrincipal CurrentMember me, @RequestParam(required = false) String status,
                          @RequestParam(required = false) String q) {
        MemberStatus s = status == null || status.isBlank() ? null : parseStatus(status);
        return members.list(me, s, q);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    MemberView invite(@AuthenticationPrincipal CurrentMember me, @Valid @RequestBody Invite body) {
        Role role = body.role() == null || body.role().isBlank() ? Role.PLAYER : Role.fromCode(body.role());
        return members.invite(me, new MemberService.NewMember(body.email(), body.displayName(), body.phone(), role));
    }

    @GetMapping("/{id}")
    MemberView get(@AuthenticationPrincipal CurrentMember me, @PathVariable UUID id) {
        return members.get(me, id);
    }

    @PatchMapping("/{id}")
    MemberView update(@AuthenticationPrincipal CurrentMember me, @PathVariable UUID id, @Valid @RequestBody Edit body) {
        return members.update(me, id, new MemberService.Changes(body.email(), body.displayName(), body.phone()));
    }

    /** "Delete": the member can't sign in any more, but their history stays. Reversible. */
    @PostMapping("/{id}/deactivate")
    MemberView deactivate(@AuthenticationPrincipal CurrentMember me, @PathVariable UUID id) {
        return members.deactivate(me, id);
    }

    @PostMapping("/{id}/reactivate")
    MemberView reactivate(@AuthenticationPrincipal CurrentMember me, @PathVariable UUID id) {
        return members.reactivate(me, id);
    }

    @PostMapping("/{id}/resend-invite")
    MemberView resendInvite(@AuthenticationPrincipal CurrentMember me, @PathVariable UUID id) {
        return members.resendInvite(me, id);
    }

    /** Superusers only. */
    @PutMapping("/{id}/role")
    MemberView changeRole(@AuthenticationPrincipal CurrentMember me, @PathVariable UUID id,
                          @Valid @RequestBody RoleChange body) {
        return members.changeRole(me, id, Role.fromCode(body.role()));
    }

    private static MemberStatus parseStatus(String status) {
        try {
            return MemberStatus.fromCode(status.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("status must be invited, active or deactivated");
        }
    }
}
