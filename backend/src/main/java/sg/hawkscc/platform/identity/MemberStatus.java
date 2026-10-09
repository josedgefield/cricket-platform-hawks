package sg.hawkscc.platform.identity;

import java.util.Locale;

/** Invited until they set a password; deactivated instead of deleted, so history stays linked. */
public enum MemberStatus {
    INVITED, ACTIVE, DEACTIVATED;

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MemberStatus fromCode(String code) {
        return valueOf(code.toUpperCase(Locale.ROOT));
    }
}
