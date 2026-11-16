package sg.hawkscc.platform.comms.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import sg.hawkscc.platform.comms.NoticeService;
import sg.hawkscc.platform.comms.NoticeViews;
import sg.hawkscc.platform.identity.CurrentMember;

/** Club notices for every signed-in member: read, react, reply. */
@RestController
@RequestMapping("/api/notices")
class NoticeController {

    private final NoticeService notices;

    NoticeController(NoticeService notices) {
        this.notices = notices;
    }

    record Reaction(String reaction) {
    }

    record NewReply(@NotBlank @Size(max = 1000) String body) {
    }

    record Hide(@Size(max = 300) String reason) {
    }

    /** Pinned notices first, then newest. */
    @GetMapping
    List<NoticeViews.Notice> list(@AuthenticationPrincipal CurrentMember me) {
        return notices.list(me);
    }

    /** The notice and its replies. Opening it marks it as read for the caller. */
    @GetMapping("/{id}")
    NoticeViews.NoticeWithReplies open(@AuthenticationPrincipal CurrentMember me, @PathVariable UUID id) {
        return notices.open(me, id);
    }

    /** like, done, cricket, clap or question; null removes the caller's reaction. */
    @PutMapping("/{id}/reaction")
    NoticeViews.NoticeWithReplies react(@AuthenticationPrincipal CurrentMember me, @PathVariable UUID id,
                                        @RequestBody Reaction body) {
        return notices.react(me, id, body.reaction());
    }

    @PostMapping("/{id}/replies")
    @ResponseStatus(HttpStatus.CREATED)
    NoticeViews.NoticeWithReplies reply(@AuthenticationPrincipal CurrentMember me, @PathVariable UUID id,
                                        @Valid @RequestBody NewReply body) {
        return notices.reply(me, id, body.body());
    }

    /** Your own reply (no reason needed), or anyone's if you are an admin (reason required). */
    @PostMapping("/{id}/replies/{replyId}/hide")
    NoticeViews.NoticeWithReplies hide(@AuthenticationPrincipal CurrentMember me, @PathVariable UUID id,
                                       @PathVariable UUID replyId, @Valid @RequestBody(required = false) Hide body) {
        return notices.hideReply(me, id, replyId, body == null ? null : body.reason());
    }
}
