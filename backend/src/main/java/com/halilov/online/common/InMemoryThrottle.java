package com.halilov.online.common;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Per-key sliding-window throttle, in-process. Each call to
 * {@link #tryAcquire(String, String, int, Duration)} records a hit for the
 * (bucket,key) pair and returns true if the window count is within limit.
 * <p>
 * Single-VM only — no Redis. Fine for our 1-OCPU deployment; revisit when
 * scaling horizontally. Keys are typically client IPs.
 * <p>
 * The map grows with distinct keys; entries with no hits in the last 24h are
 * purged on the next access.
 */
@Component
public class InMemoryThrottle {

    private static final Duration GC_AFTER = Duration.ofHours(24);

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    /**
     * @return true if the request is within the allowance and may proceed,
     *         false if it should be rejected (429).
     */
    public boolean tryAcquire(String bucket, String key, int maxHits, Duration window) {
        String composite = bucket + "|" + (key == null ? "anon" : key);
        long now = Instant.now().toEpochMilli();
        long cutoff = now - window.toMillis();

        Deque<Long> deque = hits.computeIfAbsent(composite, k -> new ConcurrentLinkedDeque<>());
        synchronized (deque) {
            // drop entries outside the window
            while (!deque.isEmpty() && deque.peekFirst() < cutoff) {
                deque.pollFirst();
            }
            if (deque.size() >= maxHits) {
                return false;
            }
            deque.addLast(now);
        }
        maybeGc(now);
        return true;
    }

    private long lastGc = 0L;

    private void maybeGc(long now) {
        // Cheap periodic cleanup — once per hour, drop any deque whose newest
        // entry is older than GC_AFTER. Keeps the map from accumulating
        // single-hit IPs forever.
        if (now - lastGc < Duration.ofHours(1).toMillis()) return;
        lastGc = now;
        long expired = now - GC_AFTER.toMillis();
        hits.entrySet().removeIf(e -> {
            Deque<Long> d = e.getValue();
            Long last = d.peekLast();
            return last == null || last < expired;
        });
    }
}
