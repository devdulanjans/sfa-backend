package com.sfa.security;

import com.sfa.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small in-memory sliding-window limiter for anonymous write endpoints (the public sign-up
 * form), so a script can't flood the super admin's review queue. Per instance only — fine
 * for the single app container; a shared store would be needed if the API is scaled out.
 */
@Component
public class PublicRateLimiter {

    private static final int MAX_KEYS = 10_000;
    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    /** @throws BusinessException when {@code key} already made {@code max} calls within {@code window}. */
    public void check(String key, int max, Duration window) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(window);
        if (hits.size() > MAX_KEYS) {
            hits.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    return e.getValue().isEmpty() || e.getValue().peekLast().isBefore(cutoff);
                }
            });
        }
        Deque<Instant> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst().isBefore(cutoff)) {
                q.pollFirst();
            }
            if (q.size() >= max) {
                throw new BusinessException("Too many requests. Please try again later, or contact us directly.");
            }
            q.addLast(now);
        }
    }
}
