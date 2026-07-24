package com.example.project.blabla_porter;

import com.example.project.blabla_porter.dto.*;
import com.example.project.blabla_porter.model.*;
import com.example.project.blabla_porter.repository.PaymentRepository;

import com.example.project.blabla_porter.service.ChatService;
import com.example.project.blabla_porter.service.ParcelService;
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
    "spring.datasource.url=jdbc:h2:mem:hard_tests_db;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=true"
})
@Transactional
public class HardTestCasesTests {

    @Autowired
    private UserService userService;

    @Autowired
    private TripService tripService;

    @Autowired
    private ParcelService parcelService;

    @Autowired
    private ChatService chatService;

    @Autowired
    private PaymentRepository paymentRepository;

    private User sender;
    private User traveler;
    private Trip trip;

    @BeforeEach
    void setUp() {
        RegisterRequest sReq = new RegisterRequest();
        sReq.setFullName("Hard Test Sender");
        sReq.setMobileNumber("9111111111");
        sReq.setRole(User.UserRole.SENDER);
        sender = userService.register(sReq);

        RegisterRequest tReq = new RegisterRequest();
        tReq.setFullName("Hard Test Captain");
        tReq.setMobileNumber("9222222222");
        tReq.setRole(User.UserRole.TRAVELER);
        traveler = userService.register(tReq);

        KycSubmitRequest kycReq = new KycSubmitRequest();
        kycReq.setUserId(traveler.getId());
        kycReq.setAadhaarNumber("1000-2000-3000");
        kycReq.setPanNumber("ABCDE1000Z");
        kycReq.setDrivingLicenceNumber("DL-100200");
        kycReq.setRcNumber("KA-01-HH-1000");
        userService.submitKyc(kycReq);
        userService.reviewKyc(traveler.getId(), true);

        TripCreateRequest trReq = new TripCreateRequest();
        trReq.setTravelerId(traveler.getId());
        trReq.setSource("Delhi");
        trReq.setDestination("Jaipur");
        trReq.setDepartureTime(LocalDateTime.now().plusDays(1));
        trReq.setAvailableCapacityKg(50.0);
        trip = tripService.createTrip(trReq);
    }

    // --- CATEGORY A: Auth & User Identity Edge Cases ---

    @Test
    @DisplayName("Test 01: Register user with blank name throws exception")
    void test01_registerUser_withBlankName_throwsException() {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("");
        req.setMobileNumber("9333333333");
        req.setRole(User.UserRole.RIDER);

        assertThrows(Exception.class, () -> userService.register(req));
    }

    @Test
    @DisplayName("Test 02: Register user with null role throws exception")
    void test02_registerUser_withNullRole_throwsException() {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("No Role");
        req.setMobileNumber("9444444444");
        req.setRole(null);

        assertThrows(Exception.class, () -> userService.register(req));
    }

