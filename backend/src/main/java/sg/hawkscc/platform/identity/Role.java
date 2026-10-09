package sg.hawkscc.platform.identity;

import java.util.List;
import java.util.Locale;

/**
 * The three roles (docs/03). Each includes the ones below it: a superuser can do everything an
 * admin can, and an admin everything a player can.
 */
public enum Role {
    PLAYER, ADMIN, SUPERUSER;

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Role fromCode(String code) {
        for (Role r : values()) {
            if (r.code().equalsIgnoreCase(code == null ? "" : code.strip())) {
                return r;
            }
        }
        throw new IdentityException(org.springframework.http.HttpStatus.BAD_REQUEST,
                "Role must be player, admin or superuser.");
    }

    public boolean atLeast(Role other) {
        return ordinal() >= other.ordinal();
    }

    /** Spring Security authorities, including every lower role. */
    public List<String> authorities() {
        return switch (this) {
            case PLAYER -> List.of("ROLE_PLAYER");
            case ADMIN -> List.of("ROLE_PLAYER", "ROLE_ADMIN");
            case SUPERUSER -> List.of("ROLE_PLAYER", "ROLE_ADMIN", "ROLE_SUPERUSER");
        };
    }
}
