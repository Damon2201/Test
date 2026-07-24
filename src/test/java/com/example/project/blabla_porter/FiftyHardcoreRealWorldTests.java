package com.example.project.blabla_porter;

import com.example.project.blabla_porter.dto.*;
import com.example.project.blabla_porter.model.*;
import com.example.project.blabla_porter.repository.PaymentRepository;
import com.example.project.blabla_porter.repository.SafetyAlertRepository;
import com.example.project.blabla_porter.service.ChatService;
import com.example.project.blabla_porter.service.ParcelService;
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
    "spring.datasource.url=jdbc:h2:mem:fifty_tests_db;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=true"
})
@Transactional
public class FiftyHardcoreRealWorldTests {

    @Autowired
    private UserService userService;

    @Autowired
    private TripService tripService;

    @Autowired
    private ParcelService parcelService;

    @Autowired
    private RideService rideService;

    @Autowired
    private ChatService chatService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private SafetyAlertRepository safetyAlertRepository;

    private User sender;
    private User traveler;
    private User rider;
    private Trip trip;

    @BeforeEach
    void setUp() {
        // Register Sender
        RegisterRequest sReq = new RegisterRequest();
        sReq.setFullName("Production Sender");
        sReq.setMobileNumber("9811111111");
        sReq.setRole(User.UserRole.SENDER);
        sender = userService.register(sReq);

        // Register & Approve Traveler
        RegisterRequest tReq = new RegisterRequest();
        tReq.setFullName("Production Captain");
        tReq.setMobileNumber("9822222222");
        tReq.setRole(User.UserRole.TRAVELER);
        traveler = userService.register(tReq);

        KycSubmitRequest kycReq = new KycSubmitRequest();
        kycReq.setUserId(traveler.getId());
        kycReq.setAadhaarNumber("1111-2222-3333");
        kycReq.setPanNumber("ABCDE1111Z");
        kycReq.setDrivingLicenceNumber("DL-111");
        kycReq.setRcNumber("KA-01-AA-1111");
        userService.submitKyc(kycReq);
        userService.reviewKyc(traveler.getId(), true);

        // Register Rider
        RegisterRequest rReq = new RegisterRequest();
        rReq.setFullName("Production Rider");
        rReq.setMobileNumber("9833333333");
        rReq.setRole(User.UserRole.RIDER);
        rider = userService.register(rReq);

        userService.addTrustedContact(rider.getId(), "Emergency Contact", "9899999999", "Parent");

        // Create Trip
        TripCreateRequest trReq = new TripCreateRequest();
        trReq.setTravelerId(traveler.getId());
        trReq.setSource("Mumbai");
        trReq.setDestination("Pune");
        trReq.setDepartureTime(LocalDateTime.now().plusDays(1));
        trReq.setAvailableCapacityKg(40.0);
        trReq.setAvailableSeats(3);
        trip = tripService.createTrip(trReq);
    }

    // =========================================================================
    // CATEGORY 1: Auth, Roles & Profile Security (Tests 01 - 05)
    // =========================================================================

    @Test
    @DisplayName("Test 01: Register user with null full name fails validation")
    void test01_registerUser_nullFullName_failsValidation() {
        RegisterRequest req = new RegisterRequest();
        req.setFullName(null);
        req.setMobileNumber("9800000001");
        req.setRole(User.UserRole.RIDER);
        assertThrows(Exception.class, () -> userService.register(req));
    }

    @Test
    @DisplayName("Test 02: Register user with null mobile number fails validation")
    void test02_registerUser_nullMobile_failsValidation() {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Test User");
        req.setMobileNumber(null);
        req.setRole(User.UserRole.RIDER);
        assertThrows(Exception.class, () -> userService.register(req));
    }

