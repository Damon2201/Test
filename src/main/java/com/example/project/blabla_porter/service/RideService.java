package com.example.project.blabla_porter.service;

import com.example.project.blabla_porter.dto.RideBookingRequest;
import com.example.project.blabla_porter.model.*;
import com.example.project.blabla_porter.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class RideService {

    @Autowired
    private RideRequestRepository rideRequestRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SafetyAlertRepository safetyAlertRepository;

    @Autowired
    private TrustedContactRepository trustedContactRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private SmsService smsService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private NotificationService notificationService;

    @org.springframework.beans.factory.annotation.Value("${razorpay.key.id:}")
    private String razorpayKeyId;

    @org.springframework.beans.factory.annotation.Value("${razorpay.key.secret:}")
    private String razorpayKeySecret;

    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    public double calculateRideFare(double distanceKm) {
        if (distanceKm <= 0) return 50.0;
        double fare = 50.0;
        if (distanceKm <= 100) {
            fare += distanceKm * 1.50;
        } else {
            fare += (100 * 1.50) + (distanceKm - 100) * 1.00;
        }
        return fare;
    }

    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Earth radius in kilometers
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    public RideRequest requestRide(RideBookingRequest req) {
        userRepository.findById(req.getRiderId())
                .orElseThrow(() -> new RuntimeException("Rider not found with id: " + req.getRiderId()));

        Trip trip = tripRepository.findById(req.getTripId())
                .orElseThrow(() -> new RuntimeException("Trip not found with id: " + req.getTripId()));

        if (trip.getStatus() != Trip.TripStatus.PLANNED && trip.getStatus() != Trip.TripStatus.ACTIVE) {
            throw new IllegalStateException("Trip is not active or planned!");
        }

        int estDuration = (req.getEstimatedDurationMinutes() != null && req.getEstimatedDurationMinutes() > 0)
                ? req.getEstimatedDurationMinutes() : 30;

        // Dynamic Buffer = max(5 min, 20% of estimated trip duration)
        int bufferMinutes = Math.max(5, (int) Math.ceil(estDuration * 0.20));

        Double distance = 0.0;
        if (req.getPickupLatitude() != null && req.getPickupLongitude() != null &&
            req.getDropoffLatitude() != null && req.getDropoffLongitude() != null) {
            distance = calculateHaversineDistance(
                req.getPickupLatitude(), req.getPickupLongitude(),
                req.getDropoffLatitude(), req.getDropoffLongitude()
            );
        }

        double fare = calculateRideFare(distance);

        RideRequest ride = RideRequest.builder()
                .riderId(req.getRiderId())
                .tripId(req.getTripId())
                .pickupLocation(req.getPickupLocation())
                .dropoffLocation(req.getDropoffLocation())
                .pickupLatitude(req.getPickupLatitude())
                .pickupLongitude(req.getPickupLongitude())
                .dropoffLatitude(req.getDropoffLatitude())
                .dropoffLongitude(req.getDropoffLongitude())
                .calculatedFare(fare)
                .safetyModeEnabled(req.getSafetyModeEnabled() != null ? req.getSafetyModeEnabled() : false)
                .estimatedDurationMinutes(estDuration)
                .bufferMinutes(bufferMinutes)
                .status(RideRequest.RideStatus.REQUESTED)
                .build();

        return rideRequestRepository.save(ride);
    }

    public RideRequest acceptRide(Long rideRequestId, Long travelerId) {
        RideRequest ride = getById(rideRequestId);
        Trip trip = tripRepository.findById(ride.getTripId()).orElseThrow();

        if (!trip.getTravelerId().equals(travelerId)) {
            throw new IllegalArgumentException("Only the designated traveler for this trip can accept the ride request!");
        }

        if (ride.getStatus() != RideRequest.RideStatus.REQUESTED) {
            throw new IllegalStateException("Ride request is not in REQUESTED status!");
        }

        ride.setStatus(RideRequest.RideStatus.ACCEPTED);
        RideRequest saved = rideRequestRepository.save(ride);

        // Broadcast to WebSocket subscribers
        try {
            messagingTemplate.convertAndSend("/topic/trip/" + ride.getTripId(), saved);
        } catch (Exception e) {
            System.err.println("Failed to broadcast WebSocket update on acceptRide: " + e.getMessage());
        }

        // Trigger FCM push notification to rider
        try {
            User traveler = userRepository.findById(travelerId).orElse(null);
            String captainName = traveler != null ? traveler.getFullName() : "Your Captain";
            notificationService.sendPushToUser(
                    ride.getRiderId(),
                    "Ride Confirmed",
                    "Captain " + captainName + " has accepted your ride request.",
                    Map.of("type", "RIDE_ACCEPTED", "rideId", String.valueOf(rideRequestId))
            );
        } catch (Exception e) {
            System.err.println("Failed to send FCM push on acceptRide: " + e.getMessage());
        }

        return saved;
    }

    public RideRequest startRide(Long rideRequestId) {
        RideRequest ride = getById(rideRequestId);
        if (ride.getStatus() != RideRequest.RideStatus.ACCEPTED) {
            throw new IllegalStateException("Ride must be in ACCEPTED status to start!");
        }

        ride.setStatus(RideRequest.RideStatus.IN_PROGRESS);
        RideRequest saved = rideRequestRepository.save(ride);

        // Broadcast to WebSocket subscribers
        try {
            messagingTemplate.convertAndSend("/topic/trip/" + ride.getTripId(), saved);
        } catch (Exception e) {
            System.err.println("Failed to broadcast WebSocket update on startRide: " + e.getMessage());
        }

        // Trigger FCM push notification to rider
        try {
            notificationService.sendPushToUser(
                    ride.getRiderId(),
                    "Ride Started",
                    "Your ride is now in progress.",
                    Map.of("type", "RIDE_STARTED", "rideId", String.valueOf(rideRequestId))
            );
        } catch (Exception e) {
            System.err.println("Failed to send FCM push on startRide: " + e.getMessage());
        }

        return saved;
    }

    @Transactional
    public RideRequest completeRide(Long rideRequestId) {
        RideRequest ride = getById(rideRequestId);
        if (ride.getStatus() != RideRequest.RideStatus.IN_PROGRESS) {
            throw new IllegalStateException("Ride must be IN_PROGRESS to complete!");
        }

        ride.setStatus(RideRequest.RideStatus.COMPLETED);
        RideRequest saved = rideRequestRepository.save(ride);

        // Release Escrow Payment if exists
        paymentRepository.findByRideRequestId(rideRequestId).ifPresent(payment -> {
            payment.setStatus(Payment.EscrowStatus.RELEASED);
            paymentRepository.save(payment);
        });

        // Resolve any active safety alerts for this ride
        List<SafetyAlert> alerts = safetyAlertRepository.findByRideRequestId(rideRequestId);
        for (SafetyAlert alert : alerts) {
            if (alert.getStatus() != SafetyAlert.AlertStatus.RESOLVED) {
                alert.setStatus(SafetyAlert.AlertStatus.RESOLVED);
                alert.setResolvedAt(LocalDateTime.now());
                safetyAlertRepository.save(alert);
            }
        }

        // Broadcast to WebSocket subscribers
        try {
            messagingTemplate.convertAndSend("/topic/trip/" + ride.getTripId(), saved);
        } catch (Exception e) {
            System.err.println("Failed to broadcast WebSocket update on completeRide: " + e.getMessage());
        }

        // Trigger FCM push notification to rider
        try {
            notificationService.sendPushToUser(
                    ride.getRiderId(),
                    "Ride Completed",
                    "You have arrived at your destination.",
                    Map.of("type", "RIDE_COMPLETED", "rideId", String.valueOf(rideRequestId))
            );
        } catch (Exception e) {
            System.err.println("Failed to send FCM push on completeRide: " + e.getMessage());
        }

        return saved;
    }

    @Transactional
    public com.example.project.blabla_porter.dto.RazorpayOrderResponse createRazorpayOrder(Long rideRequestId, Long riderId) {
        RideRequest ride = getById(rideRequestId);
        if (!ride.getRiderId().equals(riderId)) {
            throw new IllegalArgumentException("Only the rider can pay for this ride request!");
        }
        if (ride.getStatus() != RideRequest.RideStatus.REQUESTED) {
            throw new IllegalStateException("Ride request must be in REQUESTED status before payment!");
        }

        Trip trip = tripRepository.findById(ride.getTripId())
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        if (trip.getAvailableSeats() == null || trip.getAvailableSeats() <= 0) {
            throw new IllegalStateException("No available seats left on this trip!");
        }

        User rider = userRepository.findById(riderId)
                .orElseThrow(() -> new RuntimeException("Rider not found"));

        double fare = ride.getCalculatedFare();
        String orderId;
        String keyIdToUse = (razorpayKeyId == null || razorpayKeyId.isBlank()) ? "rzp_test_mockkey12345" : razorpayKeyId;

        if (razorpayKeyId == null || razorpayKeyId.isBlank() || razorpayKeySecret == null || razorpayKeySecret.isBlank()) {
            // Mock Mode fallback
            orderId = "order_mock_" + System.currentTimeMillis() + "_" + RANDOM.nextInt(1000);
            System.out.println("Razorpay API keys not configured. Operating in MOCK mode. Generated Order ID: " + orderId);
        } else {
            // Real Razorpay integration
            try {
                com.razorpay.RazorpayClient client = new com.razorpay.RazorpayClient(razorpayKeyId, razorpayKeySecret);
                org.json.JSONObject orderRequest = new org.json.JSONObject();
                orderRequest.put("amount", (int) Math.round(fare * 100)); // amount in paise
                orderRequest.put("currency", "INR");
                orderRequest.put("receipt", "receipt_ride_" + rideRequestId);
                
                com.razorpay.Order order = client.orders.create(orderRequest);
                orderId = order.get("id");
            } catch (Exception e) {
                throw new RuntimeException("Failed to create Razorpay Order: " + e.getMessage(), e);
            }
        }

        return com.example.project.blabla_porter.dto.RazorpayOrderResponse.builder()
                .keyId(keyIdToUse)
                .orderId(orderId)
                .amount(fare)
                .currency("INR")
                .goodsDescription("Inter-City Co-Ride Booking")
                .senderName(rider.getFullName())
                .senderMobile(rider.getMobileNumber())
                .build();
    }

    @Transactional
    public Payment verifyRazorpayPayment(Long rideRequestId, com.example.project.blabla_porter.dto.RazorpayVerifyRequest req) {
        RideRequest ride = getById(rideRequestId);
        if (!ride.getRiderId().equals(req.getSenderId())) {
            throw new IllegalArgumentException("Only the rider can verify this payment!");
        }
        if (ride.getStatus() != RideRequest.RideStatus.REQUESTED) {
            throw new IllegalStateException("Ride request must be in REQUESTED status to complete payment!");
        }

        Trip trip = tripRepository.findByIdForUpdate(ride.getTripId())
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        if (trip.getAvailableSeats() == null || trip.getAvailableSeats() <= 0) {
            throw new IllegalStateException("No available seats left on this trip!");
        }

        boolean isMock = req.getRazorpayOrderId() != null && req.getRazorpayOrderId().startsWith("order_mock_");

        if (!isMock && razorpayKeyId != null && !razorpayKeyId.isBlank() && razorpayKeySecret != null && !razorpayKeySecret.isBlank()) {
            // Verify payment signature
            try {
                org.json.JSONObject options = new org.json.JSONObject();
                options.put("razorpay_order_id", req.getRazorpayOrderId());
                options.put("razorpay_payment_id", req.getRazorpayPaymentId());
                options.put("razorpay_signature", req.getRazorpaySignature());

                boolean isSignatureValid = com.razorpay.Utils.verifyPaymentSignature(options, razorpayKeySecret);
                if (!isSignatureValid) {
                    throw new RuntimeException("Razorpay payment signature validation failed!");
                }
            } catch (Exception e) {
                throw new RuntimeException("Razorpay signature validation failed: " + e.getMessage(), e);
            }
        } else {
            System.out.println("Skipping signature verification due to Mock Mode / local testing: " + req.getRazorpayOrderId());
        }

        // Decrement availableSeats on the trip
        trip.setAvailableSeats(trip.getAvailableSeats() - 1);
        tripRepository.save(trip);

        ride.setStatus(RideRequest.RideStatus.ACCEPTED);
        ride.setRazorpayOrderId(req.getRazorpayOrderId());
        ride.setRazorpayPaymentId(req.getRazorpayPaymentId());
        ride.setRazorpaySignature(req.getRazorpaySignature());
        RideRequest savedRide = rideRequestRepository.save(ride);

        Payment payment = Payment.builder()
                .rideRequestId(rideRequestId)
                .riderId(req.getSenderId())
                .travelerId(trip.getTravelerId())
                .amount(ride.getCalculatedFare())
                .status(Payment.EscrowStatus.HELD)
                .razorpayOrderId(req.getRazorpayOrderId())
                .razorpayPaymentId(req.getRazorpayPaymentId())
                .razorpaySignature(req.getRazorpaySignature())
                .build();

        Payment savedPayment = paymentRepository.save(payment);

        // Broadcast updated ride state to WebSocket subscribers
        try {
            messagingTemplate.convertAndSend("/topic/trip/" + ride.getTripId(), savedRide);
        } catch (Exception e) {
            System.err.println("Failed to broadcast WebSocket update on verifyRazorpayPayment: " + e.getMessage());
        }

        // Trigger FCM push notification to traveler (Captain)
        try {
            User rider = userRepository.findById(req.getSenderId()).orElse(null);
            String riderName = rider != null ? rider.getFullName() : "A rider";
            notificationService.sendPushToUser(
                    trip.getTravelerId(),
                    "Ride Booking Confirmed & Paid",
                    "Rider " + riderName + " has confirmed and paid for Ride #" + rideRequestId + ".",
                    Map.of("type", "PAYMENT_CONFIRMED", "rideId", String.valueOf(rideRequestId))
            );
        } catch (Exception e) {
            System.err.println("Failed to send FCM push on verifyRazorpayPayment: " + e.getMessage());
        }

        return savedPayment;
    }

    @Transactional
    public RideRequest cancelAndRefundRide(Long rideRequestId, Long userId) {
        RideRequest ride = getById(rideRequestId);
        if (ride.getStatus() == RideRequest.RideStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel a completed ride!");
        }

        if (userId.equals(ride.getRiderId())) {
            ride.setStatus(RideRequest.RideStatus.CANCELLED);
            RideRequest saved = rideRequestRepository.save(ride);

            // Increment available seats back on the trip
            tripRepository.findByIdForUpdate(ride.getTripId()).ifPresent(trip -> {
                if (trip.getAvailableSeats() != null) {
                    trip.setAvailableSeats(trip.getAvailableSeats() + 1);
                    tripRepository.save(trip);
                }
            });

            paymentRepository.findByRideRequestId(rideRequestId).ifPresent(payment -> {
                if (payment.getStatus() == Payment.EscrowStatus.HELD) {
                    payment.setStatus(Payment.EscrowStatus.REFUNDED);
                    paymentRepository.save(payment);
                }
            });

            return saved;
        } else {
            throw new IllegalArgumentException("Only the rider can cancel and request refund for this ride!");
        }
    }

    @Transactional
    public SafetyAlert triggerSafetyEscalation(Long rideRequestId, String lastKnownLocation, SafetyAlert.EscalationStage stage) {
        RideRequest ride = getById(rideRequestId);
        if (!ride.getSafetyModeEnabled()) {
            throw new IllegalStateException("Safety Mode is not enabled for this ride request!");
        }

        List<TrustedContact> contacts = trustedContactRepository.findByUserId(ride.getRiderId());

        String reason = "Timer buffer (" + ride.getBufferMinutes() + " mins) exceeded without location update";
        if (stage == SafetyAlert.EscalationStage.STAGE_3_TRUSTED_CONTACT_ALERT && contacts.isEmpty()) {
            reason += " (Warning: No trusted contacts registered for rider)";
        }

        SafetyAlert alert = SafetyAlert.builder()
                .rideRequestId(rideRequestId)
                .riderId(ride.getRiderId())
                .lastKnownLocation(lastKnownLocation != null ? lastKnownLocation : ride.getPickupLocation())
                .triggerReason(reason)
                .escalationStage(stage)
                .status(stage == SafetyAlert.EscalationStage.STAGE_3_TRUSTED_CONTACT_ALERT ? SafetyAlert.AlertStatus.ESCALATED : SafetyAlert.AlertStatus.TRIGGERED)
                .build();

        SafetyAlert savedAlert = safetyAlertRepository.save(alert);

        if (stage == SafetyAlert.EscalationStage.STAGE_3_TRUSTED_CONTACT_ALERT) {
            for (TrustedContact contact : contacts) {
                try {
                    String msg = "EMERGENCY: Rider safety check-in failed or timer buffer exceeded. Ride #" + rideRequestId + 
                                 ". Last known location: " + savedAlert.getLastKnownLocation();
                    smsService.sendSms(contact.getContactPhoneNumber(), msg);
                    System.out.println("Emergency SMS sent successfully to " + contact.getContactPhoneNumber());
                } catch (Exception e) {
                    System.err.println("Failed to send emergency SMS to " + contact.getContactPhoneNumber() + ": " + e.getMessage());
                }
            }
        }

        return savedAlert;
    }

    public SafetyAlert acknowledgeCheckin(Long alertId, boolean isSafe) {
        SafetyAlert alert = safetyAlertRepository.findById(alertId)
                .orElseThrow(() -> new RuntimeException("Safety alert not found with id: " + alertId));

        if (isSafe) {
            alert.setStatus(SafetyAlert.AlertStatus.RESOLVED);
            alert.setResolvedAt(LocalDateTime.now());
        } else {
            alert.setStatus(SafetyAlert.AlertStatus.ESCALATED);
            alert.setEscalationStage(SafetyAlert.EscalationStage.STAGE_3_TRUSTED_CONTACT_ALERT);
        }

        return safetyAlertRepository.save(alert);
    }

    public RideRequest getById(Long id) {
        return rideRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ride request not found with id: " + id));
    }

    public List<RideRequest> getRidesByRider(Long riderId) {
        return rideRequestRepository.findByRiderId(riderId);
    }

    public List<RideRequest> getRidesByTrip(Long tripId) {
        return rideRequestRepository.findByTripId(tripId);
    }
}
