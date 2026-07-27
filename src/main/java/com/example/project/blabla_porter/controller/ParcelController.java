package com.example.project.blabla_porter.controller;

import com.example.project.blabla_porter.config.RequireRole;
import com.example.project.blabla_porter.dto.OtpVerificationRequest;
import com.example.project.blabla_porter.dto.ParcelBookingRequest;
import com.example.project.blabla_porter.model.ParcelRequest;
import com.example.project.blabla_porter.model.Payment;
import com.example.project.blabla_porter.model.User;
import com.example.project.blabla_porter.service.ParcelService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/parcels")
public class ParcelController {

    @Autowired
    private ParcelService parcelService;

    @PostMapping("/request")
    @RequireRole(User.UserRole.SENDER)
    public ParcelRequest createParcelRequest(@Valid @RequestBody ParcelBookingRequest request) {
        return parcelService.createParcelRequest(request);
    }

    @GetMapping("/quote")
    // No @RequireRole — any authenticated user can view fare quotes
    public com.example.project.blabla_porter.dto.FareBreakdownDTO getFareQuote(
            @RequestParam(required = false) Double declaredValue,
            @RequestParam(required = false) Double distanceKm,
            @RequestParam(required = false) Double weightKg,
            @RequestParam(required = false) Double pickupLat,
            @RequestParam(required = false) Double pickupLng,
            @RequestParam(required = false) Double dropoffLat,
            @RequestParam(required = false) Double dropoffLng) {
        return parcelService.getFareQuote(declaredValue, distanceKm, weightKg, pickupLat, pickupLng, dropoffLat, dropoffLng);
    }

    @PutMapping("/{id}/accept")
    @RequireRole(User.UserRole.TRAVELER)
    public ParcelRequest acceptParcelRequest(@PathVariable Long id, @RequestParam Long travelerId) {
        return parcelService.acceptParcelRequest(id, travelerId);
    }

    @PutMapping("/{id}/fare")
    @RequireRole(User.UserRole.TRAVELER)
    public ParcelRequest updateParcelFare(@PathVariable Long id, @RequestParam Double fare, @RequestParam Long travelerId) {
        return parcelService.updateParcelFare(id, fare, travelerId);
    }

    @PostMapping("/{id}/create-payment-order")
    @RequireRole(User.UserRole.SENDER)
    public com.example.project.blabla_porter.dto.RazorpayOrderResponse createPaymentOrder(@PathVariable Long id, @RequestParam Long senderId) {
        return parcelService.createRazorpayOrder(id, senderId);
    }

    @PostMapping("/{id}/verify-payment")
    @RequireRole(User.UserRole.SENDER)
    public Payment verifyPayment(@PathVariable Long id, @RequestBody com.example.project.blabla_porter.dto.RazorpayVerifyRequest request) {
        request.setParcelRequestId(id);
        return parcelService.verifyRazorpayPayment(id, request);
    }


    @PostMapping("/{id}/verify-pickup")
    @RequireRole(User.UserRole.TRAVELER)
    public ParcelRequest verifyPickup(@PathVariable Long id, @Valid @RequestBody OtpVerificationRequest request) {
        request.setParcelRequestId(id);
        return parcelService.verifyPickup(request);
    }

    @PostMapping("/{id}/verify-delivery")
    @RequireRole(User.UserRole.TRAVELER)
    public ParcelRequest verifyDelivery(@PathVariable Long id, @Valid @RequestBody OtpVerificationRequest request) {
        request.setParcelRequestId(id);
        return parcelService.verifyDelivery(request);
    }

    @PostMapping("/{id}/cancel")
    // No @RequireRole — both sender and traveler can cancel
    public ParcelRequest cancelAndRefund(@PathVariable Long id, @RequestParam Long userId) {
        return parcelService.cancelAndRefund(id, userId);
    }

    @GetMapping("/{id}")
    // No @RequireRole — any authenticated user can view a parcel by ID
    public ParcelRequest getById(@PathVariable Long id) {
        return parcelService.getById(id);
    }

    @GetMapping("/{id}/payment")
    public Payment getPaymentByParcelRequestId(@PathVariable Long id, jakarta.servlet.http.HttpServletRequest request) {
        Long authenticatedUserId = (Long) request.getAttribute("authenticatedUserId");
        return parcelService.getPaymentByParcelRequestId(id, authenticatedUserId);
    }

    @GetMapping("/sender/{senderId}")
    // No @RequireRole — read-only, filtering happens by senderId
    public List<ParcelRequest> getRequestsBySender(@PathVariable Long senderId) {
        return parcelService.getRequestsBySender(senderId);
    }

    @GetMapping("/trip/{tripId}")
    // No @RequireRole — read-only, any authenticated user can see parcels on a trip
    public List<ParcelRequest> getRequestsByTrip(@PathVariable Long tripId) {
        return parcelService.getRequestsByTrip(tripId);
    }
}
