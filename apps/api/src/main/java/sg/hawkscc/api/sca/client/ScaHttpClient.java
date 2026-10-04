package sg.hawkscc.api.sca.client;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import sg.hawkscc.api.sca.config.ScaProperties;

/**
 * Polite HTTP access to the SCA site: one request at a time, a minimum gap between requests, timeouts, and retries
 * with exponential backoff and jitter for transient failures (network errors, 429, 5xx).
 */
@Component
public class ScaHttpClient {

    private static final Logger log = LoggerFactory.getLogger(ScaHttpClient.class);

    private final ScaProperties props;
    private final HttpClient http;
    private final Sleeper sleeper;
    private long lastRequestNanos = 0;

    @Autowired
    public ScaHttpClient(ScaProperties props) {
        this(props, HttpClient.newBuilder()
                .connectTimeout(props.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .proxy(ProxySelector.getDefault())
                .build(), Thread::sleep);
    }

    ScaHttpClient(ScaProperties props, HttpClient http, Sleeper sleeper) {
        this.props = props;
        this.http = http;
        this.sleeper = sleeper;
    }

    public record Response(int status, String contentType, String body, URI finalUri) {
        public boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    /** GET with retries. Returns the last response (possibly non-2xx); throws only if every attempt errored. */
    public synchronized Response get(URI uri, String accept) throws IOException, InterruptedException {
        IOException lastError = null;
        Response last = null;
        Duration backoff = props.initialBackoff();
        for (int attempt = 1; attempt <= props.maxAttempts(); attempt++) {
            awaitPoliteGap();
            try {
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(props.requestTimeout())
                        .header("User-Agent", props.userAgent())
                        .header("Accept", accept)
                        .GET()
                        .build();
                HttpResponse<byte[]> res = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
                last = new Response(res.statusCode(),
                        res.headers().firstValue("Content-Type").orElse(""),
                        new String(res.body(), StandardCharsets.UTF_8),
                        res.uri());
                if (!retryable(res.statusCode())) {
                    return last;
                }
                log.warn("SCA {} returned {} (attempt {}/{})", uri, res.statusCode(), attempt, props.maxAttempts());
            } catch (IOException e) {
                lastError = e;
                log.warn("SCA {} failed: {} (attempt {}/{})", uri, e.toString(), attempt, props.maxAttempts());
            }
            if (attempt < props.maxAttempts()) {
                long jitter = ThreadLocalRandom.current().nextLong(0, Math.max(1, backoff.toMillis() / 2));
                sleeper.sleep(backoff.toMillis() + jitter);
                backoff = backoff.multipliedBy(2);
            }
        }
        if (last != null) {
            return last;
        }
        throw lastError;
    }

    private static boolean retryable(int status) {
        return status == 429 || status >= 500;
    }

    private void awaitPoliteGap() throws InterruptedException {
        long gap = props.minDelayBetweenRequests().toNanos();
        long wait = lastRequestNanos == 0 ? 0 : (lastRequestNanos + gap) - System.nanoTime();
        if (wait > 0) {
            sleeper.sleep(wait / 1_000_000);
        }
        lastRequestNanos = System.nanoTime();
    }

    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
