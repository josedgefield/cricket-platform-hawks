package sg.hawkscc.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class AttemptLimiterTest {

    /** A clock the test moves by hand. */
    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-09T00:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void blocksAfterTheLimitUntilTheWindowEnds() {
        var clock = new MutableClock();
        var limiter = new AttemptLimiter(3, Duration.ofMinutes(15), clock);
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.blocked("a")).isFalse();
            limiter.record("a");
        }
        assertThat(limiter.blocked("a")).isTrue();
        assertThat(limiter.blocked("b")).isFalse();
        clock.now = clock.now.plus(Duration.ofMinutes(16));
        assertThat(limiter.blocked("a")).isFalse();
    }

    @Test
    void resetClearsTheCount() {
        var limiter = new AttemptLimiter(2, Duration.ofMinutes(15), new MutableClock());
        limiter.record("a");
        limiter.record("a");
        limiter.reset("a");
        assertThat(limiter.blocked("a")).isFalse();
    }
}
