package com.example.project.blabla_porter;

import com.example.project.blabla_porter.controller.PaymentWebhookController;
import com.example.project.blabla_porter.dto.RideBookingRequest;
import com.example.project.blabla_porter.model.Payment;
import com.example.project.blabla_porter.model.RideRequest;
import com.example.project.blabla_porter.model.Trip;
import com.example.project.blabla_porter.model.User;
import com.example.project.blabla_porter.repository.PaymentRepository;
import com.example.project.blabla_porter.repository.RideRequestRepository;
import com.example.project.blabla_porter.repository.TripRepository;
import com.example.project.blabla_porter.repository.UserRepository;
import com.example.project.blabla_porter.service.PaymentReconciliationScheduler;
import com.example.project.blabla_porter.service.RideService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.example.project.Application.class)
@TestPropertySource(properties = {
    "blabla.seeder.enabled=false",
    "spring.main.allow-bean-definition-overriding=true",
    "spring.datasource.url=jdbc:h2:mem:reconcile_test_db;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
public class PaymentAsynchronousReconciliationTest {

    @Autowired
    private PaymentWebhookController webhookController;

    @Autowired
    private PaymentReconciliationScheduler reconciliationScheduler;

    @Autowired
    private RideService rideService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private RideRequestRepository rideRequestRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    private User traveler;
    private User rider;
    private Trip trip;

    @BeforeEach
    public void setup() {
        paymentRepository.deleteAll();
        rideRequestRepository.deleteAll();
        tripRepository.deleteAll();
        userRepository.deleteAll();

        traveler = userRepository.save(User.builder()
                .fullName("Traveler Bob")
                .mobileNumber("9876543201")
                .role(User.UserRole.TRAVELER)
                .kycStatus(User.KycStatus.APPROVED)
                .build());

        rider = userRepository.save(User.builder()
                .fullName("Rider Alice")
                .mobileNumber("9876543202")
                .role(User.UserRole.RIDER)
                .kycStatus(User.KycStatus.NOT_SUBMITTED)
                .build());

        trip = tripRepository.save(Trip.builder()
                .travelerId(traveler.getId())
                .source("Bengaluru")
                .destination("Chennai")
                .departureTime(LocalDateTime.now().plusDays(1))
                .availableSeats(1) // Only 1 available seat
                .availableCapacityKg(10.0)
                .status(Trip.TripStatus.PLANNED)
                .build());
    }

    @AfterEach
    public void tearDown() {
        paymentRepository.deleteAll();
        rideRequestRepository.deleteAll();
        tripRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    public void testWebhookAsynchronousProcessingCrashScenario() {
        // 1. Create a ride booking request (stored in status REQUESTED)
        RideBookingRequest rideReq = new RideBookingRequest();
        rideReq.setRiderId(rider.getId());
        rideReq.setTripId(trip.getId());
        rideReq.setPickupLocation("Koramangala");
        rideReq.setDropoffLocation("Domlur");
        
        RideRequest ride = rideService.requestRide(rideReq);
        assertNotNull(ride);
        assertEquals(RideRequest.RideStatus.REQUESTED, ride.getStatus());

        // Create the mock Razorpay order ID
        String mockOrderId = "order_mock_webhook_test_999";
        ride.setRazorpayOrderId(mockOrderId);
        rideRequestRepository.save(ride);

        // 2. Simulate Client Crash: Payment succeeds at Razorpay, but client never calls verify endpoint.
        // Instead, Razorpay dispatches the payment.captured webhook to our webhook controller.
        String webhookPayload = "{\n" +
                "  \"event\": \"payment.captured\",\n" +
                "  \"payload\": {\n" +
                "    \"payment\": {\n" +
                "      \"entity\": {\n" +
                "        \"id\": \"pay_captured_webhook_test_123\",\n" +
                "        \"order_id\": \"" + mockOrderId + "\",\n" +
                "        \"status\": \"captured\"\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";

        ResponseEntity<Map<String, String>> response = webhookController.handleWebhook(
                "sig_webhook_test_sig",
                webhookPayload
        );

        // 3. Verify webhook successfully processed the payment and reconciled the ride request status
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("success", response.getBody().get("status"));
        assertEquals("ride", response.getBody().get("type"));

        // Fetch refreshed ride and trip from database
        RideRequest refreshedRide = rideRequestRepository.findById(ride.getId()).orElseThrow();
        assertEquals(RideRequest.RideStatus.ACCEPTED, refreshedRide.getStatus(), 
                "Ride request should be updated to ACCEPTED by the webhook");

        Trip refreshedTrip = tripRepository.findById(trip.getId()).orElseThrow();
        assertEquals(0, refreshedTrip.getAvailableSeats(), 
                "Trip available seats must be decremented to 0");

        List<Payment> payments = paymentRepository.findAll();
        assertEquals(1, payments.size(), "A payment record must be created");
        assertEquals(Payment.EscrowStatus.HELD, payments.get(0).getStatus(), "Payment status must be HELD");
    }

    @Test
    public void testReconciliationCronJobCrashScenario() {
        // 1. Create a ride request in status REQUESTED
        RideBookingRequest rideReq = new RideBookingRequest();
        rideReq.setRiderId(rider.getId());
        rideReq.setTripId(trip.getId());
        rideReq.setPickupLocation("Koramangala");
        rideReq.setDropoffLocation("Domlur");
        
        RideRequest ride = rideService.requestRide(rideReq);
        assertNotNull(ride);
        assertEquals(RideRequest.RideStatus.REQUESTED, ride.getStatus());

        // Assign a mock reconciliation order ID that is recognized as paid by reconciliation job
        String mockReconcileOrderId = "order_mock_reconcile_999";
        ride.setRazorpayOrderId(mockReconcileOrderId);
        rideRequestRepository.save(ride);

        // 2. Trigger payment reconciliation scheduler to scan pending requests
        reconciliationScheduler.reconcilePayments();

        // 3. Assert the reconciliation scheduler corrected the request status
        RideRequest refreshedRide = rideRequestRepository.findById(ride.getId()).orElseThrow();
        assertEquals(RideRequest.RideStatus.ACCEPTED, refreshedRide.getStatus(), 
                "Ride request should be reconciled to ACCEPTED status");

        Trip refreshedTrip = tripRepository.findById(trip.getId()).orElseThrow();
        assertEquals(0, refreshedTrip.getAvailableSeats(), 
                "Trip available seats must be reconciled to 0");

        List<Payment> payments = paymentRepository.findAll();
        assertEquals(1, payments.size(), "A reconciled payment record must be created");
    }
}
