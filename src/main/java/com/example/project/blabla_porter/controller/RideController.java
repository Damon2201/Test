package com.example.project.blabla_porter.controller;

import com.example.project.blabla_porter.config.RequireRole;
import com.example.project.blabla_porter.dto.RideBookingRequest;
import com.example.project.blabla_porter.model.RideRequest;
import com.example.project.blabla_porter.model.SafetyAlert;
import com.example.project.blabla_porter.model.User;
import com.example.project.blabla_porter.service.RideService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rides")
public class RideController {

    @Autowired
    private RideService rideService;

    @Autowired
    private com.example.project.blabla_porter.service.UserService userService;

    @PostMapping("/request")
    @RequireRole(User.UserRole.RIDER)
    public RideRequest requestRide(@Valid @RequestBody RideBookingRequest request) {
        return rideService.requestRide(request);
    }

    @PutMapping("/{id}/accept")
    @RequireRole(User.UserRole.TRAVELER)
    public RideRequest acceptRide(@PathVariable Long id, @RequestParam Long travelerId) {
        User traveler = userService.getById(travelerId);
        if ("PASSENGER".equalsIgnoreCase(traveler.getTravelMode())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Passenger couriers cannot accept carpool ride bookings!");
        }
        return rideService.acceptRide(id, travelerId);
    }

    @PutMapping("/{id}/start")
    @RequireRole(User.UserRole.TRAVELER)
    public RideRequest startRide(@PathVariable Long id) {
        return rideService.startRide(id);
    }

    @PutMapping("/{id}/complete")
    @RequireRole(User.UserRole.TRAVELER)
    public RideRequest completeRide(@PathVariable Long id) {
        return rideService.completeRide(id);
    }

    @PostMapping("/{id}/safety/trigger")
    @RequireRole(User.UserRole.RIDER)
    public SafetyAlert triggerSafetyEscalation(@PathVariable Long id,
                                               @RequestParam(required = false) String lastKnownLocation,
                                               @RequestParam SafetyAlert.EscalationStage stage) {
        return rideService.triggerSafetyEscalation(id, lastKnownLocation, stage);
    }

    @PostMapping("/safety/checkin/{alertId}")
    @RequireRole(User.UserRole.RIDER)
    public SafetyAlert acknowledgeCheckin(@PathVariable Long alertId, @RequestParam boolean isSafe) {
        return rideService.acknowledgeCheckin(alertId, isSafe);
    }

    @GetMapping("/{id}")
    // No @RequireRole — any authenticated user can view a ride
    public RideRequest getById(@PathVariable Long id) {
        return rideService.getById(id);
    }

    @GetMapping("/rider/{riderId}")
    // No @RequireRole — read-only
    public List<RideRequest> getRidesByRider(@PathVariable Long riderId) {
        return rideService.getRidesByRider(riderId);
    }

    @GetMapping("/trip/{tripId}")
    // No @RequireRole — read-only
    public List<RideRequest> getRidesByTrip(@PathVariable Long tripId) {
        return rideService.getRidesByTrip(tripId);
    }

    @PostMapping("/{id}/create-payment-order")
    @RequireRole(User.UserRole.RIDER)
    public com.example.project.blabla_porter.dto.RazorpayOrderResponse createPaymentOrder(@PathVariable Long id, @RequestParam Long riderId) {
        return rideService.createRazorpayOrder(id, riderId);
    }

    @PostMapping("/{id}/verify-payment")
    @RequireRole(User.UserRole.RIDER)
    public com.example.project.blabla_porter.model.Payment verifyPayment(@PathVariable Long id, @RequestBody com.example.project.blabla_porter.dto.RazorpayVerifyRequest request) {
        return rideService.verifyRazorpayPayment(id, request);
    }
}
