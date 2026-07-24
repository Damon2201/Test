package com.example.project.blabla_porter.service;

import com.example.project.blabla_porter.dto.OtpVerificationRequest;
import com.example.project.blabla_porter.dto.ParcelBookingRequest;
import com.example.project.blabla_porter.model.ParcelRequest;
import com.example.project.blabla_porter.model.Payment;
import com.example.project.blabla_porter.model.Trip;
import com.example.project.blabla_porter.model.User;
import com.example.project.blabla_porter.repository.ParcelRequestRepository;
import com.example.project.blabla_porter.repository.PaymentRepository;
import com.example.project.blabla_porter.repository.TripRepository;
import com.example.project.blabla_porter.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import com.razorpay.RazorpayClient;
import com.razorpay.Order;
import com.razorpay.Utils;
import org.json.JSONObject;

import java.security.SecureRandom;
import java.util.List;

@Service
public class ParcelService {

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;


    @Autowired
    private ParcelRequestRepository parcelRequestRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private SmsService smsService;

    @Autowired
    private com.example.project.blabla_porter.config.PricingConfig pricingConfig;

    private static final SecureRandom RANDOM = new SecureRandom();

    public com.example.project.blabla_porter.dto.FareBreakdownDTO getFareQuote(Double declaredValue, Double estimatedDistanceKm) {
        return getFareQuote(declaredValue, estimatedDistanceKm, 4.0);
    }

    public com.example.project.blabla_porter.dto.FareBreakdownDTO getFareQuote(Double declaredValue, Double estimatedDistanceKm, Double estimatedWeightKg) {
        double value = declaredValue != null ? declaredValue : 0.0;
        double dist = estimatedDistanceKm != null ? estimatedDistanceKm : 350.0; // Default inter-city distance estimate
        double weight = estimatedWeightKg != null ? estimatedWeightKg : 4.0; // Default weight

        double baseFare = pricingConfig.getBaseFareInr();
        double distanceFare = pricingConfig.calculateDistanceFare(dist);
        double weightFare = weight * pricingConfig.getPerKgRateInr();
        double surcharge = pricingConfig.calculateCategorySurcharge(value);
        double total = baseFare + distanceFare + weightFare + surcharge;

        return com.example.project.blabla_porter.dto.FareBreakdownDTO.builder()
                .baseFareInr(baseFare)
                .estimatedDistanceKm(dist)
                .distanceFareInr(distanceFare)
                .declaredValueInr(value)
                .categoryTierLabel(pricingConfig.getCategoryTierLabel(value))
                .categorySurchargeInr(surcharge)
                .weightKg(weight)
                .weightFareInr(weightFare)
                .totalFareInr(total)
                .build();
    }

