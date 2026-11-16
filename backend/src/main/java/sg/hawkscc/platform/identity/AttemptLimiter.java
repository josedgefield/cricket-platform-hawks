package sg.hawkscc.platform.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts attempts per key (an email or an IP address) in a fixed window, in memory. Keys are
 * counted whether or not an account exists, so a lockout reveals nothing about who is a
 * member. A restart clears the counts, which is acceptable for one small server.
 */
final class AttemptLimiter {

    private static final int MAX_KEYS = 10_000;

    private record Window(int count, Instant start) {
    }

    private final int max;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    AttemptLimiter(int max, Duration window, Clock clock) {
        this.max = max;
        this.window = window;
        this.clock = clock;
    }

    boolean blocked(String key) {
        Window w = windows.get(key);
        return w != null && w.count() >= max && !expired(w);
    }

    void record(String key) {
        if (windows.size() >= MAX_KEYS) {
            windows.values().removeIf(this::expired);
        }
        windows.compute(key, (k, w) -> w == null || expired(w)
                ? new Window(1, clock.instant())
                : new Window(w.count() + 1, w.start()));
    }

    void reset(String key) {
        windows.remove(key);
    }

    private boolean expired(Window w) {
        return clock.instant().isAfter(w.start().plus(window));
    }
}
