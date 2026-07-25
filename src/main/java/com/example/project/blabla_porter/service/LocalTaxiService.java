package com.example.project.blabla_porter.service;

import com.example.project.blabla_porter.model.*;
import com.example.project.blabla_porter.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Map;
import java.util.Optional;

@Service
public class LocalTaxiService {

    @Autowired
    private LocalCaptainStatusRepository captainStatusRepository;

    @Autowired
    private LocalTaxiBookingRepository taxiBookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RideRequestRepository rideRequestRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private NotificationService notificationService;

    @Transactional
    public LocalCaptainStatus toggleAvailability(Long captainId, boolean available, Double latitude, Double longitude) {
        User user = userRepository.findById(captainId)
                .orElseThrow(() -> new RuntimeException("Captain not found"));
        if (!user.getCapabilities().contains(User.UserRole.TRAVELER)) {
            throw new IllegalArgumentException("Only Captains can toggle local availability");
        }

        LocalCaptainStatus status = captainStatusRepository.findByCaptainId(captainId)
                .orElse(LocalCaptainStatus.builder().captainId(captainId).build());

        status.setAvailable(available);
        status.setCurrentLatitude(latitude);
        status.setCurrentLongitude(longitude);
        status.setLastActiveTime(LocalDateTime.now());

        return captainStatusRepository.save(status);
    }

    public Optional<LocalCaptainStatus> getCaptainStatus(Long captainId) {
        return captainStatusRepository.findByCaptainId(captainId);
    }

    public Optional<LocalTaxiBooking> getBooking(Long id) {
        return taxiBookingRepository.findById(id);
    }

    @Transactional
    public LocalTaxiBooking bookTaxi(Long riderId, String pickupLocation, double pickupLat, double pickupLng,
                                     String dropoffLocation, double dropoffLat, double dropoffLng, boolean safetyModeEnabled) {
        
        // Match closest Captain
        List<LocalCaptainStatus> availableCaptains = captainStatusRepository.findByAvailableTrue();
        if (availableCaptains.isEmpty()) {
            throw new RuntimeException("No active Captains are available near your pickup area.");
        }

        LocalCaptainStatus nearestCaptain = null;
        double minDistance = Double.MAX_VALUE;

        for (LocalCaptainStatus cap : availableCaptains) {
            if (cap.getCurrentLatitude() == null || cap.getCurrentLongitude() == null) continue;
            double dist = calculateDistance(pickupLat, pickupLng, cap.getCurrentLatitude(), cap.getCurrentLongitude());
            if (dist < minDistance && dist <= 30.0) { // 30 Km search radius
                minDistance = dist;
                nearestCaptain = cap;
            }
        }

        if (nearestCaptain == null) {
            throw new RuntimeException("No active Captains are available near your pickup area.");
        }

        // Fare Calculation
        double distance = calculateDistance(pickupLat, pickupLng, dropoffLat, dropoffLng);
        double estDurationMinutes = distance * 3.0; // 3 mins per Km estimation

        double baseFare = 20.0;
        double distanceFare = 0.0;
        if (distance > 2.0) {
            distanceFare = (distance - 2.0) * 10.0;
        }
        double durationFare = estDurationMinutes * 1.00;
        double platformFee = 5.0;
        double totalFare = baseFare + distanceFare + durationFare + platformFee;

        LocalTaxiBooking booking = LocalTaxiBooking.builder()
                .riderId(riderId)
                .captainId(nearestCaptain.getCaptainId())
                .pickupLocation(pickupLocation)
                .dropoffLocation(dropoffLocation)
                .pickupLatitude(pickupLat)
                .pickupLongitude(pickupLng)
                .dropoffLatitude(dropoffLat)
                .dropoffLongitude(dropoffLng)
                .calculatedFare(totalFare)
                .status(LocalTaxiBookingStatus.REQUESTED)
                .safetyModeEnabled(safetyModeEnabled)
                .createdAt(LocalDateTime.now())
                .build();

        LocalTaxiBooking saved = taxiBookingRepository.save(booking);

        // Trigger FCM push notification to the nearest Captain
        try {
            notificationService.sendPushToUser(
                    nearestCaptain.getCaptainId(),
                    "New Local Taxi Request",
                    "You have a new local taxi booking request from " + pickupLocation + " to " + dropoffLocation + ".",
                    Map.of("type", "NEW_TAXI_REQUEST", "bookingId", String.valueOf(saved.getId()))
            );
        } catch (Exception e) {
            System.err.println("Failed to send FCM push to captain in bookTaxi: " + e.getMessage());
        }

        return saved;
    }