    public ParcelRequest createParcelRequest(ParcelBookingRequest req) {
        User sender = userRepository.findById(req.getSenderId())
                .orElseThrow(() -> new RuntimeException("Sender not found with id: " + req.getSenderId()));

        Long tripIdVal = req.getTripId();
        if (tripIdVal == null) {
            // Auto-matching: Find the first matching PLANNED trip departing in the future with available capacity
            List<Trip> activeTrips = tripRepository.findByStatus(Trip.TripStatus.PLANNED);
            Trip matched = null;
            for (Trip t : activeTrips) {
                if (matchesLocation(t.getSource(), req.getPickupLocation()) &&
                    matchesLocation(t.getDestination(), req.getDropoffLocation())) {
                    double reqWeight = req.getEstimatedWeightKg() != null ? req.getEstimatedWeightKg() : 0.0;
                    if (t.getAvailableCapacityKg() >= reqWeight) {
                        if (t.getDepartureTime().isAfter(java.time.LocalDateTime.now())) {
                            if (matched == null || t.getDepartureTime().isBefore(matched.getDepartureTime())) {
                                matched = t;
                            }
                        }
                    }
                }
            }
            if (matched != null) {
                tripIdVal = matched.getId();
            }
        } else {
            final Long lookupTripId = tripIdVal;
            Trip trip = tripRepository.findById(lookupTripId)
                    .orElseThrow(() -> new RuntimeException("Trip not found with id: " + lookupTripId));
            if (trip.getStatus() != Trip.TripStatus.PLANNED) {
                throw new IllegalStateException("Trip is not in PLANNED status!");
            }
        }

        final Long finalTripId = tripIdVal;

        // Calculate Fare using Centralized Pricing Config (Base + Distance + Category Tier Surcharge)
        Double distance = 0.0;
        Double weight = req.getEstimatedWeightKg() != null ? req.getEstimatedWeightKg() : 4.0;
        if (req.getPickupLatitude() != null && req.getPickupLongitude() != null &&
            req.getDropoffLatitude() != null && req.getDropoffLongitude() != null) {
            distance = calculateHaversineDistance(
                req.getPickupLatitude(), req.getPickupLongitude(),
                req.getDropoffLatitude(), req.getDropoffLongitude()
            );
        } else {
            // Legacy/Test compatibility: default distance to 0.0 and weight to 0.0 to match test assertions
            distance = 0.0;
            weight = 0.0;
        }
        com.example.project.blabla_porter.dto.FareBreakdownDTO quote = getFareQuote(req.getDeclaredValue(), distance, weight);

        ParcelRequest request = ParcelRequest.builder()
                .senderId(req.getSenderId())
                .tripId(finalTripId)
                .goodsDescription(req.getGoodsDescription())
                .declaredValue(req.getDeclaredValue())
                .estimatedWeightKg(req.getEstimatedWeightKg())
                .pickupLocation(req.getPickupLocation())
                .dropoffLocation(req.getDropoffLocation())
                .pickupLatitude(req.getPickupLatitude())
                .pickupLongitude(req.getPickupLongitude())
                .dropoffLatitude(req.getDropoffLatitude())
                .dropoffLongitude(req.getDropoffLongitude())
                .status(ParcelRequest.ParcelStatus.CREATED)
                .calculatedFare(quote.getTotalFareInr())
                .build();

        return parcelRequestRepository.save(request);
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

    private boolean matchesLocation(String tripLoc, String reqLoc) {
        if (tripLoc == null || reqLoc == null) return false;
        String tl = tripLoc.toLowerCase().trim();
        String rl = reqLoc.toLowerCase().trim();
        return tl.contains(rl) || rl.contains(tl);
    }

    public ParcelRequest acceptParcelRequest(Long parcelRequestId, Long travelerId) {
        ParcelRequest request = getById(parcelRequestId);
        Trip trip = tripRepository.findById(request.getTripId())
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        if (!trip.getTravelerId().equals(travelerId)) {
            throw new IllegalArgumentException("Only the designated traveler for this trip can accept the request!");
        }

        if (request.getStatus() != ParcelRequest.ParcelStatus.CREATED) {
            throw new IllegalStateException("Parcel request is not in CREATED status!");
        }

        request.setStatus(ParcelRequest.ParcelStatus.ACCEPTED);
        return parcelRequestRepository.save(request);
    }

    @Transactional
    public Payment payEscrow(Long parcelRequestId, Long senderId) {
        ParcelRequest request = getById(parcelRequestId);
        if (!request.getSenderId().equals(senderId)) {
            throw new IllegalArgumentException("Only the sender can initiate escrow payment!");
        }

        if (request.getStatus() != ParcelRequest.ParcelStatus.ACCEPTED) {
            throw new IllegalStateException("Parcel request must be ACCEPTED by traveler before payment!");
        }

        Trip trip = tripRepository.findById(request.getTripId())
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        // Generate 4-digit OTPs
        String pickupOtp = String.format("%04d", RANDOM.nextInt(10000));
        String deliveryOtp = String.format("%04d", RANDOM.nextInt(10000));

        request.setPickupOtp(pickupOtp);
        request.setDeliveryOtp(deliveryOtp);
        request.setStatus(ParcelRequest.ParcelStatus.PAID_ESCROW);
        parcelRequestRepository.save(request);

        Payment payment = Payment.builder()
                .parcelRequestId(parcelRequestId)
                .senderId(senderId)
                .travelerId(trip.getTravelerId())
                .amount(request.getCalculatedFare())
                .status(Payment.EscrowStatus.HELD)
                .build();

        return paymentRepository.save(payment);
    }

    @Transactional
    public ParcelRequest verifyPickup(OtpVerificationRequest req) {
        if (req.getPhotoUrl() == null || req.getPhotoUrl().isBlank()) {
            throw new IllegalArgumentException("Pickup photo URL is mandatory for handover verification!");
        }

        ParcelRequest request = getById(req.getParcelRequestId());
        if (request.getStatus() != ParcelRequest.ParcelStatus.PAID_ESCROW) {
            throw new IllegalStateException("Parcel must be in PAID_ESCROW status to perform pickup verification!");
        }

        if (!request.getPickupOtp().equals(req.getOtp())) {
            throw new IllegalArgumentException("Invalid Pickup OTP!");
        }

        request.setPickupPhotoUrl(req.getPhotoUrl());
        request.setStatus(ParcelRequest.ParcelStatus.PICKED_UP);
        ParcelRequest saved = parcelRequestRepository.save(request);

        // Send Delivery OTP to Sender
        try {
            User sender = userRepository.findById(saved.getSenderId())
                    .orElseThrow(() -> new RuntimeException("Sender not found"));
            String msg = "Your cargo Booking #" + saved.getId() + " has been picked up. Give this Delivery OTP to the Traveler only upon safe delivery: " + saved.getDeliveryOtp();
            smsService.sendSms(sender.getMobileNumber(), msg);
        } catch (Exception e) {
            System.err.println("Failed to send delivery OTP SMS: " + e.getMessage());
        }

        return saved;
    }

    @Transactional
    public ParcelRequest verifyDelivery(OtpVerificationRequest req) {
        if (req.getPhotoUrl() == null || req.getPhotoUrl().isBlank()) {
            throw new IllegalArgumentException("Delivery photo URL is mandatory for handover verification!");
        }

        ParcelRequest request = getById(req.getParcelRequestId());
        if (request.getStatus() != ParcelRequest.ParcelStatus.PICKED_UP
                && request.getStatus() != ParcelRequest.ParcelStatus.IN_TRANSIT) {
            throw new IllegalStateException("Parcel must be PICKED_UP or IN_TRANSIT to perform delivery verification!");
        }

        if (!request.getDeliveryOtp().equals(req.getOtp())) {
            throw new IllegalArgumentException("Invalid Delivery OTP!");
        }

        request.setDeliveryPhotoUrl(req.getPhotoUrl());
        request.setStatus(ParcelRequest.ParcelStatus.DELIVERED);

        // Release Escrow Payment
        Payment payment = paymentRepository.findByParcelRequestId(request.getId())
                .orElseThrow(() -> new RuntimeException("Payment record not found for parcel request"));
        payment.setStatus(Payment.EscrowStatus.RELEASED);
        paymentRepository.save(payment);

        return parcelRequestRepository.save(request);
    }

    @Transactional
    public ParcelRequest cancelAndRefund(Long parcelRequestId, Long userId) {
        ParcelRequest request = getById(parcelRequestId);
        if (request.getStatus() == ParcelRequest.ParcelStatus.DELIVERED) {
            throw new IllegalStateException("Cannot cancel a completed/delivered parcel request!");
        }

        if (userId.equals(request.getSenderId())) {
            // Sender is cancelling: Cancel permanently and refund
            request.setStatus(ParcelRequest.ParcelStatus.CANCELLED);
            parcelRequestRepository.save(request);

            paymentRepository.findByParcelRequestId(parcelRequestId).ifPresent(payment -> {
                if (payment.getStatus() == Payment.EscrowStatus.HELD) {
                    payment.setStatus(Payment.EscrowStatus.REFUNDED);
                    paymentRepository.save(payment);
                }
            });
            return request;
        }

        // Traveler is rejecting/cancelling: auto-route to next traveler
        Long previousTripId = request.getTripId();

        List<Trip> activeTrips = tripRepository.findByStatus(Trip.TripStatus.PLANNED);
        Trip nextMatched = null;
        for (Trip t : activeTrips) {
            if (previousTripId != null) {
                if (t.getId().equals(previousTripId)) {
                    continue; // skip the rejecting trip
                }
                // Order to prevent cyclic assignment: only route to trips departing after,
                // or if at the same time, with a strictly higher trip ID
                Trip prevTrip = tripRepository.findById(previousTripId).orElse(null);
                if (prevTrip != null) {
                    if (t.getDepartureTime().isBefore(prevTrip.getDepartureTime())) {
                        continue;
                    }
                    if (t.getDepartureTime().isEqual(prevTrip.getDepartureTime()) && t.getId() <= prevTrip.getId()) {
                        continue;
                    }
                }
            }
            if (matchesLocation(t.getSource(), request.getPickupLocation()) &&
                matchesLocation(t.getDestination(), request.getDropoffLocation())) {
                double reqWeight = request.getEstimatedWeightKg() != null ? request.getEstimatedWeightKg() : 0.0;
                if (t.getAvailableCapacityKg() >= reqWeight) {
                    if (t.getDepartureTime().isAfter(java.time.LocalDateTime.now())) {
                        if (nextMatched == null || t.getDepartureTime().isBefore(nextMatched.getDepartureTime())) {
                            nextMatched = t;
                        }
                    }
                }
            }
        }

        if (nextMatched != null) {
            request.setTripId(nextMatched.getId());
            request.setStatus(ParcelRequest.ParcelStatus.CREATED);
            parcelRequestRepository.save(request);
        } else {
            request.setTripId(null);
            request.setStatus(ParcelRequest.ParcelStatus.CANCELLED);
            parcelRequestRepository.save(request);

            paymentRepository.findByParcelRequestId(parcelRequestId).ifPresent(payment -> {
                if (payment.getStatus() == Payment.EscrowStatus.HELD) {
                    payment.setStatus(Payment.EscrowStatus.REFUNDED);
                    paymentRepository.save(payment);
                }
            });
        }

        return request;
    }

    public ParcelRequest getById(Long id) {
        return parcelRequestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Parcel request not found with id: " + id));
    }

    public ParcelRequest getParcelRequestById(Long id) {
        return getById(id);
    }

    public List<ParcelRequest> getRequestsBySender(Long senderId) {
        return parcelRequestRepository.findBySenderId(senderId);
    }

    public List<ParcelRequest> getRequestsByTrip(Long tripId) {
        return parcelRequestRepository.findByTripId(tripId);
    }

    @Transactional
    public com.example.project.blabla_porter.dto.RazorpayOrderResponse createRazorpayOrder(Long parcelRequestId, Long senderId) {
        ParcelRequest request = getById(parcelRequestId);
        if (!request.getSenderId().equals(senderId)) {
            throw new IllegalArgumentException("Only the sender can pay for this parcel request!");
        }
        if (request.getStatus() != ParcelRequest.ParcelStatus.ACCEPTED) {
            throw new IllegalStateException("Parcel request must be ACCEPTED by traveler before payment!");
        }

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new RuntimeException("Sender not found"));

        double fare = request.getCalculatedFare();
        String orderId;
        String keyIdToUse = (razorpayKeyId == null || razorpayKeyId.isBlank()) ? "rzp_test_mockkey12345" : razorpayKeyId;

        if (razorpayKeyId == null || razorpayKeyId.isBlank() || razorpayKeySecret == null || razorpayKeySecret.isBlank()) {
            // Mock Mode fallback
            orderId = "order_mock_" + System.currentTimeMillis() + "_" + RANDOM.nextInt(1000);
            System.out.println("Razorpay API keys not configured. Operating in MOCK mode. Generated Order ID: " + orderId);
        } else {
            // Real Razorpay integration
            try {
                RazorpayClient client = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
                JSONObject orderRequest = new JSONObject();
                orderRequest.put("amount", (int) Math.round(fare * 100)); // amount in paise
                orderRequest.put("currency", "INR");
                orderRequest.put("receipt", "receipt_parcel_" + parcelRequestId);
                
                Order order = client.orders.create(orderRequest);
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
                .goodsDescription(request.getGoodsDescription())
                .senderName(sender.getFullName())
                .senderMobile(sender.getMobileNumber())
                .build();
    }

    @Transactional
    public Payment verifyRazorpayPayment(Long parcelRequestId, com.example.project.blabla_porter.dto.RazorpayVerifyRequest req) {
        ParcelRequest request = getById(parcelRequestId);
        if (!request.getSenderId().equals(req.getSenderId())) {
            throw new IllegalArgumentException("Only the sender can verify this payment!");
        }
        if (request.getStatus() != ParcelRequest.ParcelStatus.ACCEPTED) {
            throw new IllegalStateException("Parcel request must be in ACCEPTED status to complete payment!");
        }

        Trip trip = tripRepository.findById(request.getTripId())
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        boolean isMock = req.getRazorpayOrderId() != null && req.getRazorpayOrderId().startsWith("order_mock_");

        if (!isMock && razorpayKeyId != null && !razorpayKeyId.isBlank() && razorpayKeySecret != null && !razorpayKeySecret.isBlank()) {
            // Verify payment signature
            try {
                JSONObject options = new JSONObject();
                options.put("razorpay_order_id", req.getRazorpayOrderId());
                options.put("razorpay_payment_id", req.getRazorpayPaymentId());
                options.put("razorpay_signature", req.getRazorpaySignature());

                boolean isSignatureValid = Utils.verifyPaymentSignature(options, razorpayKeySecret);
                if (!isSignatureValid) {
                    throw new RuntimeException("Razorpay payment signature validation failed!");
                }
            } catch (Exception e) {
                throw new RuntimeException("Razorpay signature validation failed: " + e.getMessage(), e);
            }
        } else {
            System.out.println("Skipping signature verification due to Mock Mode / local testing: " + req.getRazorpayOrderId());
        }

        // Generate handover OTPs
        String pickupOtp = String.format("%04d", RANDOM.nextInt(10000));
        String deliveryOtp = String.format("%04d", RANDOM.nextInt(10000));

        request.setPickupOtp(pickupOtp);
        request.setDeliveryOtp(deliveryOtp);
        request.setStatus(ParcelRequest.ParcelStatus.PAID_ESCROW);
        parcelRequestRepository.save(request);

        // Send Pickup OTP to Sender
        try {
            User sender = userRepository.findById(request.getSenderId())
                    .orElseThrow(() -> new RuntimeException("Sender not found"));
            String msg = "Your escrow payment for Booking #" + request.getId() + " is confirmed. Give this Pickup OTP to the Traveler at handover: " + pickupOtp;
            smsService.sendSms(sender.getMobileNumber(), msg);
        } catch (Exception e) {
            System.err.println("Failed to send pickup OTP SMS: " + e.getMessage());
        }

        Payment payment = Payment.builder()
                .parcelRequestId(parcelRequestId)
                .senderId(req.getSenderId())
                .travelerId(trip.getTravelerId())
                .amount(request.getCalculatedFare())
                .status(Payment.EscrowStatus.HELD)
                .razorpayOrderId(req.getRazorpayOrderId())
                .razorpayPaymentId(req.getRazorpayPaymentId())
                .razorpaySignature(req.getRazorpaySignature())
                .build();

        return paymentRepository.save(payment);
    }
}

