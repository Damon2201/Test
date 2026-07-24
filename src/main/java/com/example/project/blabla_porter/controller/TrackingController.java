package com.example.project.blabla_porter.controller;

import com.example.project.blabla_porter.dto.TrackingDto.*;
import com.example.project.blabla_porter.model.LocationPing;
import com.example.project.blabla_porter.service.TrackingService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tracking")
public class TrackingController {

    @Autowired
    private TrackingService trackingService;

    @PostMapping("/ping")
    public ResponseEntity<LocationPing> recordLocationPing(@Valid @RequestBody LocationPingRequest request) {
        LocationPing ping = trackingService.recordLocationPing(request);
        return ResponseEntity.ok(ping);
    }

    @GetMapping("/live/{tripId}")
    public ResponseEntity<LiveTrackingResponse> getLiveTracking(@PathVariable Long tripId) {
        LiveTrackingResponse response = trackingService.getLiveTracking(tripId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/route/{tripId}")
    public ResponseEntity<RouteMapResponse> getRouteMap(@PathVariable Long tripId) {
        RouteMapResponse response = trackingService.getRouteMap(tripId);
        return ResponseEntity.ok(response);
    }
}
