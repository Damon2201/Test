package com.example.project.blabla_porter;

import com.example.project.blabla_porter.dto.*;
import com.example.project.blabla_porter.model.*;
import com.example.project.blabla_porter.repository.PaymentRepository;
import com.example.project.blabla_porter.repository.TripRepository;
import com.example.project.blabla_porter.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.example.project.Application.class)
@TestPropertySource(properties = {
    "blabla.seeder.enabled=false",
    "spring.main.allow-bean-definition-overriding=true",
    "spring.datasource.url=jdbc:h2:mem:rider_tests_db;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=true"
})
@Transactional
public class BlaBlaPorterRiderTests {

    @Autowired
    private UserService userService;

    @Autowired
    private TripService tripService;

    @Autowired
    private RideService rideService;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    private User traveler;
    private User rider;
    private Trip trip;

    @BeforeEach
    void setUp() {
        // Register Traveler & Approve KYC
        RegisterRequest tReq = new RegisterRequest();
        tReq.setFullName("Captain Driver");
        tReq.setMobileNumber("9000000001");
        tReq.setRole(User.UserRole.TRAVELER);
        tReq.setPassword("password123");
        tReq.setAadhaarNumber("1111-2222-3333");
        tReq.setPanNumber("ABCDE1234F");
        tReq.setDrivingLicenceNumber("DL-12345");
        tReq.setRcNumber("RC-12345");
        User tUser = userService.register(tReq);
        traveler = userService.approveKyc(tUser.getId());

        // Create a Trip with 2 seats
        TripCreateRequest tripReq = new TripCreateRequest();
        tripReq.setTravelerId(traveler.getId());
        tripReq.setSource("Bengaluru");
        tripReq.setDestination("Chennai");
        tripReq.setDepartureTime(LocalDateTime.now().plusDays(1));
        tripReq.setEstimatedArrivalTime(LocalDateTime.now().plusDays(1).plusHours(6));
        tripReq.setAvailableCapacityKg(100.0);
        tripReq.setAvailableSeats(2);
        trip = tripService.createTrip(tripReq);

        // Register Rider
        RegisterRequest rReq = new RegisterRequest();
        rReq.setFullName("Passenger Rider");
        rReq.setMobileNumber("9000000002");
        rReq.setRole(User.UserRole.RIDER);
        rReq.setPassword("password123");
        rider = userService.register(rReq);
    }

    @Test
    void testRideBookingEscrowFlow() {
        // Request a ride: Bengaluru to Chennai (~290 km)
        RideBookingRequest req = new RideBookingRequest();
        req.setRiderId(rider.getId());
        req.setTripId(trip.getId());
        req.setPickupLocation("Indiranagar, Bengaluru");
        req.setDropoffLocation("Adyar, Chennai");
        // Coordinates for Bengaluru (12.9716, 77.5946) to Chennai (13.0827, 80.2707) -> ~290 km
        req.setPickupLatitude(12.9716);
        req.setPickupLongitude(77.5946);
        req.setDropoffLatitude(13.0827);
        req.setDropoffLongitude(80.2707);
        req.setSafetyModeEnabled(true);
        req.setEstimatedDurationMinutes(360);

        RideRequest ride = rideService.requestRide(req);
        assertNotNull(ride);
        assertEquals(RideRequest.RideStatus.REQUESTED, ride.getStatus());
        
        // Assert dynamic seat-fare for OSRM-snapped driving distance (~326.78 km): Base 50 + first 100km (excess of 3km is 97 * 1.5 = 145.5) + next 226.78km (226.78 * 1.0 = 226.78) = ₹422.28
        assertTrue(ride.getCalculatedFare() >= 422.0);
        assertTrue(ride.getCalculatedFare() < 423.0);

        // Initiate escrow payment order
        RazorpayOrderResponse order = rideService.createRazorpayOrder(ride.getId(), rider.getId());
        assertNotNull(order);
        assertTrue(order.getOrderId().startsWith("order_mock_"));

        // Verify payment
        RazorpayVerifyRequest verifyReq = new RazorpayVerifyRequest();
        verifyReq.setSenderId(rider.getId());
        verifyReq.setRazorpayOrderId(order.getOrderId());
        verifyReq.setRazorpayPaymentId("pay_mock_12345");
        verifyReq.setRazorpaySignature("sig_mock_12345");

        Payment payment = rideService.verifyRazorpayPayment(ride.getId(), verifyReq);
        assertNotNull(payment);
        assertEquals(Payment.EscrowStatus.HELD, payment.getStatus());
        assertEquals(ride.getCalculatedFare(), payment.getAmount());

        // Assert ride request is now ACCEPTED / PAID_ESCROW
        RideRequest acceptedRide = rideService.getById(ride.getId());
        assertEquals(RideRequest.RideStatus.ACCEPTED, acceptedRide.getStatus());

        // Assert Trip seats decremented from 2 to 1
        Trip updatedTrip = tripRepository.findById(trip.getId()).orElseThrow();
        assertEquals(1, updatedTrip.getAvailableSeats());

        // Start ride
        RideRequest inProgressRide = rideService.startRide(ride.getId());
        assertEquals(RideRequest.RideStatus.IN_PROGRESS, inProgressRide.getStatus());

        // Complete ride & verify payment released
        RideRequest completedRide = rideService.completeRide(ride.getId());
        assertEquals(RideRequest.RideStatus.COMPLETED, completedRide.getStatus());

        Payment releasedPayment = paymentRepository.findByRideRequestId(ride.getId()).orElseThrow();
        assertEquals(Payment.EscrowStatus.RELEASED, releasedPayment.getStatus());
    }

