package com.example.project.blabla_porter.service;

import com.example.project.blabla_porter.dto.RazorpayVerifyRequest;
import com.example.project.blabla_porter.model.LocalTaxiBooking;
import com.example.project.blabla_porter.model.LocalTaxiBookingStatus;
import com.example.project.blabla_porter.model.ParcelRequest;
import com.example.project.blabla_porter.model.RideRequest;
import com.example.project.blabla_porter.repository.LocalTaxiBookingRepository;
import com.example.project.blabla_porter.repository.ParcelRequestRepository;
import com.example.project.blabla_porter.repository.RideRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PaymentReconciliationScheduler {

    @Value("${razorpay.key.id:}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret:}")
    private String razorpayKeySecret;

    @Autowired
    private RideRequestRepository rideRequestRepository;

    @Autowired
    private ParcelRequestRepository parcelRequestRepository;

    @Autowired
    private LocalTaxiBookingRepository taxiBookingRepository;

    @Autowired
    private RideService rideService;

    @Autowired
    private ParcelService parcelService;

    @Autowired
    private LocalTaxiService localTaxiService;

    // Runs every 15 minutes
    @Scheduled(fixedRate = 900000)
    public void reconcilePayments() {
        System.out.println("Starting Payment Reconciliation Cron Job...");
        reconcileRides();
        reconcileParcels();
        reconcileTaxis();
        System.out.println("Finished Payment Reconciliation Cron Job.");
    }

    public void reconcileRides() {
        List<RideRequest> pendingRides = rideRequestRepository.findByStatus(RideRequest.RideStatus.REQUESTED);
        for (RideRequest ride : pendingRides) {
            String orderId = ride.getRazorpayOrderId();
            if (orderId == null || orderId.isBlank()) continue;

            if (isOrderPaidOnRazorpay(orderId)) {
                try {
                    System.out.println("Reconciling Ride Request " + ride.getId() + " - Order " + orderId + " was paid!");
                    rideService.verifyRazorpayPayment(ride.getId(), RazorpayVerifyRequest.builder()
                            .razorpayOrderId(orderId)
                            .razorpayPaymentId("pay_reconciled_" + orderId)
                            .razorpaySignature("sig_reconciled_" + orderId)
                            .senderId(ride.getRiderId())
                            .build());
                } catch (Exception e) {
                    System.err.println("Failed to reconcile Ride " + ride.getId() + ": " + e.getMessage());
                }
            }
        }
    }

    public void reconcileParcels() {
        List<ParcelRequest> pendingParcels = parcelRequestRepository.findByStatus(ParcelRequest.ParcelStatus.ACCEPTED);
        for (ParcelRequest parcel : pendingParcels) {
            String orderId = parcel.getRazorpayOrderId();
            if (orderId == null || orderId.isBlank()) continue;

            if (isOrderPaidOnRazorpay(orderId)) {
                try {
                    System.out.println("Reconciling Parcel Request " + parcel.getId() + " - Order " + orderId + " was paid!");
                    parcelService.verifyRazorpayPayment(parcel.getId(), RazorpayVerifyRequest.builder()
                            .razorpayOrderId(orderId)
                            .razorpayPaymentId("pay_reconciled_" + orderId)
                            .razorpaySignature("sig_reconciled_" + orderId)
                            .senderId(parcel.getSenderId())
                            .build());
                } catch (Exception e) {
                    System.err.println("Failed to reconcile Parcel " + parcel.getId() + ": " + e.getMessage());
                }
            }
        }
    }

    public void reconcileTaxis() {
        List<LocalTaxiBooking> pendingTaxis = taxiBookingRepository.findAll();
        for (LocalTaxiBooking booking : pendingTaxis) {
            if (booking.getStatus() != LocalTaxiBookingStatus.REQUESTED) continue;
            String orderId = booking.getRazorpayOrderId();
            if (orderId == null || orderId.isBlank()) continue;

            if (isOrderPaidOnRazorpay(orderId)) {
                try {
                    System.out.println("Reconciling Local Taxi Booking " + booking.getId() + " - Order " + orderId + " was paid!");
                    localTaxiService.verifyPayment(booking.getId(), orderId, "pay_reconciled_" + orderId, "sig_reconciled_" + orderId);
                } catch (Exception e) {
                    System.err.println("Failed to reconcile Taxi Booking " + booking.getId() + ": " + e.getMessage());
                }
            }
        }
    }

    private boolean isOrderPaidOnRazorpay(String orderId) {
        boolean isProduction = razorpayKeyId != null && !razorpayKeyId.isBlank() && razorpayKeySecret != null && !razorpayKeySecret.isBlank();
        if (!isProduction && (orderId.contains("reconcile_mock_succeed") || orderId.startsWith("order_mock_reconcile"))) {
            return true;
        }

        if (!isProduction) {
            return false;
        }

        try {
            com.razorpay.RazorpayClient client = new com.razorpay.RazorpayClient(razorpayKeyId, razorpayKeySecret);
            com.razorpay.Order order = client.orders.fetch(orderId);
            String status = order.get("status");
            return "paid".equalsIgnoreCase(status);
        } catch (Exception e) {
            System.err.println("Error fetching order " + orderId + " from Razorpay: " + e.getMessage());
            return false;
        }
    }
}
