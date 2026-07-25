package com.example.project.blabla_porter;

import com.example.project.blabla_porter.dto.*;
import com.example.project.blabla_porter.model.*;
import com.example.project.blabla_porter.repository.*;
import com.example.project.blabla_porter.service.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.example.project.Application.class)
@TestPropertySource(properties = {
    "blabla.seeder.enabled=false",
    "spring.main.allow-bean-definition-overriding=true",
    "spring.datasource.url=jdbc:h2:mem:location_security_db;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=true"
})
@Transactional
@ActiveProfiles("test")
public class LocationSecurityTest {

    @Autowired private UserRepository userRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private RideRequestRepository rideRequestRepository;
    @Autowired private TrackingService trackingService;
    @Autowired private RateLimitingService rateLimitingService;
    @Autowired private LocationPingRepository locationPingRepository;

    private User createUser(String name, String mobile, User.UserRole role, User.KycStatus kyc) {
        return userRepository.save(User.builder()
                .fullName(name).mobileNumber(mobile).role(role)
                .kycStatus(kyc).passwordHash("$2a$10$dummyhash").build());
    }

    // ==================================================================================
    // TEST 1: Unauthorized user cannot fetch live tracking for a trip they're not on
    // ==================================================================================
    @Test
    @DisplayName("Location Security: Unauthorized user DENIED access to live tracking")
    void testUnauthorizedUserCannotFetchLiveTracking() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("  LOCATION SECURITY TEST 1: Unauthorized user blocked from live tracking");
        System.out.println("=".repeat(80));

        // Create captain and rider on a trip
        User captain = createUser("Bob Captain", "9880001001", User.UserRole.TRAVELER, User.KycStatus.APPROVED);
        User rider = createUser("Charlie Rider", "9880001002", User.UserRole.RIDER, User.KycStatus.NOT_SUBMITTED);

        Trip trip = tripRepository.save(Trip.builder()
                .travelerId(captain.getId()).source("Bengaluru").destination("Chennai")
                .departureTime(LocalDateTime.now().plusDays(1))
                .availableCapacityKg(10.0).availableSeats(2)
                .status(Trip.TripStatus.ACTIVE).build());

        rideRequestRepository.save(RideRequest.builder()
                .riderId(rider.getId()).tripId(trip.getId())
                .pickupLocation("Koramangala").dropoffLocation("Guindy")
                .status(RideRequest.RideStatus.ACCEPTED)
                .pickupLatitude(12.9352).pickupLongitude(77.6245)
                .dropoffLatitude(13.0067).dropoffLongitude(80.2206)
                .estimatedDurationMinutes(300).bufferMinutes(5)
                .createdAt(LocalDateTime.now()).build());

        // Create outsider who is NOT on this trip
        User eve = createUser("Eve Outsider", "9880001003", User.UserRole.RIDER, User.KycStatus.NOT_SUBMITTED);

        // Eve tries to access tracking — should be DENIED
        Exception ex = assertThrows(IllegalArgumentException.class,
                () -> trackingService.getLiveTracking(trip.getId(), eve.getId()));

        System.out.println("\n  ❌ Eve (outsider) attempted to access trip " + trip.getId());
        System.out.println("  Exception: " + ex.getClass().getSimpleName());
        System.out.println("  Message: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("Access denied"));

        System.out.println("\n  HTTP equivalent: 403 Forbidden");
        System.out.println("  Response body: {\"error\": \"" + ex.getMessage() + "\"}");
        System.out.println("=".repeat(80) + "\n");
    }

    // ==================================================================================
    // TEST 2: Authorized rider CAN access live tracking for their trip
    // ==================================================================================
    @Test
    @DisplayName("Location Security: Authorized rider CAN fetch live tracking for their trip")
    void testAuthorizedRiderCanFetchLiveTracking() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("  LOCATION SECURITY TEST 2: Authorized rider can access live tracking");
        System.out.println("=".repeat(80));

        User captain = createUser("Bob Captain", "9880002001", User.UserRole.TRAVELER, User.KycStatus.APPROVED);
        User rider = createUser("Charlie Rider", "9880002002", User.UserRole.RIDER, User.KycStatus.NOT_SUBMITTED);

