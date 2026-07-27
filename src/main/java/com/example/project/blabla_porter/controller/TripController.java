package com.example.project.blabla_porter.controller;

import com.example.project.blabla_porter.config.RequireRole;
import com.example.project.blabla_porter.dto.TripCreateRequest;
import com.example.project.blabla_porter.model.Trip;
import com.example.project.blabla_porter.model.User;
import com.example.project.blabla_porter.service.TripService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import com.example.project.blabla_porter.service.OsrmRoutingService;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/trips")
public class TripController {

    @Autowired
    private TripService tripService;

    @Autowired
    private OsrmRoutingService osrmRoutingService;

    @GetMapping("/osrm-stats")
    public Map<String, Integer> getOsrmStats() {
        return osrmRoutingService.getStats();
    }

    @PostMapping("/osrm-stats/reset")
    public Map<String, String> resetOsrmStats() {
        osrmRoutingService.resetStats();
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        return response;
    }

    @PostMapping
    @RequireRole(User.UserRole.TRAVELER)
    public Trip createTrip(@Valid @RequestBody TripCreateRequest request) {
        return tripService.createTrip(request);
    }

    @GetMapping("/search")
    // No @RequireRole — any authenticated user can search trips
    public List<Trip> searchTrips(@RequestParam(required = false) String source,
                                  @RequestParam(required = false) String destination) {
        return tripService.searchTrips(source, destination);
    }

    @GetMapping("/{id}")
    // No @RequireRole — any authenticated user can view a trip
    public Trip getTripById(@PathVariable Long id) {
        return tripService.getById(id);
    }

    @GetMapping("/traveler/{travelerId}")
    // No @RequireRole — read-only
    public List<Trip> getTripsByTraveler(@PathVariable Long travelerId) {
        return tripService.getTripsByTraveler(travelerId);
    }

    @GetMapping
    // No @RequireRole — read-only list
    public List<Trip> getAllPlannedTrips() {
        return tripService.getAllPlannedTrips();
    }
}
