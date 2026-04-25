package utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Retry semantics for Playwright element interactions and AI API calls.
 * All methods block the calling thread between attempts.
 */
public class RetryUtils {

    private static final Logger log = LoggerFactory.getLogger(RetryUtils.class);

    private RetryUtils() {
    }

    /**
     * Retries {@code action} up to {@code maxAttempts} times with a fixed
     * {@code delayMs} pause between each attempt.
     */
    public static <T> T retry(int maxAttempts, long delayMs, Supplier<T> action) {
        if (maxAttempts < 1)
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        Exception last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                T result = action.get();
                if (attempt > 1)
                    log.info("Succeeded on attempt {}/{}", attempt, maxAttempts);
                return result;
            } catch (Exception e) {
                last = e;
                log.warn("Attempt {}/{} failed: {}", attempt, maxAttempts, e.getMessage());
                if (attempt < maxAttempts)
                    sleep(delayMs);
            }
        }
        throw new RuntimeException("All " + maxAttempts + " attempts failed. Last: "
                + (last != null ? last.getMessage() : "unknown"), last);
    }

    /** Retries a void {@link Runnable} action. */
    public static void retryVoid(int maxAttempts, long delayMs, Runnable action) {
        retry(maxAttempts, delayMs, () -> {
            action.run();
            return null;
        });
    }

    /**
     * Retries with exponential back-off.
     * Delay doubles after each failure, capped at {@code maxDelayMs}.
     * Ideal for slow external APIs (e.g. OpenAI).
     *
     * @param maxAttempts  max attempts
     * @param initialDelay initial sleep in ms
     * @param maxDelayMs   upper cap on sleep in ms
     * @param action       value-returning action
     */
    public static <T> T retryWithBackoff(int maxAttempts, long initialDelay, long maxDelayMs,
            Supplier<T> action) {
        Exception last = null;
        long delay = initialDelay;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.get();
            } catch (Exception e) {
                last = e;
                log.warn("Attempt {}/{} failed (next delay {}ms): {}",
                        attempt, maxAttempts, delay, e.getMessage());
                if (attempt < maxAttempts) {
                    sleep(delay);
                    delay = Math.min(delay * 2, maxDelayMs);
                }
            }
        }
        throw new RuntimeException("All " + maxAttempts + " attempts failed (backoff). Last: "
                + (last != null ? last.getMessage() : "unknown"), last);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