        Trip trip = tripRepository.save(Trip.builder()
                .travelerId(captain.getId()).source("Bengaluru").destination("Chennai")
                .departureTime(LocalDateTime.now().plusDays(1))
                .availableCapacityKg(10.0).availableSeats(2)
                .status(Trip.TripStatus.ACTIVE).build());

        rideRequestRepository.save(RideRequest.builder()
                .riderId(rider.getId()).tripId(trip.getId())
                .pickupLocation("Koramangala").dropoffLocation("Guindy")
                .status(RideRequest.RideStatus.ACCEPTED)
                .pickupLatitude(12.9352).pickupLongitude(77.6245)
                .dropoffLatitude(13.0067).dropoffLongitude(80.2206)
                .estimatedDurationMinutes(300).bufferMinutes(5)
                .createdAt(LocalDateTime.now()).build());

        // Charlie (actual rider) accesses tracking — should SUCCEED
        TrackingDto.LiveTrackingResponse response = trackingService.getLiveTracking(trip.getId(), rider.getId());

        assertNotNull(response);
        assertEquals(trip.getId(), response.getTripId());
        System.out.println("\n  ✅ Charlie (authorized rider) accessed trip " + trip.getId());
        System.out.println("  Trip status: " + response.getTripStatus());
        System.out.println("  Current position: " + response.getCurrentLatitude() + ", " + response.getCurrentLongitude());
        System.out.println("  HTTP equivalent: 200 OK");

