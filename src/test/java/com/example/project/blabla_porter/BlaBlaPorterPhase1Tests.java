package com.example.project.blabla_porter;

import com.example.project.blabla_porter.dto.KycSubmitRequest;
import com.example.project.blabla_porter.dto.RegisterRequest;
import com.example.project.blabla_porter.dto.TripCreateRequest;
import com.example.project.blabla_porter.model.Trip;
import com.example.project.blabla_porter.model.TrustedContact;
import com.example.project.blabla_porter.model.User;
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
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=true"
})
@Transactional
public class BlaBlaPorterPhase1Tests {

    @Autowired
    private UserService userService;

    @Autowired
    private TripService tripService;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("Scenario 1: Register Rider/Sender auto-approves KYC")
    void test1_registerRider_autoApprovesKyc() {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Alice Rider");
        req.setMobileNumber("9876543210");
        req.setEmail("alice@example.com");
        req.setRole(User.UserRole.RIDER);

        User user = userService.register(req);

        assertNotNull(user.getId());
        assertEquals("Alice Rider", user.getFullName());
        assertEquals(User.UserRole.RIDER, user.getRole());
        assertEquals(User.KycStatus.APPROVED, user.getKycStatus(), "Riders should be auto-approved without KYC gate");
    }

    @Test
    @DisplayName("Scenario 2: Register Traveler requires KYC (NOT_SUBMITTED)")
    void test2_registerTraveler_setsKycNotSubmitted() {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Bob Captain");
        req.setMobileNumber("9876543211");
        req.setEmail("bob@example.com");
        req.setRole(User.UserRole.TRAVELER);

        User user = userService.register(req);

        assertNotNull(user.getId());
        assertEquals(User.UserRole.TRAVELER, user.getRole());
        assertEquals(User.KycStatus.NOT_SUBMITTED, user.getKycStatus(), "Travelers must start with NOT_SUBMITTED KYC");
    }

