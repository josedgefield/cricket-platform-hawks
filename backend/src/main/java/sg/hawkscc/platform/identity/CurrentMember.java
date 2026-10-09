package sg.hawkscc.platform.identity;

import java.security.Principal;
import java.util.UUID;

/**
 * The signed-in member for one request. {@link #getName()} is the member id, so code that
 * records "who did this" via {@code Principal#getName()} stores a stable id.
 */
public record CurrentMember(UUID memberId, UUID clubId, UUID sessionId, Role role, String email,
                            String displayName) implements Principal {

    @Override
    public String getName() {
        return memberId.toString();
    }
}
