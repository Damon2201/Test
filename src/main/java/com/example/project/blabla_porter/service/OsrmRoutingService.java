package com.example.project.blabla_porter.service;

import com.example.project.blabla_porter.dto.TrackingDto.GpsPoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
public class OsrmRoutingService {

    @org.springframework.beans.factory.annotation.Value("${osrm.enabled:true}")
    private boolean osrmEnabled;

    @org.springframework.beans.factory.annotation.Value("${osrm.max-concurrent-calls:5}")
    private int maxConcurrentCalls;

    @org.springframework.beans.factory.annotation.Value("${osrm.base-url:http://router.project-osrm.org}")
    private String baseUrl;

    private volatile Semaphore osrmSemaphore;

    private Semaphore getSemaphore() {
        if (osrmSemaphore == null) {
            synchronized (this) {
                if (osrmSemaphore == null) {
                    osrmSemaphore = new Semaphore(maxConcurrentCalls, true);
                }
            }
        }
        return osrmSemaphore;
    }

    private final RestTemplate restTemplate = createRestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final java.util.concurrent.ConcurrentHashMap<String, RouteDetails> routeCache = 
            new java.util.concurrent.ConcurrentHashMap<>();

    private final java.util.concurrent.atomic.AtomicInteger totalRequests = new java.util.concurrent.atomic.AtomicInteger(0);
    private final java.util.concurrent.atomic.AtomicInteger cacheHits = new java.util.concurrent.atomic.AtomicInteger(0);
    private final java.util.concurrent.atomic.AtomicInteger osrmSuccesses = new java.util.concurrent.atomic.AtomicInteger(0);
    private final java.util.concurrent.atomic.AtomicInteger haversineFallbacks = new java.util.concurrent.atomic.AtomicInteger(0);
    private final java.util.concurrent.atomic.AtomicInteger semaphoreRejections = new java.util.concurrent.atomic.AtomicInteger(0);

    public java.util.Map<String, Integer> getStats() {
        java.util.Map<String, Integer> stats = new java.util.HashMap<>();
        stats.put("totalRequests", totalRequests.get());
        stats.put("cacheHits", cacheHits.get());
        stats.put("osrmSuccesses", osrmSuccesses.get());
        stats.put("haversineFallbacks", haversineFallbacks.get());
        stats.put("semaphoreRejections", semaphoreRejections.get());
        return stats;
    }

    public void resetStats() {
        totalRequests.set(0);
        cacheHits.set(0);
        osrmSuccesses.set(0);
        haversineFallbacks.set(0);
        semaphoreRejections.set(0);
        routeCache.clear();
    }

    private static RestTemplate createRestTemplate() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = 
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1500); // 1.5 seconds connection timeout
        factory.setReadTimeout(1500);    // 1.5 seconds read timeout
        return new RestTemplate(factory);
    }

    public static class RouteDetails {
        private final double distanceKm;
        private final List<GpsPoint> waypoints;

        public RouteDetails(double distanceKm, List<GpsPoint> waypoints) {
            this.distanceKm = distanceKm;
            this.waypoints = waypoints;
        }

        public double getDistanceKm() {
            return distanceKm;
        }

        public List<GpsPoint> getWaypoints() {
            return waypoints;
        }
    }

    public RouteDetails getRouteDetails(double lat1, double lon1, double lat2, double lon2) {
        totalRequests.incrementAndGet();

        if (!osrmEnabled) {
            haversineFallbacks.incrementAndGet();
            return getFallbackRouteDetails(lat1, lon1, lat2, lon2);
        }

        String cacheKey = String.format("%.4f_%.4f_%.4f_%.4f", lat1, lon1, lat2, lon2);
        RouteDetails cached = routeCache.get(cacheKey);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return cached;
        }

        // Rate-limit concurrent outbound OSRM calls to prevent overwhelming the public server
        boolean acquired = false;
        try {
            acquired = getSemaphore().tryAcquire(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            haversineFallbacks.incrementAndGet();
            semaphoreRejections.incrementAndGet();
            return getFallbackRouteDetails(lat1, lon1, lat2, lon2);
        }

        if (!acquired) {
            // All OSRM slots busy and queue wait exceeded 2s — fall back immediately
            haversineFallbacks.incrementAndGet();
            semaphoreRejections.incrementAndGet();
            return getFallbackRouteDetails(lat1, lon1, lat2, lon2);
        }

        try {
            // Double-check cache after acquiring semaphore: while we waited in the queue,
            // another thread may have already fetched and cached this exact route
            cached = routeCache.get(cacheKey);
            if (cached != null) {
                cacheHits.incrementAndGet();
                return cached;
            }

            // OSRM expects coordinates in longitude,latitude order!
            String url = String.format("%s/route/v1/driving/%f,%f;%f,%f?overview=full&geometries=geojson",
                    baseUrl, lon1, lat1, lon2, lat2);

            String response = restTemplate.getForObject(url, String.class);
            if (response != null) {
                JsonNode root = objectMapper.readTree(response);
                JsonNode routes = root.path("routes");
                if (routes.isArray() && routes.size() > 0) {
                    JsonNode route = routes.get(0);
                    double distanceMeters = route.path("distance").asDouble();
                    double distanceKm = distanceMeters / 1000.0;

                    List<GpsPoint> waypoints = new ArrayList<>();
                    JsonNode coordinates = route.path("geometry").path("coordinates");
                    if (coordinates.isArray()) {
                        for (JsonNode point : coordinates) {
                            if (point.isArray() && point.size() >= 2) {
                                double lon = point.get(0).asDouble();
                                double lat = point.get(1).asDouble();
                                waypoints.add(new GpsPoint(lat, lon, LocalDateTime.now()));
                            }
                        }
                    }
                    RouteDetails details = new RouteDetails(distanceKm, waypoints);
                    routeCache.put(cacheKey, details);
                    osrmSuccesses.incrementAndGet();
                    return details;
                }
            }
        } catch (Exception e) {
            System.err.println("OSRM routing API call failed, falling back to straight-line Haversine: [" + e.getClass().getSimpleName() + "] " + e.getMessage());
            io.sentry.Sentry.captureException(e);
        } finally {
            getSemaphore().release();
        }

        haversineFallbacks.incrementAndGet();
        return getFallbackRouteDetails(lat1, lon1, lat2, lon2);
    }

    private RouteDetails getFallbackRouteDetails(double lat1, double lon1, double lat2, double lon2) {
        // Fallback to straight-line Haversine distance
        double fallbackDistance = calculateHaversineDistance(lat1, lon1, lat2, lon2);
        
        // Fallback waypoints: 5 interpolated points
        List<GpsPoint> fallbackWaypoints = new ArrayList<>();
        for (int i = 0; i <= 5; i++) {
            double ratio = i / 5.0;
            double lat = lat1 + (lat2 - lat1) * ratio;
            double lon = lon1 + (lon2 - lon1) * ratio;
            fallbackWaypoints.add(new GpsPoint(lat, lon, LocalDateTime.now()));
        }

        return new RouteDetails(fallbackDistance, fallbackWaypoints);
    }

    public double getRouteDistance(double lat1, double lon1, double lat2, double lon2) {
        return getRouteDetails(lat1, lon1, lat2, lon2).getDistanceKm();
    }

    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Radius of the Earth in km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private boolean isTestEnvironment() {
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            if (element.getClassName().contains("org.junit.") || element.getClassName().contains(".jupiter.")) {
                return true;
            }
        }
        return false;
    }
}