    @Test
    @DisplayName("Scenario 3: Duplicate mobile number registration throws exception")
    void test3_duplicateMobileNumber_throwsException() {
        RegisterRequest req1 = new RegisterRequest();
        req1.setFullName("User One");
        req1.setMobileNumber("9998887770");
        req1.setRole(User.UserRole.SENDER);
        userService.register(req1);

        RegisterRequest req2 = new RegisterRequest();
        req2.setFullName("User Two");
        req2.setMobileNumber("9998887770"); // Duplicate number
        req2.setRole(User.UserRole.SENDER);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            userService.register(req2);
        });

        assertTrue(ex.getMessage().contains("already registered"));
    }

    @Test
    @DisplayName("Scenario 4: Unapproved Traveler cannot declare trips")
    void test4_unapprovedTraveler_cannotCreateTrip() {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Charlie Traveler");
        req.setMobileNumber("9876543212");
        req.setRole(User.UserRole.TRAVELER);
        User traveler = userService.register(req);

        TripCreateRequest tripReq = new TripCreateRequest();
        tripReq.setTravelerId(traveler.getId());
        tripReq.setSource("Mumbai");
        tripReq.setDestination("Pune");
        tripReq.setDepartureTime(LocalDateTime.now().plusDays(1));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            tripService.createTrip(tripReq);
        });

        assertTrue(ex.getMessage().contains("KYC must be APPROVED"));
    }

    @Test
    @DisplayName("Scenario 5: Traveler submitting KYC sets status to PENDING_APPROVAL")
    void test5_travelerSubmitKyc_setsPendingApproval() {
        RegisterRequest regReq = new RegisterRequest();
        regReq.setFullName("Dave Captain");
        regReq.setMobileNumber("9876543213");
        regReq.setRole(User.UserRole.TRAVELER);
        User traveler = userService.register(regReq);

        KycSubmitRequest kycReq = new KycSubmitRequest();
        kycReq.setUserId(traveler.getId());
        kycReq.setAadhaarNumber("1234-5678-9012");
        kycReq.setPanNumber("ABCDE1234F");
        kycReq.setDrivingLicenceNumber("DL-999888777");
        kycReq.setRcNumber("MH-12-AB-1234");
        kycReq.setInsuranceNumber("INS-100200");
        kycReq.setPucNumber("PUC-300400");
        kycReq.setSelfieUrl("http://storage.com/selfie.jpg");

        User updatedUser = userService.submitKyc(kycReq);

        assertEquals(User.KycStatus.PENDING_APPROVAL, updatedUser.getKycStatus());
        assertEquals("ABCDE1234F", updatedUser.getPanNumber());
    }

    @Test
    @DisplayName("Scenario 6: Admin reviews and approves pending KYC")
    void test6_adminReviewKyc_approvesTraveler() {
        RegisterRequest regReq = new RegisterRequest();
        regReq.setFullName("Eve Captain");
        regReq.setMobileNumber("9876543214");
        regReq.setRole(User.UserRole.TRAVELER);
        User traveler = userService.register(regReq);

        KycSubmitRequest kycReq = new KycSubmitRequest();
        kycReq.setUserId(traveler.getId());
        kycReq.setAadhaarNumber("1234-5678-9999");
        kycReq.setPanNumber("XYZDE1234F");
        kycReq.setDrivingLicenceNumber("DL-111222333");
        kycReq.setRcNumber("MH-14-XY-9999");
        userService.submitKyc(kycReq);

        List<User> pendingUsers = userService.getPendingKycUsers();
        assertFalse(pendingUsers.isEmpty());

        User approvedUser = userService.reviewKyc(traveler.getId(), true);
        assertEquals(User.KycStatus.APPROVED, approvedUser.getKycStatus());
    }

    @Test
    @DisplayName("Scenario 7: Approved Traveler creates Trip successfully")
    void test7_approvedTraveler_createsTripSuccessfully() {
        RegisterRequest regReq = new RegisterRequest();
        regReq.setFullName("Frank Captain");
        regReq.setMobileNumber("9876543215");
        regReq.setRole(User.UserRole.TRAVELER);
        User traveler = userService.register(regReq);

        KycSubmitRequest kycReq = new KycSubmitRequest();
        kycReq.setUserId(traveler.getId());
        kycReq.setAadhaarNumber("1111-2222-3333");
        kycReq.setPanNumber("ABCDE1111A");
        kycReq.setDrivingLicenceNumber("DL-000111");
        kycReq.setRcNumber("MH-01-AA-0001");
        userService.submitKyc(kycReq);
        userService.reviewKyc(traveler.getId(), true);

        TripCreateRequest tripReq = new TripCreateRequest();
        tripReq.setTravelerId(traveler.getId());
        tripReq.setSource("Bengaluru");
        tripReq.setDestination("Chennai");
        tripReq.setDepartureTime(LocalDateTime.now().plusDays(2));
        tripReq.setAvailableCapacityKg(15.0);
        tripReq.setAvailableSeats(2);

        Trip trip = tripService.createTrip(tripReq);

        assertNotNull(trip.getId());
        assertEquals("Bengaluru", trip.getSource());
        assertEquals("Chennai", trip.getDestination());
        assertEquals(Trip.TripStatus.PLANNED, trip.getStatus());
    }

    @Test
    @DisplayName("Scenario 8: Non-Traveler role cannot declare trips")
    void test8_nonTravelerRole_cannotCreateTrip() {
        RegisterRequest regReq = new RegisterRequest();
        regReq.setFullName("Grace Rider");
        regReq.setMobileNumber("9876543216");
        regReq.setRole(User.UserRole.RIDER);
        User rider = userService.register(regReq);

        TripCreateRequest tripReq = new TripCreateRequest();
        tripReq.setTravelerId(rider.getId());
        tripReq.setSource("Delhi");
        tripReq.setDestination("Agra");
        tripReq.setDepartureTime(LocalDateTime.now().plusDays(1));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            tripService.createTrip(tripReq);
        });

        assertTrue(ex.getMessage().contains("must have TRAVELER role"));
    }

    @Test
    @DisplayName("Scenario 9: Search trips with case-insensitive route matching")
    void test9_searchTrips_matchesRouteCaseInsensitive() {
        RegisterRequest regReq = new RegisterRequest();
        regReq.setFullName("Hank Captain");
        regReq.setMobileNumber("9876543217");
        regReq.setRole(User.UserRole.TRAVELER);
        User traveler = userService.register(regReq);

        KycSubmitRequest kycReq = new KycSubmitRequest();
        kycReq.setUserId(traveler.getId());
        kycReq.setAadhaarNumber("3333-4444-5555");
        kycReq.setPanNumber("ABCDE3333A");
        kycReq.setDrivingLicenceNumber("DL-333444");
        kycReq.setRcNumber("KA-01-BB-3333");
        userService.submitKyc(kycReq);
        userService.reviewKyc(traveler.getId(), true);

        TripCreateRequest tripReq = new TripCreateRequest();
        tripReq.setTravelerId(traveler.getId());
        tripReq.setSource("Hyderabad");
        tripReq.setDestination("Vijayawada");
        tripReq.setDepartureTime(LocalDateTime.now().plusDays(1));
        tripService.createTrip(tripReq);

        List<Trip> searchResults = tripService.searchTrips("hyder", "vijaya");
        assertFalse(searchResults.isEmpty());
        assertEquals("Hyderabad", searchResults.get(0).getSource());

        List<Trip> emptyResults = tripService.searchTrips("Kolkata", "Mumbai");
        assertTrue(emptyResults.isEmpty());
    }

    @Test
    @DisplayName("Scenario 10: Trusted Contact registration and retrieval")
    void test10_trustedContact_registrationAndRetrieval() {
        RegisterRequest regReq = new RegisterRequest();
        regReq.setFullName("Ivy Rider");
        regReq.setMobileNumber("9876543218");
        regReq.setRole(User.UserRole.RIDER);
        User rider = userService.register(regReq);

        TrustedContact contact = userService.addTrustedContact(rider.getId(), "Mom", "9112233445", "Parent");

        assertNotNull(contact.getId());
        assertEquals("Mom", contact.getContactName());

        List<TrustedContact> contacts = userService.getTrustedContacts(rider.getId());
        assertEquals(1, contacts.size());
        assertEquals("9112233445", contacts.get(0).getContactPhoneNumber());
    }
}
