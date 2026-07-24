package com.example.project.blabla_porter;

import com.example.project.blabla_porter.dto.*;
import com.example.project.blabla_porter.model.*;
import com.example.project.blabla_porter.repository.SafetyAlertRepository;
import com.example.project.blabla_porter.service.RideService;
import com.example.project.blabla_porter.service.TripService;
import com.example.project.blabla_porter.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.example.project.Application.class)
@TestPropertySource(properties = {
    "blabla.seeder.enabled=false",
    "spring.main.allow-bean-definition-overriding=true",
    "spring.datasource.url=jdbc:h2:mem:phase3_tests_db;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=true"
})
@Transactional
public class BlaBlaPorterPhase3Tests {

    @Autowired
    private UserService userService;

    @Autowired
    private TripService tripService;

    @Autowired
    private RideService rideService;

    @Autowired
    private SafetyAlertRepository safetyAlertRepository;

    private User rider;
    private User traveler;
    private Trip trip;

    @BeforeEach
    void setUp() {
        // Register Rider
        RegisterRequest rReq = new RegisterRequest();
        rReq.setFullName("Rachel Rider");
        rReq.setMobileNumber("9888877777");
        rReq.setRole(User.UserRole.RIDER);
        rider = userService.register(rReq);

        // Add Trusted Contact for Rider
        userService.addTrustedContact(rider.getId(), "Brother", "9888866666", "Sibling");

        // Register & Approve Traveler
        RegisterRequest tReq = new RegisterRequest();
        tReq.setFullName("Troy Traveler");
        tReq.setMobileNumber("9777766666");
        tReq.setRole(User.UserRole.TRAVELER);
        traveler = userService.register(tReq);

        KycSubmitRequest kycReq = new KycSubmitRequest();
        kycReq.setUserId(traveler.getId());
        kycReq.setAadhaarNumber("5000-6000-7000");
        kycReq.setPanNumber("ABCDE5000Z");
        kycReq.setDrivingLicenceNumber("DL-500");
        kycReq.setRcNumber("KA-03-MM-5000");
        userService.submitKyc(kycReq);
        userService.reviewKyc(traveler.getId(), true);

        // Declare Trip
        TripCreateRequest trReq = new TripCreateRequest();
        trReq.setTravelerId(traveler.getId());
        trReq.setSource("Koramangala, Bengaluru");
        trReq.setDestination("Indiranagar, Bengaluru");
        trReq.setDepartureTime(LocalDateTime.now().plusHours(2));
        trReq.setAvailableSeats(3);
        trip = tripService.createTrip(trReq);
    }

    @Test
    @DisplayName("Phase 3 - Test 1: Request ride calculates dynamic buffer minutes")
    void test1_requestRide_calculatesDynamicBuffer() {
        RideBookingRequest req = new RideBookingRequest();
        req.setRiderId(rider.getId());
        req.setTripId(trip.getId());
        req.setPickupLocation("Koramangala 5th Block");
        req.setDropoffLocation("100ft Road, Indiranagar");
        req.setSafetyModeEnabled(true);
        req.setEstimatedDurationMinutes(40); // 20% of 40 = 8 minutes buffer

        RideRequest ride = rideService.requestRide(req);

        assertNotNull(ride.getId());
        assertEquals(RideRequest.RideStatus.REQUESTED, ride.getStatus());
        assertTrue(ride.getSafetyModeEnabled());
        assertEquals(8, ride.getBufferMinutes());
    }

    @Test
    @DisplayName("Phase 3 - Test 2: Traveler accepts Ride Request")
    void test2_travelerAcceptsRide() {
        RideBookingRequest req = createSampleRideRequest();
        RideRequest ride = rideService.requestRide(req);

        RideRequest acceptedRide = rideService.acceptRide(ride.getId(), traveler.getId());

        assertEquals(RideRequest.RideStatus.ACCEPTED, acceptedRide.getStatus());
    }

    @Test
    @DisplayName("Phase 3 - Test 3: Start ride updates status to IN_PROGRESS")
    void test3_startRide_updatesStatusToInProgress() {
        RideBookingRequest req = createSampleRideRequest();
        RideRequest ride = rideService.requestRide(req);
        rideService.acceptRide(ride.getId(), traveler.getId());

        RideRequest startedRide = rideService.startRide(ride.getId());

        assertEquals(RideRequest.RideStatus.IN_PROGRESS, startedRide.getStatus());
    }

    @Test
    @DisplayName("Phase 3 - Test 4: Complete ride updates status to COMPLETED and resolves safety alerts")
    void test4_completeRide_updatesStatusToCompleted() {
        RideBookingRequest req = createSampleRideRequest();
        RideRequest ride = rideService.requestRide(req);
        rideService.acceptRide(ride.getId(), traveler.getId());
        rideService.startRide(ride.getId());

        // Trigger safety alert
        SafetyAlert alert = rideService.triggerSafetyEscalation(ride.getId(), "Location Ping 1", SafetyAlert.EscalationStage.STAGE_1_SILENT_PING);
        assertEquals(SafetyAlert.AlertStatus.TRIGGERED, alert.getStatus());

        RideRequest completedRide = rideService.completeRide(ride.getId());

        assertEquals(RideRequest.RideStatus.COMPLETED, completedRide.getStatus());
        SafetyAlert updatedAlert = safetyAlertRepository.findById(alert.getId()).orElseThrow();
        assertEquals(SafetyAlert.AlertStatus.RESOLVED, updatedAlert.getStatus());
    }

