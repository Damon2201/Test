package com.example.project.blabla_porter;

import com.example.project.blabla_porter.dto.*;
import com.example.project.blabla_porter.model.*;
import com.example.project.blabla_porter.repository.*;
import com.example.project.blabla_porter.service.*;

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
    "spring.datasource.url=jdbc:h2:mem:two_hundred_tests_db;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=true"
})
@Transactional
public class TwoHundredRealTimeProductionTests {

    @Autowired private UserService userService;
    @Autowired private TripService tripService;
    @Autowired private ParcelService parcelService;
    @Autowired private RideService rideService;
    @Autowired private ChatService chatService;
    @Autowired private TrustAndDisputeService trustAndDisputeService;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private SafetyAlertRepository safetyAlertRepository;

    private User sender;
    private User traveler;
    private User rider;
    private Trip trip;

    @BeforeEach
    void setUp() {
        RegisterRequest sReq = new RegisterRequest();
        sReq.setFullName("Realtime Sender");
        sReq.setMobileNumber("9900011111");
        sReq.setRole(User.UserRole.SENDER);
        sender = userService.register(sReq);

        RegisterRequest tReq = new RegisterRequest();
        tReq.setFullName("Realtime Captain");
        tReq.setMobileNumber("9900022222");
        tReq.setRole(User.UserRole.TRAVELER);
        traveler = userService.register(tReq);

        KycSubmitRequest kycReq = new KycSubmitRequest();
        kycReq.setUserId(traveler.getId());
        kycReq.setAadhaarNumber("9999-0000-1111");
        kycReq.setPanNumber("ABCDE9999Z");
        kycReq.setDrivingLicenceNumber("DL-999");
        kycReq.setRcNumber("KA-01-9999");
        userService.submitKyc(kycReq);
        userService.reviewKyc(traveler.getId(), true);

        RegisterRequest rReq = new RegisterRequest();
        rReq.setFullName("Realtime Rider");
        rReq.setMobileNumber("9900033333");
        rReq.setRole(User.UserRole.RIDER);
        rider = userService.register(rReq);

        userService.addTrustedContact(rider.getId(), "Family Member", "9900044444", "Parent");

        TripCreateRequest trReq = new TripCreateRequest();
        trReq.setTravelerId(traveler.getId());
        trReq.setSource("Bengaluru");
        trReq.setDestination("Hyderabad");
        trReq.setDepartureTime(LocalDateTime.now().plusDays(1));
        trReq.setAvailableCapacityKg(50.0);
        trReq.setAvailableSeats(4);
        trip = tripService.createTrip(trReq);
    }

    // =========================================================================
    // CATEGORY 1: Auth & User Identity (001 - 020)
    // =========================================================================

    @Test void test001_registerRider_autoApprovedKyc() { assertEquals(User.KycStatus.APPROVED, rider.getKycStatus()); }
    @Test void test002_registerSender_autoApprovedKyc() { assertEquals(User.KycStatus.APPROVED, sender.getKycStatus()); }
    @Test void test003_registerTraveler_requiresKyc_notSubmitted() {
        RegisterRequest r = new RegisterRequest(); r.setFullName("T2"); r.setMobileNumber("9900000002"); r.setRole(User.UserRole.TRAVELER);
        assertEquals(User.KycStatus.NOT_SUBMITTED, userService.register(r).getKycStatus());
    }
    @Test void test004_registerAdmin_autoApprovedKyc() {
        RegisterRequest r = new RegisterRequest(); r.setFullName("Admin"); r.setMobileNumber("9900000004"); r.setRole(User.UserRole.ADMIN);
        assertEquals(User.KycStatus.APPROVED, userService.register(r).getKycStatus());
    }
    @Test void test005_registerUser_nullName_validationError() {
        RegisterRequest r = new RegisterRequest(); r.setMobileNumber("9900000005"); r.setRole(User.UserRole.RIDER);
        assertThrows(Exception.class, () -> userService.register(r));
    }
    @Test void test006_registerUser_blankName_validationError() {
        RegisterRequest r = new RegisterRequest(); r.setFullName(""); r.setMobileNumber("9900000006"); r.setRole(User.UserRole.RIDER);
        assertThrows(Exception.class, () -> userService.register(r));
    }
    @Test void test007_registerUser_nullMobile_validationError() {
        RegisterRequest r = new RegisterRequest(); r.setFullName("U7"); r.setRole(User.UserRole.RIDER);
        assertThrows(Exception.class, () -> userService.register(r));
    }
    @Test void test008_registerUser_blankMobile_validationError() {
        RegisterRequest r = new RegisterRequest(); r.setFullName("U8"); r.setMobileNumber(""); r.setRole(User.UserRole.RIDER);
        assertThrows(Exception.class, () -> userService.register(r));
    }
    @Test void test009_registerUser_duplicateMobile_throwsIllegalArgumentException() {
        RegisterRequest r = new RegisterRequest(); r.setFullName("Dup"); r.setMobileNumber("9900011111"); r.setRole(User.UserRole.SENDER);
        assertThrows(IllegalArgumentException.class, () -> userService.register(r));
    }
    @Test void test010_registerUser_uniqueMobile_succeeds() {
        RegisterRequest r = new RegisterRequest(); r.setFullName("U10"); r.setMobileNumber("9900000010"); r.setRole(User.UserRole.RIDER);
        assertNotNull(userService.register(r).getId());
    }
    @Test void test011_getUser_validId_returnsUser() { assertNotNull(userService.getById(sender.getId())); }
    @Test void test012_getUser_nonExistentId_throwsRuntimeException() { assertThrows(RuntimeException.class, () -> userService.getById(99999L)); }
    @Test void test013_registerUser_defaultRatings_fiveStarsZeroCount() {
        assertEquals(5.0, sender.getAverageRating());
        assertEquals(0, sender.getTotalRatingsCount());
    }
    @Test void test014_trustedContact_addValidContact_succeeds() {
        TrustedContact tc = userService.addTrustedContact(sender.getId(), "Sister", "9900055555", "Sibling");
        assertNotNull(tc.getId());
    }
    @Test void test015_trustedContact_addMultipleContacts_returnsList() {
        userService.addTrustedContact(sender.getId(), "Contact 1", "9900055551", "Friend");
        userService.addTrustedContact(sender.getId(), "Contact 2", "9900055552", "Colleague");
        assertEquals(2, userService.getTrustedContacts(sender.getId()).size());
    }
    @Test void test016_trustedContact_nonExistentUser_throwsRuntimeException() {
        assertThrows(RuntimeException.class, () -> userService.addTrustedContact(88888L, "Name", "9900000000", "Rel"));
    }
    @Test void test017_trustedContact_emptyName_throwsException() {
        assertThrows(Exception.class, () -> userService.addTrustedContact(sender.getId(), "", "9900055555", "Friend"));
    }
    @Test void test018_trustedContact_emptyPhone_throwsException() {
        assertThrows(Exception.class, () -> userService.addTrustedContact(sender.getId(), "Name", "", "Friend"));
    }
    @Test void test019_trustedContact_isolationBetweenUsers() {
        userService.addTrustedContact(sender.getId(), "Sender Contact", "9900011112", "Friend");
        userService.addTrustedContact(rider.getId(), "Rider Contact", "9900033334", "Relative");
        assertEquals(1, userService.getTrustedContacts(sender.getId()).size());
        assertEquals("Sender Contact", userService.getTrustedContacts(sender.getId()).get(0).getContactName());
    }
    @Test void test020_user_updateBankAccount_updatesDetails() {
        sender.setBankAccountDetails("HDFC0001234");
        assertEquals("HDFC0001234", sender.getBankAccountDetails());
    }

    // =========================================================================
    // CATEGORY 2: Captain KYC Governance (021 - 040)
    // =========================================================================