    @Test
    @DisplayName("Test 03: Register duplicate mobile number throws IllegalArgumentException")
    void test03_registerUser_duplicateMobile_throwsIllegalArgumentException() {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Duplicate User");
        req.setMobileNumber("9811111111"); // Same as sender
        req.setRole(User.UserRole.SENDER);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> userService.register(req));
        assertTrue(ex.getMessage().contains("already registered"));
    }

    @Test
    @DisplayName("Test 04: Role initialization: Rider is APPROVED, Traveler is NOT_SUBMITTED")
    void test04_registerUser_rolesInitialization_RiderApproved_TravelerNotSubmitted() {
        assertEquals(User.KycStatus.APPROVED, rider.getKycStatus());
        RegisterRequest tReq2 = new RegisterRequest();
        tReq2.setFullName("New Captain");
        tReq2.setMobileNumber("9844444444");
        tReq2.setRole(User.UserRole.TRAVELER);
        User cap = userService.register(tReq2);
        assertEquals(User.KycStatus.NOT_SUBMITTED, cap.getKycStatus());
    }

    @Test
    @DisplayName("Test 05: Fetch non-existent user ID throws RuntimeException")
    void test05_getUser_nonExistentId_throwsRuntimeException() {
        assertThrows(RuntimeException.class, () -> userService.getById(99999L));
    }

    // =========================================================================
    // CATEGORY 2: Captain KYC & Governance Edge Cases (Tests 06 - 10)
    // =========================================================================

    @Test
    @DisplayName("Test 06: Non-Traveler role submitting KYC throws IllegalArgumentException")
    void test06_submitKyc_nonTravelerRole_throwsIllegalArgumentException() {
        KycSubmitRequest kyc = new KycSubmitRequest();
        kyc.setUserId(rider.getId());
        kyc.setAadhaarNumber("1111-1111-1111");
        kyc.setPanNumber("ABCDE1111F");
        kyc.setDrivingLicenceNumber("DL-111");
        kyc.setRcNumber("KA-01");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> userService.submitKyc(kyc));
        assertTrue(ex.getMessage().contains("Only Travelers / Captains require KYC"));
    }

    @Test
    @DisplayName("Test 07: Submit KYC for non-existent user ID throws RuntimeException")
    void test07_submitKyc_nonExistentUserId_throwsRuntimeException() {
        KycSubmitRequest kyc = new KycSubmitRequest();
        kyc.setUserId(88888L);
        kyc.setAadhaarNumber("2222-2222-2222");
        assertThrows(RuntimeException.class, () -> userService.submitKyc(kyc));
    }

    @Test
    @DisplayName("Test 08: Valid KYC submission transitions status to PENDING_APPROVAL")
    void test08_submitKyc_validDocuments_setsStatusPendingApproval() {
        RegisterRequest tReq = new RegisterRequest();
        tReq.setFullName("Captain Pending");
        tReq.setMobileNumber("9855555555");
        tReq.setRole(User.UserRole.TRAVELER);
        User cap = userService.register(tReq);

        KycSubmitRequest kyc = new KycSubmitRequest();
        kyc.setUserId(cap.getId());
        kyc.setAadhaarNumber("3333-3333-3333");
        kyc.setPanNumber("ABCDE3333F");
        kyc.setDrivingLicenceNumber("DL-333");
        kyc.setRcNumber("KA-03");

        User updated = userService.submitKyc(kyc);
        assertEquals(User.KycStatus.PENDING_APPROVAL, updated.getKycStatus());
    }

    @Test
    @DisplayName("Test 09: Admin review approve transitions status to APPROVED")
    void test09_adminReviewKyc_approve_updatesStatusToApproved() {
        RegisterRequest tReq = new RegisterRequest();
        tReq.setFullName("Captain Approve");
        tReq.setMobileNumber("9866666666");
        tReq.setRole(User.UserRole.TRAVELER);
        User cap = userService.register(tReq);

        KycSubmitRequest kyc = new KycSubmitRequest();
        kyc.setUserId(cap.getId());
        kyc.setAadhaarNumber("4444-4444-4444");
        kyc.setPanNumber("ABCDE4444F");
        kyc.setDrivingLicenceNumber("DL-444");
        kyc.setRcNumber("KA-04");
        userService.submitKyc(kyc);

        User approved = userService.reviewKyc(cap.getId(), true);
        assertEquals(User.KycStatus.APPROVED, approved.getKycStatus());
    }

    @Test
    @DisplayName("Test 10: Admin review reject transitions status to REJECTED")
    void test10_adminReviewKyc_reject_updatesStatusToRejected() {
        RegisterRequest tReq = new RegisterRequest();
        tReq.setFullName("Captain Reject");
        tReq.setMobileNumber("9877777777");
        tReq.setRole(User.UserRole.TRAVELER);
        User cap = userService.register(tReq);

        KycSubmitRequest kyc = new KycSubmitRequest();
        kyc.setUserId(cap.getId());
        kyc.setAadhaarNumber("5555-5555-5555");
        kyc.setPanNumber("ABCDE5555F");
        kyc.setDrivingLicenceNumber("DL-555");
        kyc.setRcNumber("KA-05");
        userService.submitKyc(kyc);

        User rejected = userService.reviewKyc(cap.getId(), false);
        assertEquals(User.KycStatus.REJECTED, rejected.getKycStatus());
    }

    // =========================================================================
    // CATEGORY 3: Governance Locks & Trip Declaration Constraints (Tests 11 - 15)
    // =========================================================================

    @Test
    @DisplayName("Test 11: Unapproved Traveler creating trip throws IllegalStateException")
    void test11_createTrip_unapprovedTraveler_throwsIllegalStateException() {
        RegisterRequest tReq = new RegisterRequest();
        tReq.setFullName("Unapproved Captain");
        tReq.setMobileNumber("9888888888");
        tReq.setRole(User.UserRole.TRAVELER);
        User cap = userService.register(tReq);

        TripCreateRequest tr = new TripCreateRequest();
        tr.setTravelerId(cap.getId());
        tr.setSource("Mumbai");
        tr.setDestination("Pune");
        tr.setDepartureTime(LocalDateTime.now().plusDays(1));

        assertThrows(IllegalStateException.class, () -> tripService.createTrip(tr));
    }

    @Test
    @DisplayName("Test 12: Rejected Traveler creating trip throws IllegalStateException")
    void test12_createTrip_rejectedTraveler_throwsIllegalStateException() {
        RegisterRequest tReq = new RegisterRequest();
        tReq.setFullName("Rejected Captain");
        tReq.setMobileNumber("9899999999");
        tReq.setRole(User.UserRole.TRAVELER);
        User cap = userService.register(tReq);

        KycSubmitRequest kyc = new KycSubmitRequest();
        kyc.setUserId(cap.getId());
        kyc.setAadhaarNumber("6666-6666-6666");
        kyc.setPanNumber("ABCDE6666F");
        kyc.setDrivingLicenceNumber("DL-666");
        kyc.setRcNumber("KA-06");
        userService.submitKyc(kyc);
        userService.reviewKyc(cap.getId(), false);

        TripCreateRequest tr = new TripCreateRequest();
        tr.setTravelerId(cap.getId());
        tr.setSource("Mumbai");
        tr.setDestination("Pune");
        tr.setDepartureTime(LocalDateTime.now().plusDays(1));

        assertThrows(IllegalStateException.class, () -> tripService.createTrip(tr));
    }

    @Test
    @DisplayName("Test 13: Non-Traveler role creating trip throws IllegalArgumentException")
    void test13_createTrip_nonTravelerRole_throwsIllegalArgumentException() {
        TripCreateRequest tr = new TripCreateRequest();
        tr.setTravelerId(rider.getId()); // Rider role
        tr.setSource("Mumbai");
        tr.setDestination("Pune");
        tr.setDepartureTime(LocalDateTime.now().plusDays(1));

        assertThrows(IllegalArgumentException.class, () -> tripService.createTrip(tr));
    }

    @Test
    @DisplayName("Test 14: Approved Traveler creates trip in PLANNED status")
    void test14_createTrip_approvedTraveler_succeedsPlannedStatus() {
        assertNotNull(trip.getId());
        assertEquals(Trip.TripStatus.PLANNED, trip.getStatus());
        assertEquals("Mumbai", trip.getSource());
    }

    @Test
    @DisplayName("Test 15: Create trip for non-existent traveler ID throws RuntimeException")
    void test15_createTrip_nonExistentTraveler_throwsRuntimeException() {
        TripCreateRequest tr = new TripCreateRequest();
        tr.setTravelerId(77777L);
        tr.setSource("Mumbai");
        tr.setDestination("Pune");
        tr.setDepartureTime(LocalDateTime.now().plusDays(1));

        assertThrows(RuntimeException.class, () -> tripService.createTrip(tr));
    }

    // =========================================================================
    // CATEGORY 4: Route Matching & Trip Search Mechanics (Tests 16 - 20)
    // =========================================================================

    @Test
    @DisplayName("Test 16: Search trips with case-insensitive partial route matching")
    void test16_searchTrips_caseInsensitivePartialMatching() {
        List<Trip> results = tripService.searchTrips("mum", "pune");
        assertFalse(results.isEmpty());
        assertEquals("Mumbai", results.get(0).getSource());
    }

    @Test
    @DisplayName("Test 17: Search trips with empty query returns all active planned trips")
    void test17_searchTrips_emptyQuery_returnsAllPlannedTrips() {
        List<Trip> results = tripService.searchTrips("", "");
        assertFalse(results.isEmpty());
    }

    @Test
    @DisplayName("Test 18: Search trips for non-matching route returns empty list")
    void test18_searchTrips_nonMatchingRoute_returnsEmptyList() {
        List<Trip> results = tripService.searchTrips("Kolkata", "Chennai");
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Test 19: Get trips by traveler returns correct trip list")
    void test19_getTripsByTraveler_returnsCorrectTripList() {
        List<Trip> tTrips = tripService.getTripsByTraveler(traveler.getId());
        assertEquals(1, tTrips.size());
    }

    @Test
    @DisplayName("Test 20: Fetch non-existent trip ID throws RuntimeException")
    void test20_getTripById_nonExistentId_throwsRuntimeException() {
        assertThrows(RuntimeException.class, () -> tripService.getById(66666L));
    }

    // =========================================================================
    // CATEGORY 5: Parcel Booking & Fare Calculations (Tests 21 - 25)
    // =========================================================================

    @Test
    @DisplayName("Test 21: Create parcel request with zero declared value calculates base fare ($15)")
    void test21_createParcelRequest_baseFare_zeroDeclaredValue() {
        ParcelBookingRequest pr = createSampleParcelBooking();
        pr.setDeclaredValue(0.0);
        ParcelRequest req = parcelService.createParcelRequest(pr);
        assertEquals(15.0, req.getCalculatedFare(), 0.001);
    }

    @Test
    @DisplayName("Test 22: Create parcel request with high value calculates 2% surcharge")
    void test22_createParcelRequest_highDeclaredValue_calculatesExact2PercentSurcharge() {
        ParcelBookingRequest pr = createSampleParcelBooking();
        pr.setDeclaredValue(5000.0); // 2% of 5000 = $100 + $15 = $115
        ParcelRequest req = parcelService.createParcelRequest(pr);
        assertEquals(115.0, req.getCalculatedFare(), 0.001);
    }

    @Test
    @DisplayName("Test 23: Create parcel request against non-planned trip throws IllegalStateException")
    void test23_createParcelRequest_againstNonPlannedTrip_throwsIllegalStateException() {
        trip.setStatus(Trip.TripStatus.COMPLETED);
        ParcelBookingRequest pr = createSampleParcelBooking();
        assertThrows(IllegalStateException.class, () -> parcelService.createParcelRequest(pr));
    }

    @Test
    @DisplayName("Test 24: Create parcel request with non-existent sender ID throws RuntimeException")
    void test24_createParcelRequest_nonExistentSender_throwsRuntimeException() {
        ParcelBookingRequest pr = createSampleParcelBooking();
        pr.setSenderId(99999L);
        assertThrows(RuntimeException.class, () -> parcelService.createParcelRequest(pr));
    }

    @Test
    @DisplayName("Test 25: Create parcel request with non-existent trip ID throws RuntimeException")
    void test25_createParcelRequest_nonExistentTrip_throwsRuntimeException() {
        ParcelBookingRequest pr = createSampleParcelBooking();
        pr.setTripId(99999L);
        assertThrows(RuntimeException.class, () -> parcelService.createParcelRequest(pr));
    }

    // =========================================================================
    // CATEGORY 6: Parcel Acceptance & Unauthorized Interceptions (Tests 26 - 30)
    // =========================================================================

    @Test
    @DisplayName("Test 26: Accept parcel request by designated traveler succeeds")
    void test26_acceptParcelRequest_validTraveler_succeeds() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelBooking());
        ParcelRequest accepted = parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        assertEquals(ParcelRequest.ParcelStatus.ACCEPTED, accepted.getStatus());
    }

    @Test
    @DisplayName("Test 27: Accept parcel request by unauthorized traveler throws IllegalArgumentException")
    void test27_acceptParcelRequest_unauthorizedTraveler_throwsIllegalArgumentException() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelBooking());
        assertThrows(IllegalArgumentException.class, () -> parcelService.acceptParcelRequest(req.getId(), 8888L));
    }

    @Test
    @DisplayName("Test 28: Accept already accepted parcel request throws IllegalStateException")
    void test28_acceptParcelRequest_alreadyAccepted_throwsIllegalStateException() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelBooking());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        assertThrows(IllegalStateException.class, () -> parcelService.acceptParcelRequest(req.getId(), traveler.getId()));
    }

    @Test
    @DisplayName("Test 29: Accept non-existent parcel request throws RuntimeException")
    void test29_acceptParcelRequest_nonExistentParcel_throwsRuntimeException() {
        assertThrows(RuntimeException.class, () -> parcelService.acceptParcelRequest(7777L, traveler.getId()));
    }

    @Test
    @DisplayName("Test 30: Fetch parcel requests by sender and trip returns correct lists")
    void test30_getParcelRequestsBySender_andByTrip_returnsList() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelBooking());
        assertEquals(1, parcelService.getRequestsBySender(sender.getId()).size());
        assertEquals(1, parcelService.getRequestsByTrip(trip.getId()).size());
    }

    // =========================================================================
    // CATEGORY 7: Escrow Payouts & Financial Lifecycle (Tests 31 - 35)
    // =========================================================================

    @Test
    @DisplayName("Test 31: Pay escrow generates 4-digit OTPs and holds payment")
    void test31_payEscrow_validSender_generates4DigitOtps_andHoldsPayment() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelBooking());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        Payment payment = parcelService.payEscrow(req.getId(), sender.getId());

        assertEquals(Payment.EscrowStatus.HELD, payment.getStatus());
        ParcelRequest paidReq = parcelService.getById(req.getId());
        assertEquals(ParcelRequest.ParcelStatus.PAID_ESCROW, paidReq.getStatus());
        assertEquals(4, paidReq.getPickupOtp().length());
        assertEquals(4, paidReq.getDeliveryOtp().length());
    }

    @Test
    @DisplayName("Test 32: Pay escrow by unauthorized user throws IllegalArgumentException")
    void test32_payEscrow_unauthorizedSender_throwsIllegalArgumentException() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelBooking());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        assertThrows(IllegalArgumentException.class, () -> parcelService.payEscrow(req.getId(), traveler.getId()));
    }

    @Test
    @DisplayName("Test 33: Pay escrow before request is ACCEPTED throws IllegalStateException")
    void test33_payEscrow_unacceptedRequest_throwsIllegalStateException() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelBooking()); // CREATED
        assertThrows(IllegalStateException.class, () -> parcelService.payEscrow(req.getId(), sender.getId()));
    }

    @Test
    @DisplayName("Test 34: Double escrow payment attempt throws IllegalStateException")
    void test34_payEscrow_doublePaymentAttempt_throwsIllegalStateException() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelBooking());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        parcelService.payEscrow(req.getId(), sender.getId());
        assertThrows(IllegalStateException.class, () -> parcelService.payEscrow(req.getId(), sender.getId()));
    }

    @Test
    @DisplayName("Test 35: Cancel parcel request before pickup refunds escrow payment")
    void test35_cancelParcelRequest_beforePickup_refundsEscrow() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelBooking());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        parcelService.payEscrow(req.getId(), sender.getId());

        ParcelRequest cancelled = parcelService.cancelAndRefund(req.getId(), sender.getId());
        assertEquals(ParcelRequest.ParcelStatus.CANCELLED, cancelled.getStatus());
        Payment payment = paymentRepository.findByParcelRequestId(req.getId()).orElseThrow();
        assertEquals(Payment.EscrowStatus.REFUNDED, payment.getStatus());
    }

    // =========================================================================
    // CATEGORY 8: Dual OTP & 3-Point Photo Handover Security (Tests 36 - 40)
    // =========================================================================

    @Test
    @DisplayName("Test 36: Verify pickup before escrow payment throws IllegalStateException")
    void test36_verifyPickup_unpaidRequest_throwsIllegalStateException() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelBooking());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId()); // ACCEPTED

        OtpVerificationRequest v = new OtpVerificationRequest();
        v.setParcelRequestId(req.getId());
        v.setOtp("1234");
        v.setPhotoUrl("http://photo.com/pickup.jpg");

        assertThrows(IllegalStateException.class, () -> parcelService.verifyPickup(v));
    }

    @Test
    @DisplayName("Test 37: Verify pickup with wrong OTP throws IllegalArgumentException and retains status")
    void test37_verifyPickup_invalidOtp_throwsIllegalArgumentException_andRetainsStatus() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelBooking());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        parcelService.payEscrow(req.getId(), sender.getId());

        OtpVerificationRequest v = new OtpVerificationRequest();
        v.setParcelRequestId(req.getId());
        v.setOtp("0000"); // Wrong OTP
        v.setPhotoUrl("http://photo.com/pickup.jpg");

        assertThrows(IllegalArgumentException.class, () -> parcelService.verifyPickup(v));
        assertEquals(ParcelRequest.ParcelStatus.PAID_ESCROW, parcelService.getById(req.getId()).getStatus());
    }

    @Test
    @DisplayName("Test 38: Verify pickup with valid OTP and photo updates status to PICKED_UP")
    void test38_verifyPickup_validOtpAndPhoto_updatesStatusToPickedUp() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelBooking());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        parcelService.payEscrow(req.getId(), sender.getId());
        ParcelRequest paid = parcelService.getById(req.getId());

        OtpVerificationRequest v = new OtpVerificationRequest();
        v.setParcelRequestId(req.getId());
        v.setOtp(paid.getPickupOtp());
        v.setPhotoUrl("http://photo.com/pickup.jpg");

        ParcelRequest pickedUp = parcelService.verifyPickup(v);
        assertEquals(ParcelRequest.ParcelStatus.PICKED_UP, pickedUp.getStatus());
        assertEquals("http://photo.com/pickup.jpg", pickedUp.getPickupPhotoUrl());
    }

    @Test
    @DisplayName("Test 39: Verify delivery before pickup throws IllegalStateException")
    void test39_verifyDelivery_beforePickup_throwsIllegalStateException() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelBooking());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        parcelService.payEscrow(req.getId(), sender.getId());
        ParcelRequest paid = parcelService.getById(req.getId());

        OtpVerificationRequest v = new OtpVerificationRequest();
        v.setParcelRequestId(req.getId());
        v.setOtp(paid.getDeliveryOtp());
        v.setPhotoUrl("http://photo.com/delivery.jpg");

        assertThrows(IllegalStateException.class, () -> parcelService.verifyDelivery(v));
    }

    @Test
    @DisplayName("Test 40: Verify delivery with valid OTP releases Escrow funds to traveler")
    void test40_verifyDelivery_validOtpAndPhoto_updatesStatusToDelivered_andReleasesEscrow() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelBooking());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        parcelService.payEscrow(req.getId(), sender.getId());
        ParcelRequest paid = parcelService.getById(req.getId());

        // Pickup
        OtpVerificationRequest p = new OtpVerificationRequest();
        p.setParcelRequestId(req.getId());
        p.setOtp(paid.getPickupOtp());
        p.setPhotoUrl("http://photo.com/pickup.jpg");
        parcelService.verifyPickup(p);

        // Delivery
        OtpVerificationRequest d = new OtpVerificationRequest();
        d.setParcelRequestId(req.getId());
        d.setOtp(paid.getDeliveryOtp());
        d.setPhotoUrl("http://photo.com/delivery.jpg");

        ParcelRequest delivered = parcelService.verifyDelivery(d);
        assertEquals(ParcelRequest.ParcelStatus.DELIVERED, delivered.getStatus());
        assertEquals("http://photo.com/delivery.jpg", delivered.getDeliveryPhotoUrl());

        Payment payment = paymentRepository.findByParcelRequestId(req.getId()).orElseThrow();
        assertEquals(Payment.EscrowStatus.RELEASED, payment.getStatus());
    }

    // =========================================================================
    // CATEGORY 9: Post-Handover Protection & In-App Chat Security (Tests 41 - 45)
    // =========================================================================

    @Test
    @DisplayName("Test 41: Double delivery verification attempt throws IllegalStateException")
    void test41_doubleDeliveryAttempt_throwsIllegalStateException() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelBooking());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        parcelService.payEscrow(req.getId(), sender.getId());
        ParcelRequest paid = parcelService.getById(req.getId());

        OtpVerificationRequest p = new OtpVerificationRequest();
        p.setParcelRequestId(req.getId());
        p.setOtp(paid.getPickupOtp());
        p.setPhotoUrl("http://photo.com/pickup.jpg");
        parcelService.verifyPickup(p);

        OtpVerificationRequest d = new OtpVerificationRequest();
        d.setParcelRequestId(req.getId());
        d.setOtp(paid.getDeliveryOtp());
        d.setPhotoUrl("http://photo.com/delivery.jpg");
        parcelService.verifyDelivery(d);

        assertThrows(IllegalStateException.class, () -> parcelService.verifyDelivery(d));
    }

    @Test
    @DisplayName("Test 42: Cancel delivered parcel throws IllegalStateException")
    void test42_cancelDeliveredParcel_throwsIllegalStateException() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelBooking());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        parcelService.payEscrow(req.getId(), sender.getId());
        ParcelRequest paid = parcelService.getById(req.getId());

        OtpVerificationRequest p = new OtpVerificationRequest();
        p.setParcelRequestId(req.getId());
        p.setOtp(paid.getPickupOtp());
        p.setPhotoUrl("http://photo.com/pickup.jpg");
        parcelService.verifyPickup(p);

        OtpVerificationRequest d = new OtpVerificationRequest();
        d.setParcelRequestId(req.getId());
        d.setOtp(paid.getDeliveryOtp());
        d.setPhotoUrl("http://photo.com/delivery.jpg");
        parcelService.verifyDelivery(d);

        assertThrows(IllegalStateException.class, () -> parcelService.cancelAndRefund(req.getId(), sender.getId()));
    }

    @Test
    @DisplayName("Test 43: Send chat message before parcel request acceptance throws IllegalStateException")
    void test43_sendChatMessage_beforeAcceptance_throwsIllegalStateException() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelBooking()); // CREATED
        ChatMessageRequest msg = new ChatMessageRequest();
        msg.setSenderUserId(sender.getId());
        msg.setMessage("Hello");

        assertThrows(IllegalStateException.class, () -> chatService.sendMessage(req.getId(), msg));
    }

    @Test
    @DisplayName("Test 44: Send chat message after acceptance saves message successfully")
    void test44_sendChatMessage_afterAcceptance_savesMessage() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelBooking());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());

        ChatMessageRequest msg = new ChatMessageRequest();
        msg.setSenderUserId(sender.getId());
        msg.setMessage("Where can we meet?");

        ChatMessage chat = chatService.sendMessage(req.getId(), msg);
        assertNotNull(chat.getId());
        assertEquals("Where can we meet?", chat.getMessage());
    }

    @Test
    @DisplayName("Test 45: Get chat history returns timestamp-ordered message list")
    void test45_getChatHistory_returnsOrderedMessages() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelBooking());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());

        ChatMessageRequest msg1 = new ChatMessageRequest();
        msg1.setSenderUserId(sender.getId());
        msg1.setMessage("Msg 1");
        chatService.sendMessage(req.getId(), msg1);

        ChatMessageRequest msg2 = new ChatMessageRequest();
        msg2.setSenderUserId(traveler.getId());
        msg2.setMessage("Msg 2");
        chatService.sendMessage(req.getId(), msg2);

        List<ChatMessage> history = chatService.getChatHistory(req.getId());
        assertEquals(2, history.size());
        assertEquals("Msg 1", history.get(0).getMessage());
        assertEquals("Msg 2", history.get(1).getMessage());
    }

    // =========================================================================
    // CATEGORY 10: Ride Booking, Safety Buffer & Escalation Ladder (Tests 46 - 50)
    // =========================================================================

    @Test
    @DisplayName("Test 46: Request ride calculates dynamic buffer minutes max(5, 20% duration)")
    void test46_requestRide_calculatesDynamicBuffer_max5Min20Percent() {
        RideBookingRequest rReq = new RideBookingRequest();
        rReq.setRiderId(rider.getId());
        rReq.setTripId(trip.getId());
        rReq.setPickupLocation("Mumbai");
        rReq.setDropoffLocation("Pune");
        rReq.setSafetyModeEnabled(true);
        rReq.setEstimatedDurationMinutes(60); // 20% of 60 = 12 mins buffer

        RideRequest ride = rideService.requestRide(rReq);
        assertEquals(12, ride.getBufferMinutes());
    }

    @Test
    @DisplayName("Test 47: Accept ride request by unauthorized traveler throws IllegalArgumentException")
    void test47_acceptRide_unauthorizedTraveler_throwsIllegalArgument() {
        RideBookingRequest rReq = createSampleRideBooking();
        RideRequest ride = rideService.requestRide(rReq);
        assertThrows(IllegalArgumentException.class, () -> rideService.acceptRide(ride.getId(), 9999L));
    }

    @Test
    @DisplayName("Test 48: Complete ride resolves all active safety alerts")
    void test48_rideProgression_startAndComplete_autoResolvesSafetyAlerts() {
        RideBookingRequest rReq = createSampleRideBooking();
        RideRequest ride = rideService.requestRide(rReq);
        rideService.acceptRide(ride.getId(), traveler.getId());
        rideService.startRide(ride.getId());

        SafetyAlert alert = rideService.triggerSafetyEscalation(ride.getId(), "Location 1", SafetyAlert.EscalationStage.STAGE_1_SILENT_PING);
        assertEquals(SafetyAlert.AlertStatus.TRIGGERED, alert.getStatus());

        rideService.completeRide(ride.getId());
        SafetyAlert resolvedAlert = safetyAlertRepository.findById(alert.getId()).orElseThrow();
        assertEquals(SafetyAlert.AlertStatus.RESOLVED, resolvedAlert.getStatus());
    }

    @Test
    @DisplayName("Test 49: Trigger safety escalation ladder stages 1, 2, and 3")
    void test49_safetyEscalation_stage1_stage2_stage3_progression() {
        RideBookingRequest rReq = createSampleRideBooking();
        RideRequest ride = rideService.requestRide(rReq);
        rideService.acceptRide(ride.getId(), traveler.getId());
        rideService.startRide(ride.getId());

        SafetyAlert s1 = rideService.triggerSafetyEscalation(ride.getId(), "Loc 1", SafetyAlert.EscalationStage.STAGE_1_SILENT_PING);
        SafetyAlert s2 = rideService.triggerSafetyEscalation(ride.getId(), "Loc 2", SafetyAlert.EscalationStage.STAGE_2_IN_APP_CHECKIN);
        SafetyAlert s3 = rideService.triggerSafetyEscalation(ride.getId(), "Loc 3", SafetyAlert.EscalationStage.STAGE_3_TRUSTED_CONTACT_ALERT);

        assertEquals(SafetyAlert.EscalationStage.STAGE_1_SILENT_PING, s1.getEscalationStage());
        assertEquals(SafetyAlert.EscalationStage.STAGE_2_IN_APP_CHECKIN, s2.getEscalationStage());
        assertEquals(SafetyAlert.EscalationStage.STAGE_3_TRUSTED_CONTACT_ALERT, s3.getEscalationStage());
        assertEquals(SafetyAlert.AlertStatus.ESCALATED, s3.getStatus());
    }

    @Test
    @DisplayName("Test 50: Safety checkin rider responds safe resolves alert; unsafe escalates to Stage 3")
    void test50_safetyCheckin_riderRespondsSafe_resolvesAlert_unsafe_escalatesToStage3() {
        RideBookingRequest rReq = createSampleRideBooking();
        RideRequest ride = rideService.requestRide(rReq);
        rideService.acceptRide(ride.getId(), traveler.getId());
        rideService.startRide(ride.getId());

        SafetyAlert alert1 = rideService.triggerSafetyEscalation(ride.getId(), "Loc A", SafetyAlert.EscalationStage.STAGE_2_IN_APP_CHECKIN);
        SafetyAlert ack1 = rideService.acknowledgeCheckin(alert1.getId(), true);
        assertEquals(SafetyAlert.AlertStatus.RESOLVED, ack1.getStatus());

        SafetyAlert alert2 = rideService.triggerSafetyEscalation(ride.getId(), "Loc B", SafetyAlert.EscalationStage.STAGE_2_IN_APP_CHECKIN);
        SafetyAlert ack2 = rideService.acknowledgeCheckin(alert2.getId(), false);
        assertEquals(SafetyAlert.AlertStatus.ESCALATED, ack2.getStatus());
        assertEquals(SafetyAlert.EscalationStage.STAGE_3_TRUSTED_CONTACT_ALERT, ack2.getEscalationStage());
    }

    private ParcelBookingRequest createSampleParcelBooking() {
        ParcelBookingRequest pr = new ParcelBookingRequest();
        pr.setSenderId(sender.getId());
        pr.setTripId(trip.getId());
        pr.setGoodsDescription("Documents");
        pr.setDeclaredValue(100.0);
        pr.setEstimatedWeightKg(1.0);
        pr.setPickupLocation("Mumbai");
        pr.setDropoffLocation("Pune");
        return pr;
    }

    private RideBookingRequest createSampleRideBooking() {
        RideBookingRequest rr = new RideBookingRequest();
        rr.setRiderId(rider.getId());
        rr.setTripId(trip.getId());
        rr.setPickupLocation("Mumbai");
        rr.setDropoffLocation("Pune");
        rr.setSafetyModeEnabled(true);
        rr.setEstimatedDurationMinutes(30);
        return rr;
    }
}