    @Transactional
    public Map<String, Object> createPaymentOrder(Long bookingId, Long riderId) {
        LocalTaxiBooking booking = taxiBookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Taxi booking not found"));

        if (!booking.getRiderId().equals(riderId)) {
            throw new IllegalArgumentException("Unauthorized payment initialization");
        }

        String orderId = "order_taxi_mock_" + System.currentTimeMillis() + "_" + bookingId;

        // Save order ID to booking
        booking.setRazorpayOrderId(orderId);
        taxiBookingRepository.save(booking);

        User rider = userRepository.findById(riderId)
                .orElseThrow(() -> new RuntimeException("Rider not found"));

        return Map.of(
                "orderId", orderId,
                "amount", booking.getCalculatedFare(),
                "currency", "INR",
                "keyId", "rzp_test_mockkey_987654",
                "goodsDescription", "Same-City Ride Escrow: " + booking.getPickupLocation() + " to " + booking.getDropoffLocation(),
                "senderName", rider.getFullName(),
                "senderMobile", rider.getMobileNumber()
        );
    }

    @Transactional
    public Payment verifyPayment(Long bookingId, String razorpayOrderId, String razorpayPaymentId, String razorpaySignature) {
        LocalTaxiBooking booking = taxiBookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Taxi booking not found"));

        booking.setRazorpayOrderId(razorpayOrderId);
        booking.setRazorpayPaymentId(razorpayPaymentId);
        booking.setRazorpaySignature(razorpaySignature);
        booking.setStatus(LocalTaxiBookingStatus.PAID);

        // Turn Captain unavailable for new matching
        captainStatusRepository.findByCaptainId(booking.getCaptainId()).ifPresent(cap -> {
            cap.setAvailable(false);
            captainStatusRepository.save(cap);
        });

        // Initialize active tracking Trip
        Trip trip = Trip.builder()
                .travelerId(booking.getCaptainId())
                .source(booking.getPickupLocation())
                .destination(booking.getDropoffLocation())
                .availableCapacityKg(0.0)
                .availableSeats(0)
                .status(Trip.TripStatus.ACTIVE)
                .departureTime(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();
        trip = tripRepository.save(trip);
        booking.setTripId(trip.getId());

        // Create corresponding RideRequest to support the RIDER safety mode features
        RideRequest ride = RideRequest.builder()
                .riderId(booking.getRiderId())
                .tripId(trip.getId())
                .pickupLocation(booking.getPickupLocation())
                .dropoffLocation(booking.getDropoffLocation())
                .calculatedFare(booking.getCalculatedFare())
                .safetyModeEnabled(booking.isSafetyModeEnabled())
                .status(RideRequest.RideStatus.ACCEPTED)
                .pickupLatitude(booking.getPickupLatitude())
                .pickupLongitude(booking.getPickupLongitude())
                .dropoffLatitude(booking.getDropoffLatitude())
                .dropoffLongitude(booking.getDropoffLongitude())
                .estimatedDurationMinutes(30)
                .bufferMinutes(5)
                .createdAt(LocalDateTime.now())
                .build();
        rideRequestRepository.save(ride);

        taxiBookingRepository.save(booking);

        Payment payment = Payment.builder()
                .localTaxiBookingId(bookingId)
                .riderId(booking.getRiderId())
                .travelerId(booking.getCaptainId())
                .amount(booking.getCalculatedFare())
                .status(Payment.EscrowStatus.HELD)
                .razorpayOrderId(razorpayOrderId)
                .razorpayPaymentId(razorpayPaymentId)
                .razorpaySignature(razorpaySignature)
                .build();

        return paymentRepository.save(payment);
    }

    @Transactional
    public LocalTaxiBooking updateBookingStatus(Long bookingId, Long userId, LocalTaxiBookingStatus newStatus) {
        LocalTaxiBooking booking = taxiBookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Taxi booking not found"));

        // Auth check
        if (!booking.getRiderId().equals(userId) && !booking.getCaptainId().equals(userId)) {
            throw new IllegalArgumentException("Unauthorized status transition");
        }

        booking.setStatus(newStatus);

        // Handle side-effects of transitions
        if (newStatus == LocalTaxiBookingStatus.IN_PROGRESS) {
            if (booking.getTripId() != null) {
                tripRepository.findById(booking.getTripId()).ifPresent(t -> {
                    t.setStatus(Trip.TripStatus.ACTIVE);
                    tripRepository.save(t);
                });
                rideRequestRepository.findByTripId(booking.getTripId()).forEach(r -> {
                    r.setStatus(RideRequest.RideStatus.IN_PROGRESS);
                    rideRequestRepository.save(r);
                });
            }
        } else if (newStatus == LocalTaxiBookingStatus.COMPLETED) {
            // Free Captain for new rides
            captainStatusRepository.findByCaptainId(booking.getCaptainId()).ifPresent(cap -> {
                cap.setAvailable(true);
                captainStatusRepository.save(cap);
            });

            // Complete trip
            if (booking.getTripId() != null) {
                tripRepository.findById(booking.getTripId()).ifPresent(t -> {
                    t.setStatus(Trip.TripStatus.COMPLETED);
                    tripRepository.save(t);
                });
                rideRequestRepository.findByTripId(booking.getTripId()).forEach(r -> {
                    r.setStatus(RideRequest.RideStatus.COMPLETED);
                    rideRequestRepository.save(r);
                });
            }

            // Release payment
            List<Payment> payments = paymentRepository.findAll().stream()
                    .filter(p -> bookingId.equals(p.getLocalTaxiBookingId()))
                    .toList();
            for (Payment p : payments) {
                p.setStatus(Payment.EscrowStatus.RELEASED);
                paymentRepository.save(p);
            }
        } else if (newStatus == LocalTaxiBookingStatus.CANCELLED) {
            // Free Captain
            captainStatusRepository.findByCaptainId(booking.getCaptainId()).ifPresent(cap -> {
                cap.setAvailable(true);
                captainStatusRepository.save(cap);
            });

            // Cancel trip
            if (booking.getTripId() != null) {
                tripRepository.findById(booking.getTripId()).ifPresent(t -> {
                    t.setStatus(Trip.TripStatus.CANCELLED);
                    tripRepository.save(t);
                });
                rideRequestRepository.findByTripId(booking.getTripId()).forEach(r -> {
                    r.setStatus(RideRequest.RideStatus.CANCELLED);
                    rideRequestRepository.save(r);
                });
            }

            // Refund payment
            List<Payment> payments = paymentRepository.findAll().stream()
                    .filter(p -> bookingId.equals(p.getLocalTaxiBookingId()))
                    .toList();
            for (Payment p : payments) {
                p.setStatus(Payment.EscrowStatus.REFUNDED);
                paymentRepository.save(p);
            }
        }

        LocalTaxiBooking saved = taxiBookingRepository.save(booking);

        // Broadcast status update
        if (saved.getTripId() != null) {
            try {
                messagingTemplate.convertAndSend("/topic/trip/" + saved.getTripId(), saved);
            } catch (Exception e) {
                System.err.println("Failed to broadcast WebSocket update on updateBookingStatus: " + e.getMessage());
            }
        }

        // Trigger FCM push notification to rider
        try {
            String title = "Local Taxi Booking Update";
            String body = "";
            if (newStatus == LocalTaxiBookingStatus.IN_PROGRESS) {
                body = "Your taxi ride has started! Enjoy the trip.";
            } else if (newStatus == LocalTaxiBookingStatus.COMPLETED) {
                body = "Your taxi ride is completed. Thank you for using BlaBla+Porter!";
            } else if (newStatus == LocalTaxiBookingStatus.CANCELLED) {
                body = "Your taxi ride has been cancelled.";
            }

            if (!body.isEmpty()) {
                notificationService.sendPushToUser(
                        saved.getRiderId(),
                        title,
                        body,
                        Map.of("type", "TAXI_STATUS", "bookingId", String.valueOf(bookingId), "status", newStatus.name())
                );
            }
        } catch (Exception e) {
            System.err.println("Failed to send FCM push on updateBookingStatus: " + e.getMessage());
        }

        return saved;
    }

    public List<LocalTaxiBooking> getRiderBookings(Long riderId) {
        return taxiBookingRepository.findByRiderId(riderId);
    }

    public List<LocalTaxiBooking> getCaptainBookings(Long captainId) {
        return taxiBookingRepository.findByCaptainId(captainId);
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Rad of earth
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