    @Test
    void testBookingFailsWhenNoSeats() {
        // Book seat 1
        RideBookingRequest req1 = new RideBookingRequest();
        req1.setRiderId(rider.getId());
        req1.setTripId(trip.getId());
        req1.setPickupLocation("A");
        req1.setDropoffLocation("B");
        RideRequest ride1 = rideService.requestRide(req1);

        RazorpayOrderResponse o1 = rideService.createRazorpayOrder(ride1.getId(), rider.getId());
        RazorpayVerifyRequest v1 = new RazorpayVerifyRequest();
        v1.setSenderId(rider.getId());
        v1.setRazorpayOrderId(o1.getOrderId());
        rideService.verifyRazorpayPayment(ride1.getId(), v1);

        // Book seat 2
        RideBookingRequest req2 = new RideBookingRequest();
        req2.setRiderId(rider.getId());
        req2.setTripId(trip.getId());
        req2.setPickupLocation("A");
        req2.setDropoffLocation("B");
        RideRequest ride2 = rideService.requestRide(req2);

        RazorpayOrderResponse o2 = rideService.createRazorpayOrder(ride2.getId(), rider.getId());
        RazorpayVerifyRequest v2 = new RazorpayVerifyRequest();
        v2.setSenderId(rider.getId());
        v2.setRazorpayOrderId(o2.getOrderId());
        rideService.verifyRazorpayPayment(ride2.getId(), v2);

        // Confirm seats count is 0
        Trip updatedTrip = tripRepository.findById(trip.getId()).orElseThrow();
        assertEquals(0, updatedTrip.getAvailableSeats());

        // Book seat 3 (should fail)
        RideBookingRequest req3 = new RideBookingRequest();
        req3.setRiderId(rider.getId());
        req3.setTripId(trip.getId());
        req3.setPickupLocation("A");
        req3.setDropoffLocation("B");
        RideRequest ride3 = rideService.requestRide(req3);

        assertThrows(IllegalStateException.class, () -> {
            rideService.createRazorpayOrder(ride3.getId(), rider.getId());
        });

        RazorpayVerifyRequest v3 = new RazorpayVerifyRequest();
        v3.setSenderId(rider.getId());
        v3.setRazorpayOrderId("order_mock_failed");
        assertThrows(IllegalStateException.class, () -> {
            rideService.verifyRazorpayPayment(ride3.getId(), v3);
        });
    }
}
