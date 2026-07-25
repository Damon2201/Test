package com.example.project.blabla_porter.controller;

import com.example.project.blabla_porter.dto.TrackingDto.*;
import com.example.project.blabla_porter.model.LocationPing;
import com.example.project.blabla_porter.service.RateLimitingService;
import com.example.project.blabla_porter.service.TrackingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/tracking")
public class TrackingController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TrackingController.class);

    @Autowired
    private TrackingService trackingService;

    @Autowired
    private RateLimitingService rateLimitingService;

    @PostMapping("/ping")
    public ResponseEntity<LocationPing> recordLocationPing(@Valid @RequestBody LocationPingRequest request,
                                                           HttpServletRequest httpRequest) {
        Long authenticatedUserId = (Long) httpRequest.getAttribute("authenticatedUserId");

        // Rate limit: 60 pings per minute per user (1 per second is reasonable for GPS)
        if (authenticatedUserId != null) {
            String key = String.valueOf(authenticatedUserId);
            if (!rateLimitingService.tryAcquire("tracking_ping", key, 60, Duration.ofMinutes(1))) {
                log.warn("Security Alert: Tracking ping rate limit exceeded for user {}", authenticatedUserId);
                return ResponseEntity.status(429)
                        .body(null);
            }
        }

        LocationPing ping = trackingService.recordLocationPing(request, authenticatedUserId);
        return ResponseEntity.ok(ping);
    }

    @GetMapping("/live/{tripId}")
    public ResponseEntity<?> getLiveTracking(@PathVariable Long tripId, HttpServletRequest httpRequest) {
        Long authenticatedUserId = (Long) httpRequest.getAttribute("authenticatedUserId");

        // Rate limit: 30 requests per minute per user
        if (authenticatedUserId != null) {
            String key = String.valueOf(authenticatedUserId);
            if (!rateLimitingService.tryAcquire("tracking_live", key, 30, Duration.ofMinutes(1))) {
                log.warn("Security Alert: Live tracking rate limit exceeded for user {} on trip {}", authenticatedUserId, tripId);
                return ResponseEntity.status(429)
                        .body(java.util.Map.of("error", "Too many tracking requests. Please wait before retrying."));
            }
        }

        try {
            LiveTrackingResponse response = trackingService.getLiveTracking(tripId, authenticatedUserId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("Access denied")) {
                return ResponseEntity.status(403)
                        .body(java.util.Map.of("error", e.getMessage()));
            }
            throw e;
        }
    }

    @GetMapping("/route/{tripId}")
    public ResponseEntity<?> getRouteMap(@PathVariable Long tripId, HttpServletRequest httpRequest) {
        Long authenticatedUserId = (Long) httpRequest.getAttribute("authenticatedUserId");

        // Rate limit: 30 requests per minute per user
        if (authenticatedUserId != null) {
            String key = String.valueOf(authenticatedUserId);
            if (!rateLimitingService.tryAcquire("tracking_route", key, 30, Duration.ofMinutes(1))) {
                log.warn("Security Alert: Route map rate limit exceeded for user {} on trip {}", authenticatedUserId, tripId);
                return ResponseEntity.status(429)
                        .body(java.util.Map.of("error", "Too many tracking requests. Please wait before retrying."));
            }
        }

        try {
            RouteMapResponse response = trackingService.getRouteMap(tripId, authenticatedUserId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("Access denied")) {
                return ResponseEntity.status(403)
                        .body(java.util.Map.of("error", e.getMessage()));
            }
            throw e;
        }
    }
}
