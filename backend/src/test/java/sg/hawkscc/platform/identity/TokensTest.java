package sg.hawkscc.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokensTest {

    @Test
    void tokensAreLongRandomAndUrlSafe() {
        String a = Tokens.newToken();
        assertThat(a).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(Tokens.newToken()).isNotEqualTo(a);
    }

    @Test
    void onlyAStableHashIsStored() {
        assertThat(Tokens.hash("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(Tokens.hash("abc")).isNotEqualTo("abc");
    }

    @Test
    void emailsAreComparedCaseInsensitively() {
        assertThat(Tokens.normaliseEmail("  Ann.Lee@Example.ORG ")).isEqualTo("ann.lee@example.org");
    }
}
