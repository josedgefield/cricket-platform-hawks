package sg.hawkscc.platform.comms.web;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import sg.hawkscc.platform.comms.NoticeService;
import sg.hawkscc.platform.comms.NoticeViews;
import sg.hawkscc.platform.identity.CurrentMember;

/** Writing notices: admins and superusers (ROLE_ADMIN). */
@RestController
@RequestMapping("/api/admin/notices")
class NoticeAdminController {

    private final NoticeService notices;

    NoticeAdminController(NoticeService notices) {
        this.notices = notices;
    }

    record NewNotice(@NotBlank @Size(max = 120) String title, @NotBlank @Size(max = 5000) String body, Boolean pinned,
                     Boolean emailMembers, @NotBlank @Size(max = 80) String requestKey) {
    }

    record Edit(@Size(min = 1, max = 120) String title, @Size(min = 1, max = 5000) String body, Boolean pinned) {
    }

    /**
     * Publishes a notice to every member. With {@code emailMembers}, active members also get it
     * by email; the notice's {@code email} field then says how that went.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    NoticeViews.Notice publish(@AuthenticationPrincipal CurrentMember me, @Valid @RequestBody NewNotice body) {
        return notices.publish(me, new NoticeService.NewNotice(body.title(), body.body(),
                Boolean.TRUE.equals(body.pinned()), Boolean.TRUE.equals(body.emailMembers()), body.requestKey()));
    }

    @PatchMapping("/{id}")
    NoticeViews.Notice edit(@AuthenticationPrincipal CurrentMember me, @PathVariable UUID id,
                            @Valid @RequestBody Edit body) {
        return notices.edit(me, id, new NoticeService.Changes(body.title(), body.body(), body.pinned()));
    }

    /** Withdraws the notice: members stop seeing it, and it is kept for the record. */
    @PostMapping("/{id}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void withdraw(@AuthenticationPrincipal CurrentMember me, @PathVariable UUID id) {
        notices.archive(me, id);
    }
}
