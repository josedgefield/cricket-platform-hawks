package sg.hawkscc.platform.identity;

import java.time.Instant;
import java.util.UUID;

/**
 * A member as the API shows them. {@code manageable} says whether the person asking may change
 * this member (admins manage players; superusers manage everyone but themselves' role/status).
 * {@code inviteEmailError} is set when the last invite email couldn't be sent.
 */
public record MemberView(UUID id, String email, String displayName, String phone, String role, String status,
                         UUID playerId, Instant invitedAt, Instant inviteSentAt, String inviteEmailError,
                         Instant lastSignInAt, boolean manageable) {

    static MemberView of(IdentityRepository.MemberRow m, boolean manageable) {
        return new MemberView(m.id(), m.email(), m.displayName(), m.phone(), m.role().code(), m.status().code(),
                m.playerId(), m.createdAt(), m.inviteSentAt(), m.lastEmailError(), m.lastSignInAt(), manageable);
    }
}
