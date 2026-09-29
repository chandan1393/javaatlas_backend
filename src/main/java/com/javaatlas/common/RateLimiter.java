package com.javaatlas.common;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/**
 * In-memory fixed-window limiter. Good for one server; with several instances, move this to Redis.
 */
@Component
public class RateLimiter {

    private static final long ONE_DAY_MS = Duration.ofDays(1).toMillis();

    private record Window(long start, long length, AtomicInteger count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /** Counts one attempt and says whether it's within the limit. */
    public boolean allow(String key, int limit, Duration window) {
        if (limit <= 0) {
            return true;
        }
        return current(key, window).count().incrementAndGet() <= limit;
    }

    /** True if the key has already reached the limit in this window (without counting an attempt). */
    public boolean blocked(String key, int limit, Duration window) {
        Window w = windows.get(key);
        return w != null && System.currentTimeMillis() - w.start() < w.length() && w.count().get() >= limit;
    }

    /** Counts one attempt without checking (for example a failed login). */
    public void hit(String key, Duration window) {
        current(key, window).count().incrementAndGet();
    }

    public void reset(String key) {
        windows.remove(key);
    }

    private Window current(String key, Duration window) {
        long now = System.currentTimeMillis();
        long length = window.toMillis();
        Window w = windows.compute(key, (k, old) ->
                old == null || now - old.start() >= old.length() ? new Window(now, length, new AtomicInteger()) : old);
        if (windows.size() > 50_000) {
            windows.entrySet().removeIf(e -> now - e.getValue().start() > ONE_DAY_MS);
        }
        return w;
    }
}