    @Test
    @DisplayName("Test 03: Duplicate mobile number check raises IllegalArgumentException")
    void test03_duplicateMobileNumber_throwsException() {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Duplicate Phone");
        req.setMobileNumber("9111111111"); // Matches sender's number
        req.setRole(User.UserRole.SENDER);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> userService.register(req));
        assertTrue(ex.getMessage().contains("already registered"));
    }

    @Test
    @DisplayName("Test 04: Query non-existent user ID throws RuntimeException")
    void test04_getNonExistentUser_throwsNotFoundException() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.getById(99999L));
        assertTrue(ex.getMessage().contains("User not found"));
    }

    @Test
    @DisplayName("Test 05: Trusted contact boundary isolation per user")
    void test05_trustedContact_linkedToMultipleUsers() {
        userService.addTrustedContact(sender.getId(), "Contact A", "9000000001", "Friend");
        userService.addTrustedContact(traveler.getId(), "Contact B", "9000000002", "Spouse");

        assertEquals(1, userService.getTrustedContacts(sender.getId()).size());
        assertEquals("Contact A", userService.getTrustedContacts(sender.getId()).get(0).getContactName());
        assertEquals(1, userService.getTrustedContacts(traveler.getId()).size());
        assertEquals("Contact B", userService.getTrustedContacts(traveler.getId()).get(0).getContactName());
    }

    // --- CATEGORY B: Captain KYC & Admin Governance Edge Cases ---

    @Test
    @DisplayName("Test 06: Non-Traveler role submitting KYC throws IllegalArgumentException")
    void test06_riderSubmittingKyc_throwsException() {
        KycSubmitRequest kyc = new KycSubmitRequest();
        kyc.setUserId(sender.getId()); // Sender role
        kyc.setAadhaarNumber("5555-5555-5555");
        kyc.setPanNumber("ABCDE5555Z");
        kyc.setDrivingLicenceNumber("DL-555");
        kyc.setRcNumber("KA-55");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> userService.submitKyc(kyc));
        assertTrue(ex.getMessage().contains("Only Travelers / Captains require KYC"));
    }

    @Test
    @DisplayName("Test 07: Submitting KYC for non-existent user throws RuntimeException")
    void test07_submittingKyc_nonExistentUser_throwsException() {
        KycSubmitRequest kyc = new KycSubmitRequest();
        kyc.setUserId(88888L);
        kyc.setAadhaarNumber("5555-5555-5555");

        assertThrows(RuntimeException.class, () -> userService.submitKyc(kyc));
    }

    @Test
    @DisplayName("Test 08: Admin review on user not in PENDING_APPROVAL throws IllegalStateException")
    void test08_adminReviewKyc_whenNotInPendingState_throwsIllegalState() {
        // Traveler is already APPROVED
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> userService.reviewKyc(traveler.getId(), true));
        assertTrue(ex.getMessage().contains("not in PENDING_APPROVAL status"));
    }

    @Test
    @DisplayName("Test 09: Admin rejecting KYC sets status to REJECTED")
    void test09_adminRejectKyc_setsStatusToRejected() {
        RegisterRequest tReq2 = new RegisterRequest();
        tReq2.setFullName("Captain Reject");
        tReq2.setMobileNumber("9555555555");
        tReq2.setRole(User.UserRole.TRAVELER);
        User cap = userService.register(tReq2);

        KycSubmitRequest kyc = new KycSubmitRequest();
        kyc.setUserId(cap.getId());
        kyc.setAadhaarNumber("7777-8888-9999");
        kyc.setPanNumber("ABCDE7777Z");
        kyc.setDrivingLicenceNumber("DL-777");
        kyc.setRcNumber("KA-77");
        userService.submitKyc(kyc);

        User rejectedCap = userService.reviewKyc(cap.getId(), false);
        assertEquals(User.KycStatus.REJECTED, rejectedCap.getKycStatus());
    }

    @Test
    @DisplayName("Test 10: Rejected Traveler cannot declare trips")
    void test10_rejectedTraveler_cannotCreateTrip() {
        RegisterRequest tReq2 = new RegisterRequest();
        tReq2.setFullName("Captain Rejected Trip");
        tReq2.setMobileNumber("9666666666");
        tReq2.setRole(User.UserRole.TRAVELER);
        User cap = userService.register(tReq2);

        KycSubmitRequest kyc = new KycSubmitRequest();
        kyc.setUserId(cap.getId());
        kyc.setAadhaarNumber("8888-8888-8888");
        kyc.setPanNumber("ABCDE8888Z");
        kyc.setDrivingLicenceNumber("DL-888");
        kyc.setRcNumber("KA-88");
        userService.submitKyc(kyc);
        userService.reviewKyc(cap.getId(), false);

        TripCreateRequest trReq = new TripCreateRequest();
        trReq.setTravelerId(cap.getId());
        trReq.setSource("Delhi");
        trReq.setDestination("Agra");
        trReq.setDepartureTime(LocalDateTime.now().plusDays(1));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> tripService.createTrip(trReq));
        assertTrue(ex.getMessage().contains("KYC must be APPROVED"));
    }

    // --- CATEGORY C: Trip Declaration & Route Search Edge Cases ---

    @Test
    @DisplayName("Test 11: Create trip by non-existent traveler throws RuntimeException")
    void test11_createTrip_byNonExistentTraveler_throwsException() {
        TripCreateRequest trReq = new TripCreateRequest();
        trReq.setTravelerId(77777L);
        trReq.setSource("Delhi");
        trReq.setDestination("Jaipur");
        trReq.setDepartureTime(LocalDateTime.now().plusDays(1));

        assertThrows(RuntimeException.class, () -> tripService.createTrip(trReq));
    }

    @Test
    @DisplayName("Test 12: Search trips with null or empty parameters returns active planned trips")
    void test12_searchTrips_emptySourceAndDestination_returnsAllPlannedTrips() {
        List<Trip> trips = tripService.searchTrips("", null);
        assertFalse(trips.isEmpty());
        assertEquals("Delhi", trips.get(0).getSource());
    }

    @Test
    @DisplayName("Test 13: Fetch trips by traveler returns traveler's trips")
    void test13_getTripsByTraveler_returnsTripList() {
        List<Trip> travelerTrips = tripService.getTripsByTraveler(traveler.getId());
        assertEquals(1, travelerTrips.size());
        assertEquals("Delhi", travelerTrips.get(0).getSource());
    }

    @Test
    @DisplayName("Test 14: Querying non-existent trip ID throws RuntimeException")
    void test14_getNonExistentTrip_throwsNotFoundException() {
        assertThrows(RuntimeException.class, () -> tripService.getById(88888L));
    }

    // --- CATEGORY D: Parcel Booking & Fare Calculation Edge Cases ---

    @Test
    @DisplayName("Test 15: Create parcel request with zero declared value calculates base fare $15")
    void test15_createParcelRequest_withZeroValue_calculatesBaseFareOnly() {
        ParcelBookingRequest pReq = new ParcelBookingRequest();
        pReq.setSenderId(sender.getId());
        pReq.setTripId(trip.getId());
        pReq.setGoodsDescription("Keys & Documents");
        pReq.setDeclaredValue(0.0);
        pReq.setPickupLocation("Connaught Place, Delhi");
        pReq.setDropoffLocation("MI Road, Jaipur");

        ParcelRequest pr = parcelService.createParcelRequest(pReq);
        assertEquals(15.0, pr.getCalculatedFare(), 0.001);
    }

    @Test
    @DisplayName("Test 16: Create parcel request with high value calculates 2% surcharge")
    void test16_createParcelRequest_withHighValue_calculatesExactSurcharge() {
        ParcelBookingRequest pReq = new ParcelBookingRequest();
        pReq.setSenderId(sender.getId());
        pReq.setTripId(trip.getId());
        pReq.setGoodsDescription("Jewelry");
        pReq.setDeclaredValue(10000.0); // 2% of 10000 = $200 + $15 base = $215
        pReq.setPickupLocation("Delhi");
        pReq.setDropoffLocation("Jaipur");

        ParcelRequest pr = parcelService.createParcelRequest(pReq);
        assertEquals(215.0, pr.getCalculatedFare(), 0.001);
    }

    @Test
    @DisplayName("Test 17: Accept parcel request by non-designated traveler throws IllegalArgumentException")
    void test17_acceptParcelRequest_byNonDesignatedTraveler_throwsException() {
        ParcelBookingRequest pReq = createSampleParcelRequest();
        ParcelRequest pr = parcelService.createParcelRequest(pReq);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> parcelService.acceptParcelRequest(pr.getId(), 9999L));
        assertTrue(ex.getMessage().contains("Only the designated traveler"));
    }

    @Test
    @DisplayName("Test 18: Accept parcel request when not in CREATED status throws IllegalStateException")
    void test18_acceptParcelRequest_alreadyAccepted_throwsIllegalState() {
        ParcelBookingRequest pReq = createSampleParcelRequest();
        ParcelRequest pr = parcelService.createParcelRequest(pReq);
        parcelService.acceptParcelRequest(pr.getId(), traveler.getId());

        // Re-accepting
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> parcelService.acceptParcelRequest(pr.getId(), traveler.getId()));
        assertTrue(ex.getMessage().contains("not in CREATED status"));
    }

    // --- CATEGORY E: Escrow Payment & OTP Handover Edge Cases ---

    @Test
    @DisplayName("Test 19: Pay escrow by non-sender user throws IllegalArgumentException")
    void test19_payEscrow_byNonSender_throwsIllegalArgument() {
        ParcelBookingRequest pReq = createSampleParcelRequest();
        ParcelRequest pr = parcelService.createParcelRequest(pReq);
        parcelService.acceptParcelRequest(pr.getId(), traveler.getId());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> parcelService.payEscrow(pr.getId(), traveler.getId()));
        assertTrue(ex.getMessage().contains("Only the sender can initiate escrow payment"));
    }

    @Test
    @DisplayName("Test 20: Pay escrow before request is ACCEPTED throws IllegalStateException")
    void test20_payEscrow_whenNotAccepted_throwsIllegalState() {
        ParcelBookingRequest pReq = createSampleParcelRequest();
        ParcelRequest pr = parcelService.createParcelRequest(pReq); // Status = CREATED

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> parcelService.payEscrow(pr.getId(), sender.getId()));
        assertTrue(ex.getMessage().contains("must be ACCEPTED by traveler before payment"));
    }

    @Test
    @DisplayName("Test 21: Verify pickup before escrow payment throws IllegalStateException")
    void test21_verifyPickup_beforeEscrowPayment_throwsIllegalState() {
        ParcelBookingRequest pReq = createSampleParcelRequest();
        ParcelRequest pr = parcelService.createParcelRequest(pReq);
        parcelService.acceptParcelRequest(pr.getId(), traveler.getId()); // Status = ACCEPTED

        OtpVerificationRequest vReq = new OtpVerificationRequest();
        vReq.setParcelRequestId(pr.getId());
        vReq.setOtp("1234");
        vReq.setPhotoUrl("http://photo.com/pickup.jpg");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> parcelService.verifyPickup(vReq));
        assertTrue(ex.getMessage().contains("must be in PAID_ESCROW status"));
    }

    @Test
    @DisplayName("Test 22: Verify pickup with wrong OTP throws IllegalArgumentException and retains status")
    void test22_verifyPickup_withWrongOtp_doesNotChangeStatus() {
        ParcelBookingRequest pReq = createSampleParcelRequest();
        ParcelRequest pr = parcelService.createParcelRequest(pReq);
        parcelService.acceptParcelRequest(pr.getId(), traveler.getId());
        parcelService.payEscrow(pr.getId(), sender.getId());

        OtpVerificationRequest vReq = new OtpVerificationRequest();
        vReq.setParcelRequestId(pr.getId());
        vReq.setOtp("9999"); // Wrong OTP
        vReq.setPhotoUrl("http://photo.com/pickup.jpg");

        assertThrows(IllegalArgumentException.class, () -> parcelService.verifyPickup(vReq));

        ParcelRequest retained = parcelService.getById(pr.getId());
        assertEquals(ParcelRequest.ParcelStatus.PAID_ESCROW, retained.getStatus());
    }

    @Test
    @DisplayName("Test 23: Verify delivery before pickup throws IllegalStateException")
    void test23_verifyDelivery_beforePickup_throwsIllegalState() {
        ParcelBookingRequest pReq = createSampleParcelRequest();
        ParcelRequest pr = parcelService.createParcelRequest(pReq);
        parcelService.acceptParcelRequest(pr.getId(), traveler.getId());
        parcelService.payEscrow(pr.getId(), sender.getId());
        ParcelRequest paid = parcelService.getById(pr.getId());

        OtpVerificationRequest dReq = new OtpVerificationRequest();
        dReq.setParcelRequestId(pr.getId());
        dReq.setOtp(paid.getDeliveryOtp());
        dReq.setPhotoUrl("http://photo.com/delivery.jpg");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> parcelService.verifyDelivery(dReq));
        assertTrue(ex.getMessage().contains("must be PICKED_UP or IN_TRANSIT"));
    }

    @Test
    @DisplayName("Test 24: Double delivery verification attempt throws IllegalStateException")
    void test24_doubleDeliveryVerification_throwsIllegalState() {
        ParcelBookingRequest pReq = createSampleParcelRequest();
        ParcelRequest pr = parcelService.createParcelRequest(pReq);
        parcelService.acceptParcelRequest(pr.getId(), traveler.getId());
        parcelService.payEscrow(pr.getId(), sender.getId());
        ParcelRequest paid = parcelService.getById(pr.getId());

        // Pickup
        OtpVerificationRequest pVerification = new OtpVerificationRequest();
        pVerification.setParcelRequestId(pr.getId());
        pVerification.setOtp(paid.getPickupOtp());
        pVerification.setPhotoUrl("http://photo.com/pickup.jpg");
        parcelService.verifyPickup(pVerification);

        // First Delivery
        OtpVerificationRequest dVerification = new OtpVerificationRequest();
        dVerification.setParcelRequestId(pr.getId());
        dVerification.setOtp(paid.getDeliveryOtp());
        dVerification.setPhotoUrl("http://photo.com/delivery.jpg");
        parcelService.verifyDelivery(dVerification);

        // Second Delivery attempt
        assertThrows(IllegalStateException.class, () -> parcelService.verifyDelivery(dVerification));
    }

    @Test
    @DisplayName("Test 25: Cancel delivered parcel throws IllegalStateException")
    void test25_cancelDeliveredParcel_throwsIllegalState() {
        ParcelBookingRequest pReq = createSampleParcelRequest();
        ParcelRequest pr = parcelService.createParcelRequest(pReq);
        parcelService.acceptParcelRequest(pr.getId(), traveler.getId());
        parcelService.payEscrow(pr.getId(), sender.getId());
        ParcelRequest paid = parcelService.getById(pr.getId());

        OtpVerificationRequest pVerification = new OtpVerificationRequest();
        pVerification.setParcelRequestId(pr.getId());
        pVerification.setOtp(paid.getPickupOtp());
        pVerification.setPhotoUrl("http://photo.com/pickup.jpg");
        parcelService.verifyPickup(pVerification);

        OtpVerificationRequest dVerification = new OtpVerificationRequest();
        dVerification.setParcelRequestId(pr.getId());
        dVerification.setOtp(paid.getDeliveryOtp());
        dVerification.setPhotoUrl("http://photo.com/delivery.jpg");
        parcelService.verifyDelivery(dVerification);

        // Attempt cancel delivered parcel
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> parcelService.cancelAndRefund(pr.getId(), sender.getId()));
        assertTrue(ex.getMessage().contains("Cannot cancel a completed/delivered parcel request"));
    }

    private ParcelBookingRequest createSampleParcelRequest() {
        ParcelBookingRequest req = new ParcelBookingRequest();
        req.setSenderId(sender.getId());
        req.setTripId(trip.getId());
        req.setGoodsDescription("Sample Parcel");
        req.setDeclaredValue(200.0);
        req.setEstimatedWeightKg(2.0);
        req.setPickupLocation("Delhi");
        req.setDropoffLocation("Jaipur");
        return req;
    }
}
