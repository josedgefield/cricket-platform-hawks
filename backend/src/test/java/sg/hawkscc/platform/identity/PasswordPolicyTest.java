package sg.hawkscc.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void acceptsAnyLongEnoughPasswordThatIsntWellKnown() {
        assertThat(PasswordPolicy.problem("correct horse battery", "ann@example.org")).isEmpty();
        assertThat(PasswordPolicy.problem("8charsOK", "ann@example.org")).isEmpty();
    }

    @Test
    void refusesShortLongCommonAndRepeatedPasswords() {
        assertThat(PasswordPolicy.problem("short", "a@b.c")).hasValueSatisfying(p -> assertThat(p).contains("at least 8"));
        assertThat(PasswordPolicy.problem(null, "a@b.c")).isPresent();
        assertThat(PasswordPolicy.problem("x".repeat(65), "a@b.c")).hasValueSatisfying(p -> assertThat(p).contains("at most"));
        assertThat(PasswordPolicy.problem("Password123", "a@b.c")).hasValueSatisfying(p -> assertThat(p).contains("common"));
        assertThat(PasswordPolicy.problem("Cricket123", "a@b.c")).isPresent();
        assertThat(PasswordPolicy.problem("aaaaaaaaaa", "a@b.c")).isPresent();
    }

    @Test
    void refusesPasswordsBuiltFromTheEmail() {
        assertThat(PasswordPolicy.problem("shreyas2026!", "Shreyas@example.org"))
                .hasValueSatisfying(p -> assertThat(p).contains("email"));
        // A very short local part isn't a meaningful match.
        assertThat(PasswordPolicy.problem("bob is my uncle", "bo@example.org")).isEmpty();
    }
}