    @Test
    @DisplayName("Phase 3 - Test 5: Safety escalation Stage 1 Silent Ping")
    void test5_safetyEscalation_stage1_silentPing() {
        RideBookingRequest req = createSampleRideRequest();
        RideRequest ride = rideService.requestRide(req);
        rideService.acceptRide(ride.getId(), traveler.getId());
        rideService.startRide(ride.getId());

        SafetyAlert alert = rideService.triggerSafetyEscalation(ride.getId(), "Checkpoint A", SafetyAlert.EscalationStage.STAGE_1_SILENT_PING);

        assertEquals(SafetyAlert.EscalationStage.STAGE_1_SILENT_PING, alert.getEscalationStage());
        assertEquals("Checkpoint A", alert.getLastKnownLocation());
    }

    @Test
    @DisplayName("Phase 3 - Test 6: Safety escalation Stage 2 In-App Check-in")
    void test6_safetyEscalation_stage2_inAppCheckin() {
        RideBookingRequest req = createSampleRideRequest();
        RideRequest ride = rideService.requestRide(req);
        rideService.acceptRide(ride.getId(), traveler.getId());
        rideService.startRide(ride.getId());

        SafetyAlert alert = rideService.triggerSafetyEscalation(ride.getId(), "Checkpoint B", SafetyAlert.EscalationStage.STAGE_2_IN_APP_CHECKIN);

        assertEquals(SafetyAlert.EscalationStage.STAGE_2_IN_APP_CHECKIN, alert.getEscalationStage());
    }

    @Test
    @DisplayName("Phase 3 - Test 7: Safety escalation Stage 3 Trusted Contact Alert")
    void test7_safetyEscalation_stage3_alertTrustedContact() {
        RideBookingRequest req = createSampleRideRequest();
        RideRequest ride = rideService.requestRide(req);
        rideService.acceptRide(ride.getId(), traveler.getId());
        rideService.startRide(ride.getId());

        SafetyAlert alert = rideService.triggerSafetyEscalation(ride.getId(), "Checkpoint C", SafetyAlert.EscalationStage.STAGE_3_TRUSTED_CONTACT_ALERT);

        assertEquals(SafetyAlert.EscalationStage.STAGE_3_TRUSTED_CONTACT_ALERT, alert.getEscalationStage());
        assertEquals(SafetyAlert.AlertStatus.ESCALATED, alert.getStatus());
    }

    @Test
    @DisplayName("Phase 3 - Test 8: Rider responds isSafe = true resolves safety alert")
    void test8_riderRespondsSafe_resolvesSafetyAlert() {
        RideBookingRequest req = createSampleRideRequest();
        RideRequest ride = rideService.requestRide(req);
        rideService.acceptRide(ride.getId(), traveler.getId());
        rideService.startRide(ride.getId());

        SafetyAlert alert = rideService.triggerSafetyEscalation(ride.getId(), "Checkpoint D", SafetyAlert.EscalationStage.STAGE_2_IN_APP_CHECKIN);

        SafetyAlert resolvedAlert = rideService.acknowledgeCheckin(alert.getId(), true);

        assertEquals(SafetyAlert.AlertStatus.RESOLVED, resolvedAlert.getStatus());
        assertNotNull(resolvedAlert.getResolvedAt());
    }

    @Test
    @DisplayName("Phase 3 - Test 9: Rider responds isSafe = false escalates to Stage 3 alert")
    void test9_riderRespondsUnsafe_escalatesToStage3() {
        RideBookingRequest req = createSampleRideRequest();
        RideRequest ride = rideService.requestRide(req);
        rideService.acceptRide(ride.getId(), traveler.getId());
        rideService.startRide(ride.getId());

        SafetyAlert alert = rideService.triggerSafetyEscalation(ride.getId(), "Checkpoint E", SafetyAlert.EscalationStage.STAGE_2_IN_APP_CHECKIN);

        SafetyAlert escalatedAlert = rideService.acknowledgeCheckin(alert.getId(), false);

        assertEquals(SafetyAlert.AlertStatus.ESCALATED, escalatedAlert.getStatus());
        assertEquals(SafetyAlert.EscalationStage.STAGE_3_TRUSTED_CONTACT_ALERT, escalatedAlert.getEscalationStage());
    }

    private RideBookingRequest createSampleRideRequest() {
        RideBookingRequest req = new RideBookingRequest();
        req.setRiderId(rider.getId());
        req.setTripId(trip.getId());
        req.setPickupLocation("Koramangala");
        req.setDropoffLocation("Indiranagar");
        req.setSafetyModeEnabled(true);
        req.setEstimatedDurationMinutes(20);
        return req;
    }
}
