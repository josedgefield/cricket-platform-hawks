package sg.hawkscc.platform.identity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;

/**
 * Password rules (docs/10): 8 to 64 characters, not a well-known password, and not built from
 * the member's own email. Length, not complexity rules, is what makes a password hard to guess.
 */
final class PasswordPolicy {

    static final int MIN = 8;
    static final int MAX = 64;
    /** bcrypt only reads the first 72 bytes. */
    private static final int MAX_BYTES = 72;

    private static final Set<String> COMMON = load();

    private PasswordPolicy() {
    }

    /** The reason a password is refused, or empty if it is fine. */
    static Optional<String> problem(String password, String email) {
        if (password == null || password.length() < MIN) {
            return Optional.of("Use at least " + MIN + " characters.");
        }
        if (password.length() > MAX || password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return Optional.of("Use at most " + MAX + " characters.");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (COMMON.contains(lower) || password.chars().distinct().count() == 1) {
            return Optional.of("That password is too common. Try a short phrase of a few words.");
        }
        String local = Tokens.normaliseEmail(email);
        int at = local.indexOf('@');
        local = at > 0 ? local.substring(0, at) : local;
        if (local.length() >= 4 && lower.contains(local)) {
            return Optional.of("Don't use your email address in your password.");
        }
        return Optional.empty();
    }

    static void check(String password, String email) {
        problem(password, email).ifPresent(p -> {
            throw IdentityException.badRequest(p);
        });
    }

    private static Set<String> load() {
        try (var in = new BufferedReader(new InputStreamReader(
                new ClassPathResource("identity/common-passwords.txt").getInputStream(), StandardCharsets.UTF_8))) {
            return in.lines().map(String::strip).filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .map(l -> l.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
