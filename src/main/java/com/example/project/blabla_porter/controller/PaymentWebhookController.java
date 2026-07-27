package com.example.project.blabla_porter.controller;

import com.example.project.blabla_porter.dto.RazorpayVerifyRequest;
import com.example.project.blabla_porter.model.LocalTaxiBooking;
import com.example.project.blabla_porter.model.LocalTaxiBookingStatus;
import com.example.project.blabla_porter.model.ParcelRequest;
import com.example.project.blabla_porter.model.RideRequest;
import com.example.project.blabla_porter.repository.LocalTaxiBookingRepository;
import com.example.project.blabla_porter.repository.ParcelRequestRepository;
import com.example.project.blabla_porter.repository.RideRequestRepository;
import com.example.project.blabla_porter.service.LocalTaxiService;
import com.example.project.blabla_porter.service.ParcelService;
import com.example.project.blabla_porter.service.RideService;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payments/razorpay")
public class PaymentWebhookController {

    @Value("${razorpay.webhook.secret:rzp_webhook_secret_default_123}")
    private String webhookSecret;

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

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> handleWebhook(
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestBody String payload) {

        System.out.println("DEBUG WEBHOOK - Payload: " + payload);
        System.out.println("DEBUG WEBHOOK - Signature: " + signature);

        // 1. Verify Razorpay webhook signature
        boolean isValid = false;
        boolean isProduction = razorpayKeyId != null && !razorpayKeyId.isBlank() && razorpayKeySecret != null && !razorpayKeySecret.isBlank();

        if (!isProduction && signature != null && (signature.startsWith("sig_mock_") || signature.startsWith("sig_webhook_"))) {
            isValid = true;
        } else {
            try {
                if (signature != null && !signature.isBlank()) {
                    com.razorpay.Utils.verifyWebhookSignature(payload, signature, webhookSecret);
                    isValid = true;
                }
            } catch (Exception e) {
                isValid = false;
                System.err.println("Webhook signature verification failed: " + e.getMessage());
            }
        }

        if (!isValid) {
            Map<String, String> err = new HashMap<>();
            err.put("error", "Invalid signature");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        }

        // 2. Parse payload event details
        Map<String, String> response = new HashMap<>();
        try {
            JSONObject json = new JSONObject(payload);
            String event = json.optString("event");
            System.out.println("DEBUG WEBHOOK - Processing event: " + event);

            String orderId = null;
            String paymentId = null;

            if ("payment.captured".equals(event)) {
                JSONObject paymentEntity = json.getJSONObject("payload")
                        .getJSONObject("payment")
                        .getJSONObject("entity");
                orderId = paymentEntity.optString("order_id");
                paymentId = paymentEntity.optString("id");
            } else if ("order.paid".equals(event)) {
                JSONObject orderEntity = json.getJSONObject("payload")
                        .getJSONObject("order")
                        .getJSONObject("entity");
                orderId = orderEntity.optString("id");
                paymentId = "pay_webhook_" + orderId;
            }

            if (orderId == null || orderId.isBlank()) {
                response.put("status", "ignored");
                response.put("reason", "No order ID in payload");
                return ResponseEntity.ok(response);
            }

            String finalSignature = signature != null ? signature : ("sig_webhook_" + orderId);

            // 3. Look up and reconcile matching requests
            // Check RideRequest
            java.util.Optional<RideRequest> rideOpt = rideRequestRepository.findByRazorpayOrderId(orderId);
            if (rideOpt.isPresent()) {
                RideRequest ride = rideOpt.get();
                if (ride.getStatus() == RideRequest.RideStatus.REQUESTED) {
                    rideService.verifyRazorpayPayment(ride.getId(), RazorpayVerifyRequest.builder()
                            .razorpayOrderId(orderId)
                            .razorpayPaymentId(paymentId)
                            .razorpaySignature(finalSignature)
                            .senderId(ride.getRiderId())
                            .build());
                    response.put("status", "success");
                    response.put("type", "ride");
                    response.put("id", ride.getId().toString());
                    return ResponseEntity.ok(response);
                }
            }

            // Check ParcelRequest
            java.util.Optional<ParcelRequest> parcelOpt = parcelRequestRepository.findByRazorpayOrderId(orderId);
            if (parcelOpt.isPresent()) {
                ParcelRequest parcel = parcelOpt.get();
                if (parcel.getStatus() == ParcelRequest.ParcelStatus.ACCEPTED) {
                    parcelService.verifyRazorpayPayment(parcel.getId(), RazorpayVerifyRequest.builder()
                            .razorpayOrderId(orderId)
                            .razorpayPaymentId(paymentId)
                            .razorpaySignature(finalSignature)
                            .senderId(parcel.getSenderId())
                            .build());
                    response.put("status", "success");
                    response.put("type", "parcel");
                    response.put("id", parcel.getId().toString());
                    return ResponseEntity.ok(response);
                }
            }

            // Check LocalTaxiBooking
            java.util.Optional<LocalTaxiBooking> taxiOpt = taxiBookingRepository.findByRazorpayOrderId(orderId);
            if (taxiOpt.isPresent()) {
                LocalTaxiBooking booking = taxiOpt.get();
                if (booking.getStatus() == LocalTaxiBookingStatus.REQUESTED) {
                    localTaxiService.verifyPayment(booking.getId(), orderId, paymentId, finalSignature);
                    response.put("status", "success");
                    response.put("type", "taxi");
                    response.put("id", booking.getId().toString());
                    return ResponseEntity.ok(response);
                }
            }

            response.put("status", "ignored");
            response.put("reason", "No pending request matched order " + orderId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("Failed to parse/process webhook: " + e.getMessage());
            e.printStackTrace();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
