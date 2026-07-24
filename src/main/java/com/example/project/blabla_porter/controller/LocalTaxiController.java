package com.example.project.blabla_porter.controller;

import com.example.project.blabla_porter.config.RequireRole;
import com.example.project.blabla_porter.dto.LocalTaxiBookingRequest;
import com.example.project.blabla_porter.dto.RazorpayVerifyRequest;
import com.example.project.blabla_porter.model.LocalCaptainStatus;
import com.example.project.blabla_porter.model.LocalTaxiBooking;
import com.example.project.blabla_porter.model.LocalTaxiBookingStatus;
import com.example.project.blabla_porter.model.Payment;
import com.example.project.blabla_porter.model.User;
import com.example.project.blabla_porter.service.LocalTaxiService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/taxi")
public class LocalTaxiController {

    @Autowired
    private LocalTaxiService localTaxiService;

    @PostMapping("/captain/status")
    @RequireRole(User.UserRole.TRAVELER)
    public LocalCaptainStatus updateCaptainStatus(@RequestParam Long captainId,
                                                  @RequestParam boolean available,
                                                  @RequestParam Double latitude,
                                                  @RequestParam Double longitude) {
        return localTaxiService.toggleAvailability(captainId, available, latitude, longitude);
    }

    @GetMapping("/captain/status/{captainId}")
    public LocalCaptainStatus getCaptainStatus(@PathVariable Long captainId) {
        return localTaxiService.getCaptainStatus(captainId)
                .orElse(LocalCaptainStatus.builder().captainId(captainId).available(false).build());
    }

    @PostMapping("/book")
    @RequireRole(User.UserRole.RIDER)
    public LocalTaxiBooking bookTaxi(@Valid @RequestBody LocalTaxiBookingRequest request) {
        return localTaxiService.bookTaxi(
                request.getRiderId(),
                request.getPickupLocation(),
                request.getPickupLatitude(),
                request.getPickupLongitude(),
                request.getDropoffLocation(),
                request.getDropoffLatitude(),
                request.getDropoffLongitude(),
                request.isSafetyModeEnabled()
        );
    }

    @GetMapping("/{id}")
    public LocalTaxiBooking getBooking(@PathVariable Long id) {
        return localTaxiService.getBooking(id)
                .orElseThrow(() -> new RuntimeException("Taxi booking not found with ID: " + id));
    }

    @PostMapping("/{id}/create-payment-order")
    @RequireRole(User.UserRole.RIDER)
    public Map<String, Object> createPaymentOrder(@PathVariable Long id, @RequestParam Long riderId) {
        return localTaxiService.createPaymentOrder(id, riderId);
    }

    @PostMapping("/{id}/verify-payment")
    public Payment verifyPayment(@PathVariable Long id, @RequestBody RazorpayVerifyRequest request) {
        return localTaxiService.verifyPayment(
                id,
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature()
        );
    }

    @PostMapping("/{id}/status")
    public LocalTaxiBooking updateStatus(@PathVariable Long id,
                                         @RequestParam Long userId,
                                         @RequestParam LocalTaxiBookingStatus status) {
        return localTaxiService.updateBookingStatus(id, userId, status);
    }

    @GetMapping("/rider/{riderId}")
    @RequireRole(User.UserRole.RIDER)
    public List<LocalTaxiBooking> getRiderBookings(@PathVariable Long riderId) {
        return localTaxiService.getRiderBookings(riderId);
    }

    @GetMapping("/captain-bookings/{captainId}")
    @RequireRole(User.UserRole.TRAVELER)
    public List<LocalTaxiBooking> getCaptainBookings(@PathVariable Long captainId) {
        return localTaxiService.getCaptainBookings(captainId);
    }
}
