package com.example.project.blabla_porter;

import com.example.project.blabla_porter.dto.ParcelBookingRequest;
import com.example.project.blabla_porter.model.Trip;
import com.example.project.blabla_porter.model.User;
import com.example.project.blabla_porter.repository.ParcelRequestRepository;
import com.example.project.blabla_porter.repository.TripRepository;
import com.example.project.blabla_porter.repository.UserRepository;
import com.example.project.blabla_porter.service.ParcelService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.example.project.Application.class)
@TestPropertySource(properties = {
    "blabla.seeder.enabled=false",
    "spring.main.allow-bean-definition-overriding=true",
    "spring.datasource.url=jdbc:h2:mem:concurrency_test_db;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
public class ConcurrencyVerificationTest {

    @Autowired
    private ParcelService parcelService;

    @Autowired
    private com.example.project.blabla_porter.service.RideService rideService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private ParcelRequestRepository parcelRequestRepository;

    @Autowired
    private com.example.project.blabla_porter.repository.RideRequestRepository rideRequestRepository;

    private User traveler;
    private User senderA;
    private User senderB;
    private Trip trip;

    @BeforeEach
    public void setup() {
        rideRequestRepository.deleteAll();
        parcelRequestRepository.deleteAll();
        tripRepository.deleteAll();
        userRepository.deleteAll();

        traveler = userRepository.save(User.builder()
                .fullName("Traveler Bob")
                .mobileNumber("9876543201")
                .role(User.UserRole.TRAVELER)
                .kycStatus(User.KycStatus.APPROVED)
                .build());

        senderA = userRepository.save(User.builder()
                .fullName("Sender Alice")
                .mobileNumber("9876543202")
                .role(User.UserRole.SENDER)
                .kycStatus(User.KycStatus.NOT_SUBMITTED)
                .build());

        senderB = userRepository.save(User.builder()
                .fullName("Sender Charlie")
                .mobileNumber("9876543203")
                .role(User.UserRole.SENDER)
                .kycStatus(User.KycStatus.NOT_SUBMITTED)
                .build());

        trip = tripRepository.save(Trip.builder()
                .travelerId(traveler.getId())
                .source("Bengaluru")
                .destination("Chennai")
                .departureTime(LocalDateTime.now().plusDays(1))
                .availableSeats(2)
                .availableCapacityKg(5.0) // Only 5 kg capacity
                .status(Trip.TripStatus.PLANNED)
                .build());
    }

    @AfterEach
    public void tearDown() {
        rideRequestRepository.deleteAll();
        parcelRequestRepository.deleteAll();
        tripRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    public void testConcurrentDoubleBookingPessimisticLock() throws Exception {
        int numberOfThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<String> errorMessages = new java.util.concurrent.CopyOnWriteArrayList<>();

        // Create two requests for 4.0 kg each (exceeds 5.0 kg capacity if double booked)
        ParcelBookingRequest reqA = new ParcelBookingRequest();
        reqA.setSenderId(senderA.getId());
        reqA.setGoodsDescription("4kg Books");
        reqA.setDeclaredValue(100.0);
        reqA.setEstimatedWeightKg(4.0);
        reqA.setPickupLocation("Indiranagar");
        reqA.setDropoffLocation("Adyar");
        reqA.setTripId(trip.getId());

        ParcelBookingRequest reqB = new ParcelBookingRequest();
        reqB.setSenderId(senderB.getId());
        reqB.setGoodsDescription("4kg Electronics");
        reqB.setDeclaredValue(200.0);
        reqB.setEstimatedWeightKg(4.0);
        reqB.setPickupLocation("Indiranagar");
        reqB.setDropoffLocation("Adyar");
        reqB.setTripId(trip.getId());

        executor.submit(() -> {
            try {
                startLatch.await();
                parcelService.createParcelRequest(reqA);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
                errorMessages.add(e.getMessage() != null ? e.getMessage() : e.getClass().getName());
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                parcelService.createParcelRequest(reqB);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
                errorMessages.add(e.getMessage() != null ? e.getMessage() : e.getClass().getName());
            } finally {
                doneLatch.countDown();
            }
        });

        // Trigger both threads simultaneously
        startLatch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // One request must succeed, and the other must fail due to lock capacity rejection
        assertEquals(1, successCount.get(), "Exactly one booking must succeed");
        assertEquals(1, failureCount.get(), "Exactly one booking must fail");
        
        // Assert that the error is indeed capacity exhaustion
        assertTrue(errorMessages.stream().anyMatch(msg -> msg.contains("enough capacity")), 
                "Expected failure message to contain 'enough capacity', but was: " + errorMessages);

        // Fetch the trip from the database again and verify the remaining capacity is exactly 1.0 kg
        Trip updatedTrip = tripRepository.findById(trip.getId()).orElseThrow();
        assertEquals(1.0, updatedTrip.getAvailableCapacityKg(), 0.001, 
                "Trip remaining capacity must be exactly 1.0 kg (not negative)");
    }

    @Test
    public void testConcurrentAutoMatchingDoubleBooking() throws Exception {
        int numberOfThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        List<com.example.project.blabla_porter.model.ParcelRequest> results = new java.util.concurrent.CopyOnWriteArrayList<>();
        List<String> errorMessages = new java.util.concurrent.CopyOnWriteArrayList<>();

        // Create two requests with tripId left null for auto-matching (4.0 kg each)
        ParcelBookingRequest reqA = new ParcelBookingRequest();
        reqA.setSenderId(senderA.getId());
        reqA.setGoodsDescription("4kg Books A");
        reqA.setDeclaredValue(100.0);
        reqA.setEstimatedWeightKg(4.0);
        reqA.setPickupLocation("Bengaluru");
        reqA.setDropoffLocation("Chennai");

        ParcelBookingRequest reqB = new ParcelBookingRequest();
        reqB.setSenderId(senderB.getId());
        reqB.setGoodsDescription("4kg Books B");
        reqB.setDeclaredValue(200.0);
        reqB.setEstimatedWeightKg(4.0);
        reqB.setPickupLocation("Bengaluru");
        reqB.setDropoffLocation("Chennai");

        executor.submit(() -> {
            try {
                startLatch.await();
                com.example.project.blabla_porter.model.ParcelRequest res = parcelService.createParcelRequest(reqA);
                results.add(res);
            } catch (Exception e) {
                errorMessages.add(e.getMessage() != null ? e.getMessage() : e.getClass().getName());
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                com.example.project.blabla_porter.model.ParcelRequest res = parcelService.createParcelRequest(reqB);
                results.add(res);
            } catch (Exception e) {
                errorMessages.add(e.getMessage() != null ? e.getMessage() : e.getClass().getName());
            } finally {
                doneLatch.countDown();
            }
        });

        // Trigger concurrent execution
        startLatch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Both calls should complete successfully (one matches the trip, one remains unmatched)
        assertEquals(2, results.size(), "Both requests should successfully create parcel requests: " + errorMessages);
        assertEquals(0, errorMessages.size(), "No errors should occur: " + errorMessages);

        // One request must be assigned to the trip, the other must have tripId = null
        long matchedCount = results.stream().filter(r -> r.getTripId() != null && r.getTripId().equals(trip.getId())).count();
        long unmatchedCount = results.stream().filter(r -> r.getTripId() == null).count();

        assertEquals(1, matchedCount, "Exactly one booking should match the trip");
        assertEquals(1, unmatchedCount, "Exactly one booking should remain unmatched");

        // The matched trip remaining capacity must be exactly 1.0 kg
        Trip updatedTrip = tripRepository.findById(trip.getId()).orElseThrow();
        assertEquals(1.0, updatedTrip.getAvailableCapacityKg(), 0.001, 
                "Trip capacity must be exactly 1.0 kg");
    }

    @Test
    public void testConcurrentRideSeatBookingPessimisticLock() throws Exception {
        // Set trip available seats to 1
        trip.setAvailableSeats(1);
        tripRepository.save(trip);

        // Save two riders
        User riderA = userRepository.save(User.builder()
                .fullName("Rider Alice")
                .mobileNumber("9876543211")
                .role(User.UserRole.RIDER)
                .kycStatus(User.KycStatus.NOT_SUBMITTED)
                .build());

        User riderB = userRepository.save(User.builder()
                .fullName("Rider Bob")
                .mobileNumber("9876543212")
                .role(User.UserRole.RIDER)
                .kycStatus(User.KycStatus.NOT_SUBMITTED)
                .build());

        // Create ride booking requests
        com.example.project.blabla_porter.dto.RideBookingRequest rideReqA = new com.example.project.blabla_porter.dto.RideBookingRequest();
        rideReqA.setRiderId(riderA.getId());
        rideReqA.setTripId(trip.getId());
        rideReqA.setPickupLocation("Indiranagar");
        rideReqA.setDropoffLocation("Whitefield");

        com.example.project.blabla_porter.dto.RideBookingRequest rideReqB = new com.example.project.blabla_porter.dto.RideBookingRequest();
        rideReqB.setRiderId(riderB.getId());
        rideReqB.setTripId(trip.getId());
        rideReqB.setPickupLocation("Indiranagar");
        rideReqB.setDropoffLocation("Whitefield");

        com.example.project.blabla_porter.model.RideRequest rideA = rideService.requestRide(rideReqA);
        com.example.project.blabla_porter.model.RideRequest rideB = rideService.requestRide(rideReqB);

        int numberOfThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<String> errorMessages = new java.util.concurrent.CopyOnWriteArrayList<>();

        // Create verification verifyRequests (mocking Razorpay)
        com.example.project.blabla_porter.dto.RazorpayVerifyRequest verifyReqA = com.example.project.blabla_porter.dto.RazorpayVerifyRequest.builder()
                .razorpayOrderId("order_mock_ride_A")
                .razorpayPaymentId("pay_mock_ride_A")
                .razorpaySignature("sig_mock_ride_A")
                .senderId(riderA.getId()) // SenderId is mapped as the verifier
                .build();

        com.example.project.blabla_porter.dto.RazorpayVerifyRequest verifyReqB = com.example.project.blabla_porter.dto.RazorpayVerifyRequest.builder()
                .razorpayOrderId("order_mock_ride_B")
                .razorpayPaymentId("pay_mock_ride_B")
                .razorpaySignature("sig_mock_ride_B")
                .senderId(riderB.getId())
                .build();

        executor.submit(() -> {
            try {
                startLatch.await();
                rideService.verifyRazorpayPayment(rideA.getId(), verifyReqA);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
                errorMessages.add(e.getMessage() != null ? e.getMessage() : e.getClass().getName());
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                rideService.verifyRazorpayPayment(rideB.getId(), verifyReqB);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
                errorMessages.add(e.getMessage() != null ? e.getMessage() : e.getClass().getName());
            } finally {
                doneLatch.countDown();
            }
        });

        // Trigger simultaneous execution
        startLatch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly one seat verification should succeed, and exactly one should fail due to seat exhaustion
        assertEquals(1, successCount.get(), "Exactly one ride seat payment verification should succeed");
        assertEquals(1, failureCount.get(), "Exactly one ride seat payment verification should fail");

        // Verify that the error message is related to seats availability
        assertTrue(errorMessages.stream().anyMatch(msg -> msg.contains("seats left")), 
                "Expected failure message to contain 'seats left', but was: " + errorMessages);

        // Verify the remaining seats on the trip is exactly 0
        Trip updatedTrip = tripRepository.findById(trip.getId()).orElseThrow();
        assertEquals(0, updatedTrip.getAvailableSeats(), "Trip available seats must be exactly 0");
    }
}
