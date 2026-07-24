package com.example.project.blabla_porter.service;

import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitingService {

    // Map: Category -> Key -> List of request timestamps
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, List<Instant>>> registry = new ConcurrentHashMap<>();

    /**
     * Tries to acquire a permit. Returns true if allowed, false if rate limit exceeded.
     */
    public boolean tryAcquire(String category, String key, int maxRequests, Duration window) {
        if (key == null || key.isBlank()) {
            return true;
        }
        
        Instant now = Instant.now();
        Instant cutoff = now.minus(window);

        ConcurrentHashMap<String, List<Instant>> keyMap = registry.computeIfAbsent(category, c -> new ConcurrentHashMap<>());
        List<Instant> timestamps = keyMap.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()));

        synchronized (timestamps) {
            // Remove timestamps older than the sliding window
            timestamps.removeIf(t -> t.isBefore(cutoff));

            if (timestamps.size() >= maxRequests) {
                return false;
            }

            timestamps.add(now);
            return true;
        }
    }

    /**
     * Resets rate limit counters for a specific category. Useful for testing.
     */
    public void resetCategory(String category) {
        registry.remove(category);
    }
}