    @Test void test021_submitKyc_travelerRole_pendingApprovalStatus() {
        RegisterRequest r = new RegisterRequest(); r.setFullName("Cap21"); r.setMobileNumber("9900000021"); r.setRole(User.UserRole.TRAVELER);
        User cap = userService.register(r);
        KycSubmitRequest kyc = createSampleKyc(cap.getId());
        assertEquals(User.KycStatus.PENDING_APPROVAL, userService.submitKyc(kyc).getKycStatus());
    }
    @Test void test022_submitKyc_riderRole_throwsIllegalArgumentException() {
        KycSubmitRequest kyc = createSampleKyc(rider.getId());
        assertThrows(IllegalArgumentException.class, () -> userService.submitKyc(kyc));
    }
    @Test void test023_submitKyc_senderRole_throwsIllegalArgumentException() {
        KycSubmitRequest kyc = createSampleKyc(sender.getId());
        assertThrows(IllegalArgumentException.class, () -> userService.submitKyc(kyc));
    }
    @Test void test024_submitKyc_adminRole_throwsIllegalArgumentException() {
        RegisterRequest r = new RegisterRequest(); r.setFullName("Admin24"); r.setMobileNumber("9900000024"); r.setRole(User.UserRole.ADMIN);
        User adm = userService.register(r);
        KycSubmitRequest kyc = createSampleKyc(adm.getId());
        assertThrows(IllegalArgumentException.class, () -> userService.submitKyc(kyc));
    }
    @Test void test025_submitKyc_nonExistentUser_throwsRuntimeException() {
        KycSubmitRequest kyc = createSampleKyc(77777L);
        assertThrows(RuntimeException.class, () -> userService.submitKyc(kyc));
    }
    @Test void test026_submitKyc_missingAadhaar_throwsException() {
        RegisterRequest r = new RegisterRequest(); r.setFullName("Cap26"); r.setMobileNumber("9900000026"); r.setRole(User.UserRole.TRAVELER);
        User cap = userService.register(r);
        KycSubmitRequest kyc = createSampleKyc(cap.getId()); kyc.setAadhaarNumber(null);
        assertThrows(Exception.class, () -> userService.submitKyc(kyc));
    }
    @Test void test027_submitKyc_missingPan_throwsException() {
        RegisterRequest r = new RegisterRequest(); r.setFullName("Cap27"); r.setMobileNumber("9900000027"); r.setRole(User.UserRole.TRAVELER);
        User cap = userService.register(r);
        KycSubmitRequest kyc = createSampleKyc(cap.getId()); kyc.setPanNumber(null);
        assertThrows(Exception.class, () -> userService.submitKyc(kyc));
    }
    @Test void test028_submitKyc_missingDL_throwsException() {
        RegisterRequest r = new RegisterRequest(); r.setFullName("Cap28"); r.setMobileNumber("9900000028"); r.setRole(User.UserRole.TRAVELER);
        User cap = userService.register(r);
        KycSubmitRequest kyc = createSampleKyc(cap.getId()); kyc.setDrivingLicenceNumber(null);
        assertThrows(Exception.class, () -> userService.submitKyc(kyc));
    }
    @Test void test029_submitKyc_missingRC_throwsException() {
        RegisterRequest r = new RegisterRequest(); r.setFullName("Cap29"); r.setMobileNumber("9900000029"); r.setRole(User.UserRole.TRAVELER);
        User cap = userService.register(r);
        KycSubmitRequest kyc = createSampleKyc(cap.getId()); kyc.setRcNumber(null);
        assertThrows(Exception.class, () -> userService.submitKyc(kyc));
    }
    @Test void test030_reviewKyc_approve_updatesStatusToApproved() {
        RegisterRequest r = new RegisterRequest(); r.setFullName("Cap30"); r.setMobileNumber("9900000030"); r.setRole(User.UserRole.TRAVELER);
        User cap = userService.register(r);
        userService.submitKyc(createSampleKyc(cap.getId()));
        assertEquals(User.KycStatus.APPROVED, userService.reviewKyc(cap.getId(), true).getKycStatus());
    }
    @Test void test031_reviewKyc_reject_updatesStatusToRejected() {
        RegisterRequest r = new RegisterRequest(); r.setFullName("Cap31"); r.setMobileNumber("9900000031"); r.setRole(User.UserRole.TRAVELER);
        User cap = userService.register(r);
        userService.submitKyc(createSampleKyc(cap.getId()));
        assertEquals(User.KycStatus.REJECTED, userService.reviewKyc(cap.getId(), false).getKycStatus());
    }
    @Test void test032_reviewKyc_nonPendingUser_approved_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () -> userService.reviewKyc(traveler.getId(), true));
    }
    @Test void test033_reviewKyc_nonPendingUser_rejected_throwsIllegalStateException() {
        RegisterRequest r = new RegisterRequest(); r.setFullName("Cap33"); r.setMobileNumber("9900000033"); r.setRole(User.UserRole.TRAVELER);
        User cap = userService.register(r);
        userService.submitKyc(createSampleKyc(cap.getId()));
        userService.reviewKyc(cap.getId(), false); // REJECTED
        assertThrows(IllegalStateException.class, () -> userService.reviewKyc(cap.getId(), true));
    }
    @Test void test034_reviewKyc_nonPendingUser_notSubmitted_throwsIllegalStateException() {
        RegisterRequest r = new RegisterRequest(); r.setFullName("Cap34"); r.setMobileNumber("9900000034"); r.setRole(User.UserRole.TRAVELER);
        User cap = userService.register(r);
        assertThrows(IllegalStateException.class, () -> userService.reviewKyc(cap.getId(), true));
    }
    @Test void test035_reviewKyc_nonExistentUser_throwsRuntimeException() {
        assertThrows(RuntimeException.class, () -> userService.reviewKyc(66666L, true));
    }
    @Test void test036_resubmitKyc_afterRejection_allowsPendingApproval() {
        RegisterRequest r = new RegisterRequest(); r.setFullName("Cap36"); r.setMobileNumber("9900000036"); r.setRole(User.UserRole.TRAVELER);
        User cap = userService.register(r);
        userService.submitKyc(createSampleKyc(cap.getId()));
        userService.reviewKyc(cap.getId(), false); // REJECTED
        // Resubmit
        User resubmitted = userService.submitKyc(createSampleKyc(cap.getId()));
        assertEquals(User.KycStatus.PENDING_APPROVAL, resubmitted.getKycStatus());
    }
    @Test void test037_resubmittedKyc_reApprovedByAdmin() {
        RegisterRequest r = new RegisterRequest(); r.setFullName("Cap37"); r.setMobileNumber("9900000037"); r.setRole(User.UserRole.TRAVELER);
        User cap = userService.register(r);
        userService.submitKyc(createSampleKyc(cap.getId()));
        userService.reviewKyc(cap.getId(), false);
        userService.submitKyc(createSampleKyc(cap.getId()));
        User reapproved = userService.reviewKyc(cap.getId(), true);
        assertEquals(User.KycStatus.APPROVED, reapproved.getKycStatus());
    }
    @Test void test038_kycStatus_notSubmitted_cannotDeclareTrips() {
        RegisterRequest r = new RegisterRequest(); r.setFullName("Cap38"); r.setMobileNumber("9900000038"); r.setRole(User.UserRole.TRAVELER);
        User cap = userService.register(r);
        TripCreateRequest tr = createSampleTripRequest(cap.getId());
        assertThrows(IllegalStateException.class, () -> tripService.createTrip(tr));
    }
    @Test void test039_kycStatus_pendingApproval_cannotDeclareTrips() {
        RegisterRequest r = new RegisterRequest(); r.setFullName("Cap39"); r.setMobileNumber("9900000039"); r.setRole(User.UserRole.TRAVELER);
        User cap = userService.register(r);
        userService.submitKyc(createSampleKyc(cap.getId()));
        TripCreateRequest tr = createSampleTripRequest(cap.getId());
        assertThrows(IllegalStateException.class, () -> tripService.createTrip(tr));
    }
    @Test void test040_kycStatus_rejected_cannotDeclareTrips() {
        RegisterRequest r = new RegisterRequest(); r.setFullName("Cap40"); r.setMobileNumber("9900000040"); r.setRole(User.UserRole.TRAVELER);
        User cap = userService.register(r);
        userService.submitKyc(createSampleKyc(cap.getId()));
        userService.reviewKyc(cap.getId(), false);
        TripCreateRequest tr = createSampleTripRequest(cap.getId());
        assertThrows(IllegalStateException.class, () -> tripService.createTrip(tr));
    }

    // =========================================================================
    // CATEGORY 3: Traveler Trip Declarations & Route Search (041 - 060)
    // =========================================================================

    @Test void test041_createTrip_approvedTraveler_createsPlannedTrip() {
        assertNotNull(trip.getId());
        assertEquals(Trip.TripStatus.PLANNED, trip.getStatus());
    }
    @Test void test042_createTrip_unapprovedTraveler_throwsIllegalStateException() {
        RegisterRequest r = new RegisterRequest(); r.setFullName("Cap42"); r.setMobileNumber("9900000042"); r.setRole(User.UserRole.TRAVELER);
        User cap = userService.register(r);
        assertThrows(IllegalStateException.class, () -> tripService.createTrip(createSampleTripRequest(cap.getId())));
    }
    @Test void test043_createTrip_nonTravelerRole_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> tripService.createTrip(createSampleTripRequest(rider.getId())));
    }
    @Test void test044_createTrip_nonExistentTraveler_throwsRuntimeException() {
        assertThrows(RuntimeException.class, () -> tripService.createTrip(createSampleTripRequest(55555L)));
    }
    @Test void test045_createTrip_nullSource_throwsException() {
        TripCreateRequest tr = createSampleTripRequest(traveler.getId()); tr.setSource(null);
        assertThrows(Exception.class, () -> tripService.createTrip(tr));
    }
    @Test void test046_createTrip_nullDestination_throwsException() {
        TripCreateRequest tr = createSampleTripRequest(traveler.getId()); tr.setDestination(null);
        assertThrows(Exception.class, () -> tripService.createTrip(tr));
    }
    @Test void test047_createTrip_nullDepartureTime_throwsException() {
        TripCreateRequest tr = createSampleTripRequest(traveler.getId()); tr.setDepartureTime(null);
        assertThrows(Exception.class, () -> tripService.createTrip(tr));
    }
    @Test void test048_searchTrips_matchingSourceAndDest_returnsTrips() {
        List<Trip> res = tripService.searchTrips("Bengaluru", "Hyderabad");
        assertEquals(1, res.size());
    }
    @Test void test049_searchTrips_caseInsensitive_matching() {
        List<Trip> res = tripService.searchTrips("bengaluru", "hyderabad");
        assertEquals(1, res.size());
    }
    @Test void test050_searchTrips_partialSource_matching() {
        List<Trip> res = tripService.searchTrips("beng", "hyd");
        assertEquals(1, res.size());
    }
    @Test void test051_searchTrips_partialDest_matching() {
        List<Trip> res = tripService.searchTrips("", "era");
        assertEquals(1, res.size());
    }
    @Test void test052_searchTrips_emptyQueries_returnsAllPlannedTrips() {
        List<Trip> res = tripService.searchTrips("", "");
        assertFalse(res.isEmpty());
    }
    @Test void test053_searchTrips_nonMatching_returnsEmpty() {
        List<Trip> res = tripService.searchTrips("Kochi", "Goa");
        assertTrue(res.isEmpty());
    }
    @Test void test054_getTripsByTraveler_returnsList() {
        assertEquals(1, tripService.getTripsByTraveler(traveler.getId()).size());
    }
    @Test void test055_getTripById_validId_returnsTrip() {
        assertNotNull(tripService.getById(trip.getId()));
    }
    @Test void test056_getTripById_nonExistent_throwsRuntimeException() {
        assertThrows(RuntimeException.class, () -> tripService.getById(44444L));
    }
    @Test void test057_tripStatus_transitionsToActive() {
        trip.setStatus(Trip.TripStatus.ACTIVE);
        assertEquals(Trip.TripStatus.ACTIVE, trip.getStatus());
    }
    @Test void test058_tripStatus_transitionsToCompleted() {
        trip.setStatus(Trip.TripStatus.COMPLETED);
        assertEquals(Trip.TripStatus.COMPLETED, trip.getStatus());
    }
    @Test void test059_tripStatus_transitionsToCancelled() {
        trip.setStatus(Trip.TripStatus.CANCELLED);
        assertEquals(Trip.TripStatus.CANCELLED, trip.getStatus());
    }
    @Test void test060_cancelledTrip_excludedFromSearch() {
        trip.setStatus(Trip.TripStatus.CANCELLED);
        List<Trip> res = tripService.searchTrips("Bengaluru", "Hyderabad");
        assertTrue(res.isEmpty());
    }

    // =========================================================================
    // CATEGORY 4: Parcel Booking & Fare Calculations (061 - 080)
    // =========================================================================

    @Test void test061_createParcel_baseFare_zeroDeclaredValue() {
        ParcelBookingRequest pr = createSampleParcelReq(); pr.setDeclaredValue(0.0);
        assertEquals(15.0, parcelService.createParcelRequest(pr).getCalculatedFare(), 0.001);
    }
    @Test void test062_createParcel_declaredValue_500_calculates25() {
        ParcelBookingRequest pr = createSampleParcelReq(); pr.setDeclaredValue(500.0);
        assertEquals(25.0, parcelService.createParcelRequest(pr).getCalculatedFare(), 0.001);
    }
    @Test void test063_createParcel_declaredValue_1000_calculates35() {
        ParcelBookingRequest pr = createSampleParcelReq(); pr.setDeclaredValue(1000.0);
        assertEquals(35.0, parcelService.createParcelRequest(pr).getCalculatedFare(), 0.001);
    }
    @Test void test064_createParcel_declaredValue_10000_calculates215() {
        ParcelBookingRequest pr = createSampleParcelReq(); pr.setDeclaredValue(10000.0);
        assertEquals(215.0, parcelService.createParcelRequest(pr).getCalculatedFare(), 0.001);
    }
    @Test void test065_createParcel_nullDeclaredValue_calculatesBaseFare() {
        ParcelBookingRequest pr = createSampleParcelReq(); pr.setDeclaredValue(null);
        assertEquals(15.0, parcelService.createParcelRequest(pr).getCalculatedFare(), 0.001);
    }
    @Test void test066_createParcel_plannedTrip_succeeds() {
        assertNotNull(parcelService.createParcelRequest(createSampleParcelReq()).getId());
    }
    @Test void test067_createParcel_completedTrip_throwsIllegalStateException() {
        trip.setStatus(Trip.TripStatus.COMPLETED);
        assertThrows(IllegalStateException.class, () -> parcelService.createParcelRequest(createSampleParcelReq()));
    }
    @Test void test068_createParcel_cancelledTrip_throwsIllegalStateException() {
        trip.setStatus(Trip.TripStatus.CANCELLED);
        assertThrows(IllegalStateException.class, () -> parcelService.createParcelRequest(createSampleParcelReq()));
    }
    @Test void test069_createParcel_nonExistentSender_throwsRuntimeException() {
        ParcelBookingRequest pr = createSampleParcelReq(); pr.setSenderId(33333L);
        assertThrows(RuntimeException.class, () -> parcelService.createParcelRequest(pr));
    }
    @Test void test070_createParcel_nonExistentTrip_throwsRuntimeException() {
        ParcelBookingRequest pr = createSampleParcelReq(); pr.setTripId(33333L);
        assertThrows(RuntimeException.class, () -> parcelService.createParcelRequest(pr));
    }
    @Test void test071_createParcel_nullPickup_throwsException() {
        ParcelBookingRequest pr = createSampleParcelReq(); pr.setPickupLocation(null);
        assertThrows(Exception.class, () -> parcelService.createParcelRequest(pr));
    }
    @Test void test072_createParcel_nullDropoff_throwsException() {
        ParcelBookingRequest pr = createSampleParcelReq(); pr.setDropoffLocation(null);
        assertThrows(Exception.class, () -> parcelService.createParcelRequest(pr));
    }
    @Test void test073_createParcel_nullGoodsDescription_throwsException() {
        ParcelBookingRequest pr = createSampleParcelReq(); pr.setGoodsDescription(null);
        assertThrows(Exception.class, () -> parcelService.createParcelRequest(pr));
    }
    @Test void test074_createParcel_initialStatus_isCreated() {
        assertEquals(ParcelRequest.ParcelStatus.CREATED, parcelService.createParcelRequest(createSampleParcelReq()).getStatus());
    }
    @Test void test075_getParcelById_valid_returnsRequest() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        assertNotNull(parcelService.getById(req.getId()));
    }
    @Test void test076_getParcelById_nonExistent_throwsRuntimeException() {
        assertThrows(RuntimeException.class, () -> parcelService.getById(22222L));
    }
    @Test void test077_getParcelsBySender_returnsList() {
        parcelService.createParcelRequest(createSampleParcelReq());
        assertEquals(1, parcelService.getRequestsBySender(sender.getId()).size());
    }
    @Test void test078_getParcelsByTrip_returnsList() {
        parcelService.createParcelRequest(createSampleParcelReq());
        assertEquals(1, parcelService.getRequestsByTrip(trip.getId()).size());
    }
    @Test void test079_createMultipleParcels_sameTrip_returnsAll() {
        parcelService.createParcelRequest(createSampleParcelReq());
        parcelService.createParcelRequest(createSampleParcelReq());
        assertEquals(2, parcelService.getRequestsByTrip(trip.getId()).size());
    }
    @Test void test080_parcelFare_alwaysPositive() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        assertTrue(req.getCalculatedFare() > 0);
    }

    // =========================================================================
    // CATEGORY 5: Parcel Acceptance & Chat Security (081 - 100)
    // =========================================================================

    @Test void test081_acceptParcel_designatedTraveler_updatesStatusToAccepted() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        assertEquals(ParcelRequest.ParcelStatus.ACCEPTED, parcelService.acceptParcelRequest(req.getId(), traveler.getId()).getStatus());
    }
    @Test void test082_acceptParcel_unauthorizedTraveler_throwsIllegalArgumentException() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        assertThrows(IllegalArgumentException.class, () -> parcelService.acceptParcelRequest(req.getId(), 11111L));
    }
    @Test void test083_acceptParcel_nonExistentTraveler_throwsIllegalArgumentException() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        assertThrows(IllegalArgumentException.class, () -> parcelService.acceptParcelRequest(req.getId(), 99999L));
    }
    @Test void test084_acceptParcel_alreadyAccepted_throwsIllegalStateException() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        assertThrows(IllegalStateException.class, () -> parcelService.acceptParcelRequest(req.getId(), traveler.getId()));
    }
    @Test void test085_acceptParcel_paidEscrowStatus_throwsIllegalStateException() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        parcelService.payEscrow(req.getId(), sender.getId());
        assertThrows(IllegalStateException.class, () -> parcelService.acceptParcelRequest(req.getId(), traveler.getId()));
    }
    @Test void test086_acceptParcel_pickedUpStatus_throwsIllegalStateException() {
        ParcelRequest req = setupPickedUpParcel();
        assertThrows(IllegalStateException.class, () -> parcelService.acceptParcelRequest(req.getId(), traveler.getId()));
    }
    @Test void test087_acceptParcel_deliveredStatus_throwsIllegalStateException() {
        ParcelRequest req = setupDeliveredParcel();
        assertThrows(IllegalStateException.class, () -> parcelService.acceptParcelRequest(req.getId(), traveler.getId()));
    }
    @Test void test088_acceptParcel_cancelledStatus_throwsIllegalStateException() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        parcelService.cancelAndRefund(req.getId(), sender.getId());
        assertThrows(IllegalStateException.class, () -> parcelService.acceptParcelRequest(req.getId(), traveler.getId()));
    }
    @Test void test089_acceptParcel_nonExistentParcel_throwsRuntimeException() {
        assertThrows(RuntimeException.class, () -> parcelService.acceptParcelRequest(88888L, traveler.getId()));
    }
    @Test void test090_chatMessage_beforeAcceptance_throwsIllegalStateException() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        ChatMessageRequest c = new ChatMessageRequest(); c.setSenderUserId(sender.getId()); c.setMessage("Hi");
        assertThrows(IllegalStateException.class, () -> chatService.sendMessage(req.getId(), c));
    }
    @Test void test091_chatMessage_afterAcceptance_savesMessage() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        ChatMessageRequest c = new ChatMessageRequest(); c.setSenderUserId(sender.getId()); c.setMessage("Hi");
        assertNotNull(chatService.sendMessage(req.getId(), c).getId());
    }
    @Test void test092_chatMessage_multipleMessages_savedInOrder() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        ChatMessageRequest c1 = new ChatMessageRequest(); c1.setSenderUserId(sender.getId()); c1.setMessage("Msg 1");
        ChatMessageRequest c2 = new ChatMessageRequest(); c2.setSenderUserId(traveler.getId()); c2.setMessage("Msg 2");
        chatService.sendMessage(req.getId(), c1); chatService.sendMessage(req.getId(), c2);
        List<ChatMessage> h = chatService.getChatHistory(req.getId());
        assertEquals(2, h.size()); assertEquals("Msg 1", h.get(0).getMessage());
    }
    @Test void test093_chatMessage_nullSender_throwsException() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        ChatMessageRequest c = new ChatMessageRequest(); c.setMessage("Hi");
        assertThrows(Exception.class, () -> chatService.sendMessage(req.getId(), c));
    }
    @Test void test094_chatMessage_emptyMessage_throwsException() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        ChatMessageRequest c = new ChatMessageRequest(); c.setSenderUserId(sender.getId()); c.setMessage("");
        assertThrows(Exception.class, () -> chatService.sendMessage(req.getId(), c));
    }
    @Test void test095_chatMessage_nonExistentParcel_throwsRuntimeException() {
        ChatMessageRequest c = new ChatMessageRequest(); c.setSenderUserId(sender.getId()); c.setMessage("Hi");
        assertThrows(RuntimeException.class, () -> chatService.sendMessage(99999L, c));
    }
    @Test void test096_chatMessage_fetchHistory_returnsOrderedMessages() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        ChatMessageRequest c = new ChatMessageRequest(); c.setSenderUserId(sender.getId()); c.setMessage("Hello");
        chatService.sendMessage(req.getId(), c);
        assertEquals(1, chatService.getChatHistory(req.getId()).size());
    }
    @Test void test097_chatMessage_isolationBetweenParcels() {
        ParcelRequest p1 = parcelService.createParcelRequest(createSampleParcelReq());
        parcelService.acceptParcelRequest(p1.getId(), traveler.getId());
        ParcelRequest p2 = parcelService.createParcelRequest(createSampleParcelReq());
        parcelService.acceptParcelRequest(p2.getId(), traveler.getId());

        ChatMessageRequest c1 = new ChatMessageRequest(); c1.setSenderUserId(sender.getId()); c1.setMessage("Msg P1");
        chatService.sendMessage(p1.getId(), c1);

        assertEquals(1, chatService.getChatHistory(p1.getId()).size());
        assertEquals(0, chatService.getChatHistory(p2.getId()).size());
    }
    @Test void test098_chatMessage_bothSenderAndTravelerCanPost() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        ChatMessageRequest c1 = new ChatMessageRequest(); c1.setSenderUserId(sender.getId()); c1.setMessage("S");
        ChatMessageRequest c2 = new ChatMessageRequest(); c2.setSenderUserId(traveler.getId()); c2.setMessage("T");
        chatService.sendMessage(req.getId(), c1);
        chatService.sendMessage(req.getId(), c2);
        assertEquals(2, chatService.getChatHistory(req.getId()).size());
    }
    @Test void test099_parcelStatus_created_cannotPayEscrow() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        assertThrows(IllegalStateException.class, () -> parcelService.payEscrow(req.getId(), sender.getId()));
    }
    @Test void test100_parcelStatus_accepted_allowsEscrowPayment() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        assertNotNull(parcelService.payEscrow(req.getId(), sender.getId()).getId());
    }

    // =========================================================================
    // CATEGORY 6: Escrow Payment Holds & Cancellation (101 - 120)
    // =========================================================================

    @Test void test101_payEscrow_validSender_createsHeldPayment() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        Payment p = parcelService.payEscrow(req.getId(), sender.getId());
        assertEquals(Payment.EscrowStatus.HELD, p.getStatus());
    }
    @Test void test102_payEscrow_generatesPickupOtp() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        parcelService.payEscrow(req.getId(), sender.getId());
        assertNotNull(parcelService.getById(req.getId()).getPickupOtp());
    }
    @Test void test103_payEscrow_generatesDeliveryOtp() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        parcelService.payEscrow(req.getId(), sender.getId());
        assertNotNull(parcelService.getById(req.getId()).getDeliveryOtp());
    }
    @Test void test104_payEscrow_otpIs4Digits() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        parcelService.payEscrow(req.getId(), sender.getId());
        assertEquals(4, parcelService.getById(req.getId()).getPickupOtp().length());
    }
    @Test void test105_payEscrow_unauthorizedSender_throwsIllegalArgumentException() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        assertThrows(IllegalArgumentException.class, () -> parcelService.payEscrow(req.getId(), traveler.getId()));
    }
    @Test void test106_payEscrow_unacceptedRequest_throwsIllegalStateException() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        assertThrows(IllegalStateException.class, () -> parcelService.payEscrow(req.getId(), sender.getId()));
    }
    @Test void test107_payEscrow_alreadyPaid_throwsIllegalStateException() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        parcelService.payEscrow(req.getId(), sender.getId());
        assertThrows(IllegalStateException.class, () -> parcelService.payEscrow(req.getId(), sender.getId()));
    }
    @Test void test108_payEscrow_nonExistentParcel_throwsRuntimeException() {
        assertThrows(RuntimeException.class, () -> parcelService.payEscrow(77777L, sender.getId()));
    }
    @Test void test109_cancelParcel_createdStatus_updatesCancelled() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        assertEquals(ParcelRequest.ParcelStatus.CANCELLED, parcelService.cancelAndRefund(req.getId(), sender.getId()).getStatus());
    }
    @Test void test110_cancelParcel_acceptedStatus_updatesCancelled() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        assertEquals(ParcelRequest.ParcelStatus.CANCELLED, parcelService.cancelAndRefund(req.getId(), sender.getId()).getStatus());
    }
    @Test void test111_cancelParcel_paidEscrowStatus_refundsPayment() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        parcelService.payEscrow(req.getId(), sender.getId());
        parcelService.cancelAndRefund(req.getId(), sender.getId());
        assertEquals(Payment.EscrowStatus.REFUNDED, paymentRepository.findByParcelRequestId(req.getId()).get().getStatus());
    }
    @Test void test112_cancelParcel_pickedUpStatus_refundsPayment() {
        ParcelRequest req = setupPickedUpParcel();
        parcelService.cancelAndRefund(req.getId(), sender.getId());
        assertEquals(Payment.EscrowStatus.REFUNDED, paymentRepository.findByParcelRequestId(req.getId()).get().getStatus());
    }
    @Test void test113_cancelParcel_deliveredStatus_throwsIllegalStateException() {
        ParcelRequest req = setupDeliveredParcel();
        assertThrows(IllegalStateException.class, () -> parcelService.cancelAndRefund(req.getId(), sender.getId()));
    }
    @Test void test114_cancelParcel_alreadyCancelled_retainsStatus() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        parcelService.cancelAndRefund(req.getId(), sender.getId());
        assertEquals(ParcelRequest.ParcelStatus.CANCELLED, parcelService.cancelAndRefund(req.getId(), sender.getId()).getStatus());
    }
    @Test void test115_payment_amountMatchesCalculatedFare() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        Payment p = parcelService.payEscrow(req.getId(), sender.getId());
        assertEquals(req.getCalculatedFare(), p.getAmount());
    }
    @Test void test116_payment_findByParcelId_returnsPayment() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        parcelService.payEscrow(req.getId(), sender.getId());
        assertTrue(paymentRepository.findByParcelRequestId(req.getId()).isPresent());
    }
    @Test void test117_payment_statusHeld_beforeDelivery() {
        ParcelRequest req = setupPickedUpParcel();
        assertEquals(Payment.EscrowStatus.HELD, paymentRepository.findByParcelRequestId(req.getId()).get().getStatus());
    }
    @Test void test118_payment_statusRefunded_afterCancel() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        parcelService.payEscrow(req.getId(), sender.getId());
        parcelService.cancelAndRefund(req.getId(), sender.getId());
        assertEquals(Payment.EscrowStatus.REFUNDED, paymentRepository.findByParcelRequestId(req.getId()).get().getStatus());
    }
    @Test void test119_payment_statusReleased_afterDelivery() {
        ParcelRequest req = setupDeliveredParcel();
        assertEquals(Payment.EscrowStatus.RELEASED, paymentRepository.findByParcelRequestId(req.getId()).get().getStatus());
    }
    @Test void test120_cancelParcel_nonExistentParcel_throwsRuntimeException() {
        assertThrows(RuntimeException.class, () -> parcelService.cancelAndRefund(66666L, sender.getId()));
    }

    // =========================================================================
    // CATEGORY 7: Dual OTP & 3-Point Photo Handover Integrity (121 - 140)
    // =========================================================================

    @Test void test121_verifyPickup_validOtpAndPhoto_updatesPickedUp() {
        ParcelRequest req = setupPickedUpParcel();
        assertEquals(ParcelRequest.ParcelStatus.PICKED_UP, req.getStatus());
    }
    @Test void test122_verifyPickup_savesPickupPhotoUrl() {
        ParcelRequest req = setupPickedUpParcel();
        assertEquals("http://photo.com/pickup.jpg", req.getPickupPhotoUrl());
    }
    @Test void test123_verifyPickup_invalidOtp_throwsIllegalArgumentException() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        parcelService.payEscrow(req.getId(), sender.getId());
        OtpVerificationRequest v = new OtpVerificationRequest(); v.setParcelRequestId(req.getId()); v.setOtp("0000"); v.setPhotoUrl("http://photo.com/p.jpg");
        assertThrows(IllegalArgumentException.class, () -> parcelService.verifyPickup(v));
    }
    @Test void test124_verifyPickup_invalidOtp_retainsPaidEscrowStatus() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        parcelService.payEscrow(req.getId(), sender.getId());
        OtpVerificationRequest v = new OtpVerificationRequest(); v.setParcelRequestId(req.getId()); v.setOtp("0000"); v.setPhotoUrl("http://photo.com/p.jpg");
        assertThrows(IllegalArgumentException.class, () -> parcelService.verifyPickup(v));
        assertEquals(ParcelRequest.ParcelStatus.PAID_ESCROW, parcelService.getById(req.getId()).getStatus());
    }
    @Test void test125_verifyPickup_createdStatus_throwsIllegalStateException() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        OtpVerificationRequest v = new OtpVerificationRequest(); v.setParcelRequestId(req.getId()); v.setOtp("1234"); v.setPhotoUrl("http://photo.com/p.jpg");
        assertThrows(IllegalStateException.class, () -> parcelService.verifyPickup(v));
    }
    @Test void test126_verifyPickup_acceptedStatus_throwsIllegalStateException() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        OtpVerificationRequest v = new OtpVerificationRequest(); v.setParcelRequestId(req.getId()); v.setOtp("1234"); v.setPhotoUrl("http://photo.com/p.jpg");
        assertThrows(IllegalStateException.class, () -> parcelService.verifyPickup(v));
    }
    @Test void test127_verifyPickup_alreadyPickedUp_throwsIllegalStateException() {
        ParcelRequest req = setupPickedUpParcel();
        OtpVerificationRequest v = new OtpVerificationRequest(); v.setParcelRequestId(req.getId()); v.setOtp(req.getPickupOtp()); v.setPhotoUrl("http://photo.com/p.jpg");
        assertThrows(IllegalStateException.class, () -> parcelService.verifyPickup(v));
    }
    @Test void test128_verifyPickup_deliveredStatus_throwsIllegalStateException() {
        ParcelRequest req = setupDeliveredParcel();
        OtpVerificationRequest v = new OtpVerificationRequest(); v.setParcelRequestId(req.getId()); v.setOtp("1234"); v.setPhotoUrl("http://photo.com/p.jpg");
        assertThrows(IllegalStateException.class, () -> parcelService.verifyPickup(v));
    }
    @Test void test129_verifyPickup_cancelledStatus_throwsIllegalStateException() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        parcelService.cancelAndRefund(req.getId(), sender.getId());
        OtpVerificationRequest v = new OtpVerificationRequest(); v.setParcelRequestId(req.getId()); v.setOtp("1234"); v.setPhotoUrl("http://photo.com/p.jpg");
        assertThrows(IllegalStateException.class, () -> parcelService.verifyPickup(v));
    }
    @Test void test130_verifyPickup_nullPhoto_throwsException() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        parcelService.payEscrow(req.getId(), sender.getId());
        ParcelRequest paid = parcelService.getById(req.getId());
        OtpVerificationRequest v = new OtpVerificationRequest(); v.setParcelRequestId(req.getId()); v.setOtp(paid.getPickupOtp()); v.setPhotoUrl(null);
        assertThrows(Exception.class, () -> parcelService.verifyPickup(v));
    }
    @Test void test131_verifyDelivery_validOtpAndPhoto_updatesDelivered() {
        ParcelRequest req = setupDeliveredParcel();
        assertEquals(ParcelRequest.ParcelStatus.DELIVERED, req.getStatus());
    }
    @Test void test132_verifyDelivery_savesDeliveryPhotoUrl() {
        ParcelRequest req = setupDeliveredParcel();
        assertEquals("http://photo.com/delivery.jpg", req.getDeliveryPhotoUrl());
    }
    @Test void test133_verifyDelivery_releasesEscrowPayment() {
        ParcelRequest req = setupDeliveredParcel();
        assertEquals(Payment.EscrowStatus.RELEASED, paymentRepository.findByParcelRequestId(req.getId()).get().getStatus());
    }
    @Test void test134_verifyDelivery_invalidOtp_throwsIllegalArgumentException() {
        ParcelRequest req = setupPickedUpParcel();
        OtpVerificationRequest v = new OtpVerificationRequest(); v.setParcelRequestId(req.getId()); v.setOtp("0000"); v.setPhotoUrl("http://photo.com/d.jpg");
        assertThrows(IllegalArgumentException.class, () -> parcelService.verifyDelivery(v));
    }
    @Test void test135_verifyDelivery_beforePickup_paidEscrowStatus_throwsIllegalStateException() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        parcelService.payEscrow(req.getId(), sender.getId());
        ParcelRequest paid = parcelService.getById(req.getId());
        OtpVerificationRequest v = new OtpVerificationRequest(); v.setParcelRequestId(req.getId()); v.setOtp(paid.getDeliveryOtp()); v.setPhotoUrl("http://photo.com/d.jpg");
        assertThrows(IllegalStateException.class, () -> parcelService.verifyDelivery(v));
    }
    @Test void test136_verifyDelivery_createdStatus_throwsIllegalStateException() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        OtpVerificationRequest v = new OtpVerificationRequest(); v.setParcelRequestId(req.getId()); v.setOtp("1234"); v.setPhotoUrl("http://photo.com/d.jpg");
        assertThrows(IllegalStateException.class, () -> parcelService.verifyDelivery(v));
    }
    @Test void test137_verifyDelivery_alreadyDelivered_throwsIllegalStateException() {
        ParcelRequest req = setupDeliveredParcel();
        OtpVerificationRequest v = new OtpVerificationRequest(); v.setParcelRequestId(req.getId()); v.setOtp(req.getDeliveryOtp()); v.setPhotoUrl("http://photo.com/d.jpg");
        assertThrows(IllegalStateException.class, () -> parcelService.verifyDelivery(v));
    }
    @Test void test138_verifyDelivery_cancelledStatus_throwsIllegalStateException() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        parcelService.cancelAndRefund(req.getId(), sender.getId());
        OtpVerificationRequest v = new OtpVerificationRequest(); v.setParcelRequestId(req.getId()); v.setOtp("1234"); v.setPhotoUrl("http://photo.com/d.jpg");
        assertThrows(IllegalStateException.class, () -> parcelService.verifyDelivery(v));
    }
    @Test void test139_verifyDelivery_nullPhoto_throwsException() {
        ParcelRequest req = setupPickedUpParcel();
        OtpVerificationRequest v = new OtpVerificationRequest(); v.setParcelRequestId(req.getId()); v.setOtp(req.getDeliveryOtp()); v.setPhotoUrl(null);
        assertThrows(Exception.class, () -> parcelService.verifyDelivery(v));
    }
    @Test void test140_handover_completeFlow_endToEnd() {
        ParcelRequest req = setupDeliveredParcel();
        assertNotNull(req.getPickupPhotoUrl());
        assertNotNull(req.getDeliveryPhotoUrl());
        assertEquals(ParcelRequest.ParcelStatus.DELIVERED, req.getStatus());
    }

    // =========================================================================
    // CATEGORY 8: Ride Booking & Dynamic ETA Buffer (141 - 160)
    // =========================================================================

    @Test void test141_requestRide_valid_createsRequestedRide() {
        RideBookingRequest r = createSampleRideReq();
        assertEquals(RideRequest.RideStatus.REQUESTED, rideService.requestRide(r).getStatus());
    }
    @Test void test142_requestRide_calculatesDynamicBuffer_20Percent() {
        RideBookingRequest r = createSampleRideReq(); r.setEstimatedDurationMinutes(50); // 20% of 50 = 10
        assertEquals(10, rideService.requestRide(r).getBufferMinutes());
    }
    @Test void test143_requestRide_minBuffer_5Minutes() {
        RideBookingRequest r = createSampleRideReq(); r.setEstimatedDurationMinutes(15); // 20% of 15 = 3 -> max(5,3) = 5
        assertEquals(5, rideService.requestRide(r).getBufferMinutes());
    }
    @Test void test144_requestRide_nonExistentRider_throwsRuntimeException() {
        RideBookingRequest r = createSampleRideReq(); r.setRiderId(77777L);
        assertThrows(RuntimeException.class, () -> rideService.requestRide(r));
    }
    @Test void test145_requestRide_nonExistentTrip_throwsRuntimeException() {
        RideBookingRequest r = createSampleRideReq(); r.setTripId(77777L);
        assertThrows(RuntimeException.class, () -> rideService.requestRide(r));
    }
    @Test void test146_requestRide_completedTrip_throwsIllegalStateException() {
        trip.setStatus(Trip.TripStatus.COMPLETED);
        assertThrows(IllegalStateException.class, () -> rideService.requestRide(createSampleRideReq()));
    }
    @Test void test147_requestRide_cancelledTrip_throwsIllegalStateException() {
        trip.setStatus(Trip.TripStatus.CANCELLED);
        assertThrows(IllegalStateException.class, () -> rideService.requestRide(createSampleRideReq()));
    }
    @Test void test148_acceptRide_designatedTraveler_updatesAccepted() {
        RideRequest ride = rideService.requestRide(createSampleRideReq());
        assertEquals(RideRequest.RideStatus.ACCEPTED, rideService.acceptRide(ride.getId(), traveler.getId()).getStatus());
    }
    @Test void test149_acceptRide_unauthorizedTraveler_throwsIllegalArgumentException() {
        RideRequest ride = rideService.requestRide(createSampleRideReq());
        assertThrows(IllegalArgumentException.class, () -> rideService.acceptRide(ride.getId(), 88888L));
    }
    @Test void test150_acceptRide_alreadyAccepted_throwsIllegalStateException() {
        RideRequest ride = rideService.requestRide(createSampleRideReq());
        rideService.acceptRide(ride.getId(), traveler.getId());
        assertThrows(IllegalStateException.class, () -> rideService.acceptRide(ride.getId(), traveler.getId()));
    }
    @Test void test151_startRide_acceptedStatus_updatesInProgress() {
        RideRequest ride = rideService.requestRide(createSampleRideReq());
        rideService.acceptRide(ride.getId(), traveler.getId());
        assertEquals(RideRequest.RideStatus.IN_PROGRESS, rideService.startRide(ride.getId()).getStatus());
    }
    @Test void test152_startRide_requestedStatus_throwsIllegalStateException() {
        RideRequest ride = rideService.requestRide(createSampleRideReq());
        assertThrows(IllegalStateException.class, () -> rideService.startRide(ride.getId()));
    }
    @Test void test153_startRide_alreadyStarted_throwsIllegalStateException() {
        RideRequest ride = rideService.requestRide(createSampleRideReq());
        rideService.acceptRide(ride.getId(), traveler.getId());
        rideService.startRide(ride.getId());
        assertThrows(IllegalStateException.class, () -> rideService.startRide(ride.getId()));
    }
    @Test void test154_completeRide_inProgressStatus_updatesCompleted() {
        RideRequest ride = rideService.requestRide(createSampleRideReq());
        rideService.acceptRide(ride.getId(), traveler.getId());
        rideService.startRide(ride.getId());
        assertEquals(RideRequest.RideStatus.COMPLETED, rideService.completeRide(ride.getId()).getStatus());
    }
    @Test void test155_completeRide_acceptedStatus_throwsIllegalStateException() {
        RideRequest ride = rideService.requestRide(createSampleRideReq());
        rideService.acceptRide(ride.getId(), traveler.getId());
        assertThrows(IllegalStateException.class, () -> rideService.completeRide(ride.getId()));
    }
    @Test void test156_completeRide_alreadyCompleted_throwsIllegalStateException() {
        RideRequest ride = rideService.requestRide(createSampleRideReq());
        rideService.acceptRide(ride.getId(), traveler.getId());
        rideService.startRide(ride.getId());
        rideService.completeRide(ride.getId());
        assertThrows(IllegalStateException.class, () -> rideService.completeRide(ride.getId()));
    }
    @Test void test157_completeRide_autoResolvesSafetyAlerts() {
        RideRequest ride = rideService.requestRide(createSampleRideReq());
        rideService.acceptRide(ride.getId(), traveler.getId());
        rideService.startRide(ride.getId());
        SafetyAlert alert = rideService.triggerSafetyEscalation(ride.getId(), "Loc", SafetyAlert.EscalationStage.STAGE_1_SILENT_PING);
        rideService.completeRide(ride.getId());
        assertEquals(SafetyAlert.AlertStatus.RESOLVED, safetyAlertRepository.findById(alert.getId()).get().getStatus());
    }
    @Test void test158_getRidesByRider_returnsList() {
        rideService.requestRide(createSampleRideReq());
        assertEquals(1, rideService.getRidesByRider(rider.getId()).size());
    }
    @Test void test159_getRidesByTrip_returnsList() {
        rideService.requestRide(createSampleRideReq());
        assertEquals(1, rideService.getRidesByTrip(trip.getId()).size());
    }
    @Test void test160_getRideById_nonExistent_throwsRuntimeException() {
        assertThrows(RuntimeException.class, () -> rideService.getById(99999L));
    }

    // =========================================================================
    // CATEGORY 9: 3-Stage Safety Escalation Ladder (161 - 180)
    // =========================================================================

    @Test void test161_triggerSafety_stage1_silentPing() {
        RideRequest r = setupStartedRide();
        SafetyAlert a = rideService.triggerSafetyEscalation(r.getId(), "Loc1", SafetyAlert.EscalationStage.STAGE_1_SILENT_PING);
        assertEquals(SafetyAlert.EscalationStage.STAGE_1_SILENT_PING, a.getEscalationStage());
    }
    @Test void test162_triggerSafety_stage2_inAppCheckin() {
        RideRequest r = setupStartedRide();
        SafetyAlert a = rideService.triggerSafetyEscalation(r.getId(), "Loc2", SafetyAlert.EscalationStage.STAGE_2_IN_APP_CHECKIN);
        assertEquals(SafetyAlert.EscalationStage.STAGE_2_IN_APP_CHECKIN, a.getEscalationStage());
    }
    @Test void test163_triggerSafety_stage3_trustedContactAlert() {
        RideRequest r = setupStartedRide();
        SafetyAlert a = rideService.triggerSafetyEscalation(r.getId(), "Loc3", SafetyAlert.EscalationStage.STAGE_3_TRUSTED_CONTACT_ALERT);
        assertEquals(SafetyAlert.EscalationStage.STAGE_3_TRUSTED_CONTACT_ALERT, a.getEscalationStage());
    }
    @Test void test164_triggerSafety_stage3_setsStatusEscalated() {
        RideRequest r = setupStartedRide();
        SafetyAlert a = rideService.triggerSafetyEscalation(r.getId(), "Loc3", SafetyAlert.EscalationStage.STAGE_3_TRUSTED_CONTACT_ALERT);
        assertEquals(SafetyAlert.AlertStatus.ESCALATED, a.getStatus());
    }
    @Test void test165_triggerSafety_safetyModeDisabled_throwsIllegalStateException() {
        RideBookingRequest req = createSampleRideReq(); req.setSafetyModeEnabled(false);
        RideRequest ride = rideService.requestRide(req);
        rideService.acceptRide(ride.getId(), traveler.getId());
        rideService.startRide(ride.getId());
        assertThrows(IllegalStateException.class, () -> rideService.triggerSafetyEscalation(ride.getId(), "Loc", SafetyAlert.EscalationStage.STAGE_1_SILENT_PING));
    }
    @Test void test166_triggerSafety_storesLastKnownLocation() {
        RideRequest r = setupStartedRide();
        SafetyAlert a = rideService.triggerSafetyEscalation(r.getId(), "GPS Point X", SafetyAlert.EscalationStage.STAGE_1_SILENT_PING);
        assertEquals("GPS Point X", a.getLastKnownLocation());
    }
    @Test void test167_acknowledgeCheckin_isSafeTrue_resolvesAlert() {
        RideRequest r = setupStartedRide();
        SafetyAlert a = rideService.triggerSafetyEscalation(r.getId(), "Loc", SafetyAlert.EscalationStage.STAGE_2_IN_APP_CHECKIN);
        assertEquals(SafetyAlert.AlertStatus.RESOLVED, rideService.acknowledgeCheckin(a.getId(), true).getStatus());
    }
    @Test void test168_acknowledgeCheckin_isSafeTrue_setsResolvedAt() {
        RideRequest r = setupStartedRide();
        SafetyAlert a = rideService.triggerSafetyEscalation(r.getId(), "Loc", SafetyAlert.EscalationStage.STAGE_2_IN_APP_CHECKIN);
        assertNotNull(rideService.acknowledgeCheckin(a.getId(), true).getResolvedAt());
    }
    @Test void test169_acknowledgeCheckin_isSafeFalse_escalatesToStage3() {
        RideRequest r = setupStartedRide();
        SafetyAlert a = rideService.triggerSafetyEscalation(r.getId(), "Loc", SafetyAlert.EscalationStage.STAGE_2_IN_APP_CHECKIN);
        assertEquals(SafetyAlert.EscalationStage.STAGE_3_TRUSTED_CONTACT_ALERT, rideService.acknowledgeCheckin(a.getId(), false).getEscalationStage());
    }
    @Test void test170_acknowledgeCheckin_isSafeFalse_setsStatusEscalated() {
        RideRequest r = setupStartedRide();
        SafetyAlert a = rideService.triggerSafetyEscalation(r.getId(), "Loc", SafetyAlert.EscalationStage.STAGE_2_IN_APP_CHECKIN);
        assertEquals(SafetyAlert.AlertStatus.ESCALATED, rideService.acknowledgeCheckin(a.getId(), false).getStatus());
    }
    @Test void test171_acknowledgeCheckin_nonExistentAlert_throwsRuntimeException() {
        assertThrows(RuntimeException.class, () -> rideService.acknowledgeCheckin(99999L, true));
    }
    @Test void test172_safetyAlert_multipleAlerts_sameRide() {
        RideRequest r = setupStartedRide();
        rideService.triggerSafetyEscalation(r.getId(), "L1", SafetyAlert.EscalationStage.STAGE_1_SILENT_PING);
        rideService.triggerSafetyEscalation(r.getId(), "L2", SafetyAlert.EscalationStage.STAGE_2_IN_APP_CHECKIN);
        assertEquals(2, safetyAlertRepository.findByRideRequestId(r.getId()).size());
    }
    @Test void test173_safetyAlert_withNoTrustedContacts_includesWarning() {
        RegisterRequest rReq = new RegisterRequest(); rReq.setFullName("No Contact"); rReq.setMobileNumber("9900099999"); rReq.setRole(User.UserRole.RIDER);
        User noContactRider = userService.register(rReq);
        RideBookingRequest rr = createSampleRideReq(); rr.setRiderId(noContactRider.getId());
        RideRequest ride = rideService.requestRide(rr);
        rideService.acceptRide(ride.getId(), traveler.getId());
        rideService.startRide(ride.getId());

        SafetyAlert a = rideService.triggerSafetyEscalation(ride.getId(), "Loc", SafetyAlert.EscalationStage.STAGE_3_TRUSTED_CONTACT_ALERT);
        assertTrue(a.getTriggerReason().contains("Warning: No trusted contacts"));
    }
    @Test void test174_safetyAlert_withTrustedContacts_dispatchesAlert() {
        RideRequest r = setupStartedRide();
        SafetyAlert a = rideService.triggerSafetyEscalation(r.getId(), "Loc", SafetyAlert.EscalationStage.STAGE_3_TRUSTED_CONTACT_ALERT);
        assertEquals(SafetyAlert.AlertStatus.ESCALATED, a.getStatus());
    }
    @Test void test175_completeRide_resolvesMultipleAlerts() {
        RideRequest r = setupStartedRide();
        rideService.triggerSafetyEscalation(r.getId(), "L1", SafetyAlert.EscalationStage.STAGE_1_SILENT_PING);
        rideService.triggerSafetyEscalation(r.getId(), "L2", SafetyAlert.EscalationStage.STAGE_2_IN_APP_CHECKIN);
        rideService.completeRide(r.getId());
        List<SafetyAlert> alerts = safetyAlertRepository.findByRideRequestId(r.getId());
        assertTrue(alerts.stream().allMatch(a -> a.getStatus() == SafetyAlert.AlertStatus.RESOLVED));
    }
    @Test void test176_safetyAlert_retainsAuditHistory() {
        RideRequest r = setupStartedRide();
        SafetyAlert a = rideService.triggerSafetyEscalation(r.getId(), "Audit Loc", SafetyAlert.EscalationStage.STAGE_1_SILENT_PING);
        assertNotNull(a.getCreatedAt());
    }
    @Test void test177_triggerSafety_nonExistentRide_throwsRuntimeException() {
        assertThrows(RuntimeException.class, () -> rideService.triggerSafetyEscalation(88888L, "Loc", SafetyAlert.EscalationStage.STAGE_1_SILENT_PING));
    }
    @Test void test178_safetyAlert_statusTriggered_stage1() {
        RideRequest r = setupStartedRide();
        SafetyAlert a = rideService.triggerSafetyEscalation(r.getId(), "Loc", SafetyAlert.EscalationStage.STAGE_1_SILENT_PING);
        assertEquals(SafetyAlert.AlertStatus.TRIGGERED, a.getStatus());
    }
    @Test void test179_safetyAlert_statusTriggered_stage2() {
        RideRequest r = setupStartedRide();
        SafetyAlert a = rideService.triggerSafetyEscalation(r.getId(), "Loc", SafetyAlert.EscalationStage.STAGE_2_IN_APP_CHECKIN);
        assertEquals(SafetyAlert.AlertStatus.TRIGGERED, a.getStatus());
    }
    @Test void test180_safetyAlert_checkinWorkflow_complete() {
        RideRequest r = setupStartedRide();
        SafetyAlert a = rideService.triggerSafetyEscalation(r.getId(), "Loc", SafetyAlert.EscalationStage.STAGE_2_IN_APP_CHECKIN);
        SafetyAlert ack = rideService.acknowledgeCheckin(a.getId(), true);
        assertEquals(SafetyAlert.AlertStatus.RESOLVED, ack.getStatus());
    }

    // =========================================================================
    // CATEGORY 10: Ratings, Disputes & End-To-End Platform (181 - 200)
    // =========================================================================

    @Test void test181_submitRating_valid_savesRating() {
        RatingSubmitRequest r = createSampleRatingReq(5);
        assertNotNull(trustAndDisputeService.submitRating(r).getId());
    }
    @Test void test182_submitRating_score5_recalculatesAverage() {
        RatingSubmitRequest r = createSampleRatingReq(5);
        trustAndDisputeService.submitRating(r);
        assertEquals(5.0, userService.getById(traveler.getId()).getAverageRating());
    }
    @Test void test183_submitRating_score1_recalculatesAverage() {
        RatingSubmitRequest r = createSampleRatingReq(1);
        trustAndDisputeService.submitRating(r);
        assertEquals(1.0, userService.getById(traveler.getId()).getAverageRating());
    }
    @Test void test184_submitRating_multipleRatings_calculatesExactMean() {
        trustAndDisputeService.submitRating(createSampleRatingReq(5));
        trustAndDisputeService.submitRating(createSampleRatingReq(3));
        assertEquals(4.0, userService.getById(traveler.getId()).getAverageRating());
    }
    @Test void test185_submitRating_scoreZero_failsValidation() {
        RatingSubmitRequest r = createSampleRatingReq(0);
        assertThrows(Exception.class, () -> trustAndDisputeService.submitRating(r));
    }
    @Test void test186_submitRating_scoreSix_failsValidation() {
        RatingSubmitRequest r = createSampleRatingReq(6);
        assertThrows(Exception.class, () -> trustAndDisputeService.submitRating(r));
    }
    @Test void test187_submitRating_nonExistentRater_throwsRuntimeException() {
        RatingSubmitRequest r = createSampleRatingReq(5); r.setRaterUserId(88888L);
        assertThrows(RuntimeException.class, () -> trustAndDisputeService.submitRating(r));
    }
    @Test void test188_submitRating_nonExistentRatee_throwsRuntimeException() {
        RatingSubmitRequest r = createSampleRatingReq(5); r.setRateeUserId(88888L);
        assertThrows(RuntimeException.class, () -> trustAndDisputeService.submitRating(r));
    }
    @Test void test189_getUserRatings_returnsUserRatingList() {
        trustAndDisputeService.submitRating(createSampleRatingReq(5));
        assertEquals(1, trustAndDisputeService.getUserRatings(traveler.getId()).size());
    }
    @Test void test190_createDispute_parcelRequest_createsOpenDispute() {
        ParcelRequest p = setupDeliveredParcel();
        DisputeCreateRequest d = new DisputeCreateRequest(); d.setReporterUserId(sender.getId()); d.setParcelRequestId(p.getId()); d.setDisputeReason("Damaged");
        assertEquals(Dispute.DisputeStatus.OPEN, trustAndDisputeService.createDispute(d).getStatus());
    }
    @Test void test191_createDispute_rideRequest_createsOpenDispute() {
        RideRequest r = setupStartedRide();
        DisputeCreateRequest d = new DisputeCreateRequest(); d.setReporterUserId(rider.getId()); d.setRideRequestId(r.getId()); d.setDisputeReason("Unsafe driving");
        assertEquals(Dispute.DisputeStatus.OPEN, trustAndDisputeService.createDispute(d).getStatus());
    }
    @Test void test192_createDispute_withEvidencePhotoUrl() {
        ParcelRequest p = setupDeliveredParcel();
        DisputeCreateRequest d = new DisputeCreateRequest(); d.setReporterUserId(sender.getId()); d.setParcelRequestId(p.getId()); d.setDisputeReason("Damaged"); d.setEvidencePhotoUrl("http://photo.com/proof.jpg");
        assertEquals("http://photo.com/proof.jpg", trustAndDisputeService.createDispute(d).getEvidencePhotoUrl());
    }
    @Test void test193_createDispute_withoutParcelOrRide_throwsIllegalArgumentException() {
        DisputeCreateRequest d = new DisputeCreateRequest(); d.setReporterUserId(sender.getId()); d.setDisputeReason("Complaint");
        assertThrows(IllegalArgumentException.class, () -> trustAndDisputeService.createDispute(d));
    }
    @Test void test194_createDispute_nonExistentReporter_throwsRuntimeException() {
        DisputeCreateRequest d = new DisputeCreateRequest(); d.setReporterUserId(77777L); d.setParcelRequestId(1L); d.setDisputeReason("Reason");
        assertThrows(RuntimeException.class, () -> trustAndDisputeService.createDispute(d));
    }
    @Test void test195_resolveDispute_refundSender_refundsEscrow_cancelsParcel() {
        ParcelRequest p = setupPickedUpParcel();
        DisputeCreateRequest d = new DisputeCreateRequest(); d.setReporterUserId(sender.getId()); d.setParcelRequestId(p.getId()); d.setDisputeReason("Lost item");
        Dispute disp = trustAndDisputeService.createDispute(d);
        trustAndDisputeService.resolveDispute(disp.getId(), Dispute.DisputeStatus.RESOLVED_REFUND_SENDER, "Full refund");

        assertEquals(Payment.EscrowStatus.REFUNDED, paymentRepository.findByParcelRequestId(p.getId()).get().getStatus());
        assertEquals(ParcelRequest.ParcelStatus.CANCELLED, parcelService.getById(p.getId()).getStatus());
    }
    @Test void test196_resolveDispute_releaseTraveler_releasesEscrow_deliversParcel() {
        ParcelRequest p = setupPickedUpParcel();
        DisputeCreateRequest d = new DisputeCreateRequest(); d.setReporterUserId(traveler.getId()); d.setParcelRequestId(p.getId()); d.setDisputeReason("Refused OTP");
        Dispute disp = trustAndDisputeService.createDispute(d);
        trustAndDisputeService.resolveDispute(disp.getId(), Dispute.DisputeStatus.RESOLVED_RELEASE_TRAVELER, "Released");

        assertEquals(Payment.EscrowStatus.RELEASED, paymentRepository.findByParcelRequestId(p.getId()).get().getStatus());
        assertEquals(ParcelRequest.ParcelStatus.DELIVERED, parcelService.getById(p.getId()).getStatus());
    }
    @Test void test197_resolveDispute_rejected_retainsPayment() {
        ParcelRequest p = setupPickedUpParcel();
        DisputeCreateRequest d = new DisputeCreateRequest(); d.setReporterUserId(sender.getId()); d.setParcelRequestId(p.getId()); d.setDisputeReason("Invalid claim");
        Dispute disp = trustAndDisputeService.createDispute(d);
        trustAndDisputeService.resolveDispute(disp.getId(), Dispute.DisputeStatus.REJECTED, "Claim invalid");

        assertEquals(Payment.EscrowStatus.HELD, paymentRepository.findByParcelRequestId(p.getId()).get().getStatus());
    }
    @Test void test198_resolveDispute_alreadyClosed_throwsIllegalStateException() {
        ParcelRequest p = setupPickedUpParcel();
        DisputeCreateRequest d = new DisputeCreateRequest(); d.setReporterUserId(sender.getId()); d.setParcelRequestId(p.getId()); d.setDisputeReason("Claim");
        Dispute disp = trustAndDisputeService.createDispute(d);
        trustAndDisputeService.resolveDispute(disp.getId(), Dispute.DisputeStatus.REJECTED, "Rejected");

        assertThrows(IllegalStateException.class, () -> trustAndDisputeService.resolveDispute(disp.getId(), Dispute.DisputeStatus.RESOLVED_REFUND_SENDER, "Re-resolve"));
    }
    @Test void test199_getDisputesByStatus_returnsFilteredList() {
        ParcelRequest p = setupPickedUpParcel();
        DisputeCreateRequest d = new DisputeCreateRequest(); d.setReporterUserId(sender.getId()); d.setParcelRequestId(p.getId()); d.setDisputeReason("Reason");
        trustAndDisputeService.createDispute(d);
        assertFalse(trustAndDisputeService.getDisputesByStatus(Dispute.DisputeStatus.OPEN).isEmpty());
    }
    @Test void test200_fullPlatform_endToEnd_realTimeIntegration() {
        // Complete End-to-End Real-Time Journey:
        // 1. Parcel Booking, Escrow Payment, Pickup OTP & Photo Verification, Delivery OTP & Photo Verification
        ParcelRequest deliveredParcel = setupDeliveredParcel();
        assertEquals(ParcelRequest.ParcelStatus.DELIVERED, deliveredParcel.getStatus());
        assertEquals(Payment.EscrowStatus.RELEASED, paymentRepository.findByParcelRequestId(deliveredParcel.getId()).get().getStatus());

        // 2. Ride Booking, Start, Safety Mode Dynamic Buffer, Check-in, and Completion
        RideRequest ride = setupStartedRide();
        SafetyAlert alert = rideService.triggerSafetyEscalation(ride.getId(), "GPS point", SafetyAlert.EscalationStage.STAGE_2_IN_APP_CHECKIN);
        rideService.acknowledgeCheckin(alert.getId(), true);
        RideRequest completedRide = rideService.completeRide(ride.getId());
        assertEquals(RideRequest.RideStatus.COMPLETED, completedRide.getStatus());

        // 3. Two-way Rating
        trustAndDisputeService.submitRating(createSampleRatingReq(5));
        assertTrue(userService.getById(traveler.getId()).getAverageRating() > 0);
    }

    // --- HELPER METHODS ---

    private KycSubmitRequest createSampleKyc(Long userId) {
        KycSubmitRequest k = new KycSubmitRequest();
        k.setUserId(userId);
        k.setAadhaarNumber("1111-2222-3333");
        k.setPanNumber("ABCDE1111Z");
        k.setDrivingLicenceNumber("DL-111");
        k.setRcNumber("KA-01-1111");
        return k;
    }

    private TripCreateRequest createSampleTripRequest(Long travelerId) {
        TripCreateRequest tr = new TripCreateRequest();
        tr.setTravelerId(travelerId);
        tr.setSource("Delhi");
        tr.setDestination("Agra");
        tr.setDepartureTime(LocalDateTime.now().plusDays(1));
        tr.setAvailableCapacityKg(20.0);
        tr.setAvailableSeats(2);
        return tr;
    }

    private ParcelBookingRequest createSampleParcelReq() {
        ParcelBookingRequest p = new ParcelBookingRequest();
        p.setSenderId(sender.getId());
        p.setTripId(trip.getId());
        p.setGoodsDescription("Box");
        p.setDeclaredValue(200.0);
        p.setPickupLocation("Bengaluru");
        p.setDropoffLocation("Hyderabad");
        return p;
    }

    private RideBookingRequest createSampleRideReq() {
        RideBookingRequest r = new RideBookingRequest();
        r.setRiderId(rider.getId());
        r.setTripId(trip.getId());
        r.setPickupLocation("Bengaluru");
        r.setDropoffLocation("Hyderabad");
        r.setSafetyModeEnabled(true);
        r.setEstimatedDurationMinutes(40);
        return r;
    }

    private ParcelRequest setupPickedUpParcel() {
        ParcelRequest req = parcelService.createParcelRequest(createSampleParcelReq());
        parcelService.acceptParcelRequest(req.getId(), traveler.getId());
        parcelService.payEscrow(req.getId(), sender.getId());
        ParcelRequest paid = parcelService.getById(req.getId());

        OtpVerificationRequest v = new OtpVerificationRequest();
        v.setParcelRequestId(req.getId());
        v.setOtp(paid.getPickupOtp());
        v.setPhotoUrl("http://photo.com/pickup.jpg");
        return parcelService.verifyPickup(v);
    }

    private ParcelRequest setupDeliveredParcel() {
        ParcelRequest pickedUp = setupPickedUpParcel();
        OtpVerificationRequest v = new OtpVerificationRequest();
        v.setParcelRequestId(pickedUp.getId());
        v.setOtp(pickedUp.getDeliveryOtp());
        v.setPhotoUrl("http://photo.com/delivery.jpg");
        return parcelService.verifyDelivery(v);
    }

    private RideRequest setupStartedRide() {
        RideRequest ride = rideService.requestRide(createSampleRideReq());
        rideService.acceptRide(ride.getId(), traveler.getId());
        return rideService.startRide(ride.getId());
    }

    private RatingSubmitRequest createSampleRatingReq(int score) {
        RatingSubmitRequest r = new RatingSubmitRequest();
        r.setRaterUserId(sender.getId());
        r.setRateeUserId(traveler.getId());
        r.setScore(score);
        r.setReviewText("Feedback");
        return r;
    }
}