        // Captain also accesses — should SUCCEED
        TrackingDto.LiveTrackingResponse captainResponse = trackingService.getLiveTracking(trip.getId(), captain.getId());
        assertNotNull(captainResponse);
        System.out.println("\n  ✅ Bob (captain/traveler) also accessed trip " + trip.getId());
        System.out.println("  HTTP equivalent: 200 OK");
        System.out.println("=".repeat(80) + "\n");
    }

    // ==================================================================================
    // TEST 3: Ping impersonation is blocked
    // ==================================================================================
    @Test
    @DisplayName("Location Security: Ping impersonation blocked — user cannot send pings as another traveler")
    void testPingImpersonationBlocked() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("  LOCATION SECURITY TEST 3: Ping impersonation blocked");
        System.out.println("=".repeat(80));

        User captain = createUser("Bob Captain", "9880003001", User.UserRole.TRAVELER, User.KycStatus.APPROVED);
        User eve = createUser("Eve Impersonator", "9880003002", User.UserRole.RIDER, User.KycStatus.NOT_SUBMITTED);

        Trip trip = tripRepository.save(Trip.builder()
                .travelerId(captain.getId()).source("Bengaluru").destination("Chennai")
                .departureTime(LocalDateTime.now().plusDays(1))
                .availableCapacityKg(10.0).availableSeats(2)
                .status(Trip.TripStatus.ACTIVE).build());

        // Eve tries to send a ping pretending to be Bob
        TrackingDto.LocationPingRequest pingReq = new TrackingDto.LocationPingRequest();
        pingReq.setTripId(trip.getId());
        pingReq.setTravelerId(captain.getId());  // Impersonating Bob
        pingReq.setLatitude(12.9716);
        pingReq.setLongitude(77.5946);

        // authenticatedUserId = Eve's ID, but travelerId = Bob's ID → BLOCKED
        Exception ex = assertThrows(IllegalArgumentException.class,
                () -> trackingService.recordLocationPing(pingReq, eve.getId()));

        System.out.println("\n  ❌ Eve (ID: " + eve.getId() + ") tried to send ping as Bob (ID: " + captain.getId() + ")");
        System.out.println("  Exception: " + ex.getClass().getSimpleName());
        System.out.println("  Message: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("Access denied"));
        assertTrue(ex.getMessage().contains("as yourself"));

        System.out.println("\n  HTTP equivalent: 403 Forbidden");
        System.out.println("  Response body: {\"error\": \"" + ex.getMessage() + "\"}");
        System.out.println("=".repeat(80) + "\n");
    }

    // ==================================================================================
    // TEST 4: Completed trip location data NOT returnable
    // ==================================================================================
    @Test
    @DisplayName("Location Security: Completed trip returns empty tracking — no historical location data leaked")
    void testCompletedTripLocationDataNotReturnable() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("  LOCATION SECURITY TEST 4: Completed trip returns empty tracking data");
        System.out.println("=".repeat(80));

        User captain = createUser("Bob Captain", "9880004001", User.UserRole.TRAVELER, User.KycStatus.APPROVED);

        Trip trip = tripRepository.save(Trip.builder()
                .travelerId(captain.getId()).source("Bengaluru").destination("Chennai")
                .departureTime(LocalDateTime.now().minusHours(5))
                .availableCapacityKg(10.0).availableSeats(2)
                .status(Trip.TripStatus.ACTIVE).build());

        // Record some pings while trip was active
        TrackingDto.LocationPingRequest ping1 = new TrackingDto.LocationPingRequest();
        ping1.setTripId(trip.getId());
        ping1.setTravelerId(captain.getId());
        ping1.setLatitude(12.9716);
        ping1.setLongitude(77.5946);
        trackingService.recordLocationPing(ping1, captain.getId());

        TrackingDto.LocationPingRequest ping2 = new TrackingDto.LocationPingRequest();
        ping2.setTripId(trip.getId());
        ping2.setTravelerId(captain.getId());
        ping2.setLatitude(13.0827);
        ping2.setLongitude(80.2707);
        trackingService.recordLocationPing(ping2, captain.getId());

        // Verify pings exist while trip is active
        TrackingDto.LiveTrackingResponse activeResponse = trackingService.getLiveTracking(trip.getId(), captain.getId());
        System.out.println("\n  While trip ACTIVE:");
        System.out.println("  Total pings: " + activeResponse.getTotalPingsCount());
        System.out.println("  Trail size: " + activeResponse.getBreadcrumbTrail().size());
        assertEquals(2, activeResponse.getTotalPingsCount());

        // Mark trip as COMPLETED
        trip.setStatus(Trip.TripStatus.COMPLETED);
        tripRepository.save(trip);

        // Now fetch again — should return EMPTY response
        TrackingDto.LiveTrackingResponse completedResponse = trackingService.getLiveTracking(trip.getId(), captain.getId());

        System.out.println("\n  After trip COMPLETED:");
        System.out.println("  Total pings returned: " + completedResponse.getTotalPingsCount());
        System.out.println("  Trail size: " + (completedResponse.getBreadcrumbTrail() != null ? completedResponse.getBreadcrumbTrail().size() : 0));
        System.out.println("  Trip status: " + completedResponse.getTripStatus());
        System.out.println("  Distance remaining: " + completedResponse.getDistanceRemainingKm());

        assertEquals(0, completedResponse.getTotalPingsCount(), "No pings should be returned for completed trip");
        assertEquals("COMPLETED", completedResponse.getTripStatus());
        assertEquals(0.0, completedResponse.getDistanceRemainingKm());

        System.out.println("\n  ✅ Historical location data is NOT returned for completed trips.");
        System.out.println("=".repeat(80) + "\n");
    }

    // ==================================================================================
    // TEST 5: Tracking rate limit enforced
    // ==================================================================================
    @Test
    @DisplayName("Location Security: Rate limit enforced on tracking reads (30/min)")
    void testTrackingRateLimitEnforced() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("  LOCATION SECURITY TEST 5: Rate limit enforcement on tracking endpoints");
        System.out.println("=".repeat(80));

        // Reset rate limiter for clean test
        rateLimitingService.resetCategory("tracking_live");

        String testKey = "rate_test_user_999";
        java.time.Duration window = java.time.Duration.ofMinutes(1);
        int limit = 30;

        int successCount = 0;
        int blockedCount = 0;

        for (int i = 0; i < 35; i++) {
            boolean allowed = rateLimitingService.tryAcquire("tracking_live", testKey, limit, window);
            if (allowed) {
                successCount++;
            } else {
                blockedCount++;
            }
        }

        System.out.println("\n  Attempted 35 tracking requests in rapid succession:");
        System.out.println("  ✅ Allowed: " + successCount);
        System.out.println("  ❌ Rate-limited (429): " + blockedCount);

        assertEquals(30, successCount, "First 30 requests should be allowed");
        assertEquals(5, blockedCount, "Last 5 requests should be rate-limited");

        System.out.println("\n  HTTP equivalent for blocked requests:");
        System.out.println("  Status: 429 Too Many Requests");
        System.out.println("  Body: {\"error\": \"Too many tracking requests. Please wait before retrying.\"}");
        System.out.println("=".repeat(80) + "\n");
    }
}
