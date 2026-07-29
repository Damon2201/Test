package com.example.project.blabla_porter;

import com.example.project.blabla_porter.dto.*;
import com.example.project.blabla_porter.model.*;
import com.example.project.blabla_porter.repository.PaymentRepository;
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
    "spring.datasource.url=jdbc:h2:mem:phase4_tests_db;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=true"
})
@Transactional
public class BlaBlaPorterPhase4Tests {

    @Autowired
    private UserService userService;

    @Autowired
    private TripService tripService;

    @Autowired
    private ParcelService parcelService;

    @Autowired
    private TrustAndDisputeService trustAndDisputeService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private com.example.project.blabla_porter.repository.ParcelRequestRepository parcelRequestRepository;

    private User sender;
    private User traveler;
    private Trip trip;
    private ParcelRequest parcel;

    @BeforeEach
    void setUp() {
        // Register Sender
        RegisterRequest sReq = new RegisterRequest();
        sReq.setFullName("Phase4 Sender");
        sReq.setMobileNumber("9700011111");
        sReq.setRole(User.UserRole.SENDER);
        sender = userService.register(sReq);

        // Register & Approve Traveler
        RegisterRequest tReq = new RegisterRequest();
        tReq.setFullName("Phase4 Captain");
        tReq.setMobileNumber("9700022222");
        tReq.setRole(User.UserRole.TRAVELER);
        traveler = userService.register(tReq);

        KycSubmitRequest kycReq = new KycSubmitRequest();
        kycReq.setUserId(traveler.getId());
        kycReq.setAadhaarNumber("4444-5555-6666");
        kycReq.setPanNumber("ABCDE4444Z");
        kycReq.setDrivingLicenceNumber("DL-444");
        kycReq.setRcNumber("KA-04-AA-4444");
        userService.submitKyc(kycReq);
        userService.reviewKyc(traveler.getId(), true);

        // Declare Trip
        TripCreateRequest trReq = new TripCreateRequest();
        trReq.setTravelerId(traveler.getId());
        trReq.setSource("Chennai");
        trReq.setDestination("Bengaluru");
        trReq.setDepartureTime(LocalDateTime.now().plusDays(1));
        trReq.setAvailableCapacityKg(30.0);
        trip = tripService.createTrip(trReq);

        // Create & Pay Parcel
        ParcelBookingRequest pReq = new ParcelBookingRequest();
        pReq.setSenderId(sender.getId());
        pReq.setTripId(trip.getId());
        pReq.setGoodsDescription("Camera Equipment");
        pReq.setDeclaredValue(1500.0);
        pReq.setPickupLocation("Chennai Central");
        pReq.setDropoffLocation("Silk Board, Bengaluru");
        parcel = parcelService.createParcelRequest(pReq);
        parcelService.acceptParcelRequest(parcel.getId(), traveler.getId());
        parcelService.payEscrow(parcel.getId(), sender.getId());

        // Update status to DELIVERED so ratings are allowed under security constraints
        com.example.project.blabla_porter.model.ParcelRequest dbParcel = parcelRequestRepository.findById(parcel.getId()).orElseThrow();
        dbParcel.setStatus(com.example.project.blabla_porter.model.ParcelRequest.ParcelStatus.DELIVERED);
        parcel = parcelRequestRepository.save(dbParcel);
    }

    @Test
    @DisplayName("Phase 4 - Test 1: Submit rating recalculates ratee's average rating dynamically")
    void test1_submitRating_recalculatesAverageRating() {
        // First rating: 4 stars
        RatingSubmitRequest r1 = new RatingSubmitRequest();
        r1.setRaterUserId(sender.getId());
        r1.setRateeUserId(traveler.getId());
        r1.setParcelRequestId(parcel.getId());
        r1.setScore(4);
        r1.setReviewText("Good trip!");
        trustAndDisputeService.submitRating(r1);

        // Create second parcel for the second rating to avoid duplicate checking constraint
        ParcelBookingRequest pReq2 = new ParcelBookingRequest();
        pReq2.setSenderId(sender.getId());
        pReq2.setTripId(trip.getId());
        pReq2.setGoodsDescription("Second Goods");
        pReq2.setDeclaredValue(2000.0);
        pReq2.setPickupLocation("Chennai Central");
        pReq2.setDropoffLocation("Silk Board, Bengaluru");
        ParcelRequest parcel2 = parcelService.createParcelRequest(pReq2);
        parcelService.acceptParcelRequest(parcel2.getId(), traveler.getId());
        parcelService.payEscrow(parcel2.getId(), sender.getId());
        
        com.example.project.blabla_porter.model.ParcelRequest dbParcel2 = parcelRequestRepository.findById(parcel2.getId()).orElseThrow();
        dbParcel2.setStatus(com.example.project.blabla_porter.model.ParcelRequest.ParcelStatus.DELIVERED);
        parcel2 = parcelRequestRepository.save(dbParcel2);

        // Second rating: 2 stars
        RatingSubmitRequest r2 = new RatingSubmitRequest();
        r2.setRaterUserId(sender.getId());
        r2.setRateeUserId(traveler.getId());
        r2.setParcelRequestId(parcel2.getId());
        r2.setScore(2);
        r2.setReviewText("Slight delay");
        trustAndDisputeService.submitRating(r2);

        User updatedTraveler = userService.getById(traveler.getId());

        // Initial default: (5.0 * 0 + 4) / 1 = 4.0; Then (4.0 * 1 + 2) / 2 = 3.0
        assertEquals(3.0, updatedTraveler.getAverageRating(), 0.01);
        assertEquals(2, updatedTraveler.getTotalRatingsCount());
    }

    @Test
    @DisplayName("Phase 4 - Test 2: Create dispute with evidence photo URL in OPEN status")
    void test2_createDispute_withEvidencePhoto() {
        DisputeCreateRequest req = new DisputeCreateRequest();
        req.setReporterUserId(sender.getId());
        req.setParcelRequestId(parcel.getId());
        req.setDisputeReason("Damaged packaging upon handover");
        req.setEvidencePhotoUrl("http://storage.com/damage_proof.jpg");

        Dispute dispute = trustAndDisputeService.createDispute(req);

        assertNotNull(dispute.getId());
        assertEquals(Dispute.DisputeStatus.OPEN, dispute.getStatus());
        assertEquals("http://storage.com/damage_proof.jpg", dispute.getEvidencePhotoUrl());
    }

    @Test
    @DisplayName("Phase 4 - Test 3: Opening dispute without parcel or ride ID throws IllegalArgumentException")
    void test3_createDispute_withoutParcelOrRideId_throwsIllegalArgument() {
        DisputeCreateRequest req = new DisputeCreateRequest();
        req.setReporterUserId(sender.getId());
        req.setDisputeReason("General complaint");

        assertThrows(IllegalArgumentException.class, () -> trustAndDisputeService.createDispute(req));
    }

    @Test
    @DisplayName("Phase 4 - Test 4: Admin resolves dispute (REFUND SENDER) refunds Escrow & cancels parcel")
    void test4_resolveDispute_refundSender_updatesPaymentAndParcelStatus() {
        DisputeCreateRequest req = new DisputeCreateRequest();
        req.setReporterUserId(sender.getId());
        req.setParcelRequestId(parcel.getId());
        req.setDisputeReason("Lost item during transit");
        Dispute dispute = trustAndDisputeService.createDispute(req);

        Dispute resolved = trustAndDisputeService.resolveDispute(dispute.getId(), Dispute.DisputeStatus.RESOLVED_REFUND_SENDER, "Verified lost item. Full refund granted.");

        assertEquals(Dispute.DisputeStatus.RESOLVED_REFUND_SENDER, resolved.getStatus());
        assertEquals("Verified lost item. Full refund granted.", resolved.getAdminNotes());

        Payment payment = paymentRepository.findByParcelRequestId(parcel.getId()).orElseThrow();
        assertEquals(Payment.EscrowStatus.REFUNDED, payment.getStatus());

        ParcelRequest updatedParcel = parcelService.getById(parcel.getId());
        assertEquals(ParcelRequest.ParcelStatus.CANCELLED, updatedParcel.getStatus());
    }

    @Test
    @DisplayName("Phase 4 - Test 5: Admin resolves dispute (RELEASE TRAVELER) releases Escrow & marks delivered")
    void test5_resolveDispute_releaseTraveler_updatesPaymentAndParcelStatus() {
        DisputeCreateRequest req = new DisputeCreateRequest();
        req.setReporterUserId(traveler.getId());
        req.setParcelRequestId(parcel.getId());
        req.setDisputeReason("Sender refused OTP input after dropoff");
        Dispute dispute = trustAndDisputeService.createDispute(req);

        Dispute resolved = trustAndDisputeService.resolveDispute(dispute.getId(), Dispute.DisputeStatus.RESOLVED_RELEASE_TRAVELER, "Evidence verified delivery. Escrow released to traveler.");

        assertEquals(Dispute.DisputeStatus.RESOLVED_RELEASE_TRAVELER, resolved.getStatus());

        Payment payment = paymentRepository.findByParcelRequestId(parcel.getId()).orElseThrow();
        assertEquals(Payment.EscrowStatus.RELEASED, payment.getStatus());

        ParcelRequest updatedParcel = parcelService.getById(parcel.getId());
        assertEquals(ParcelRequest.ParcelStatus.DELIVERED, updatedParcel.getStatus());
    }

    @Test
    @DisplayName("Phase 4 - Test 6: Resolving an already closed dispute throws IllegalStateException")
    void test6_resolveDispute_alreadyResolved_throwsIllegalStateException() {
        DisputeCreateRequest req = new DisputeCreateRequest();
        req.setReporterUserId(sender.getId());
        req.setParcelRequestId(parcel.getId());
        req.setDisputeReason("Package delayed");
        Dispute dispute = trustAndDisputeService.createDispute(req);

        trustAndDisputeService.resolveDispute(dispute.getId(), Dispute.DisputeStatus.REJECTED, "Claim invalid");

        assertThrows(IllegalStateException.class, () -> {
            trustAndDisputeService.resolveDispute(dispute.getId(), Dispute.DisputeStatus.RESOLVED_REFUND_SENDER, "Re-opening claim");
        });
    }

    @Test
    @DisplayName("Phase 4 - Test 7: Get user ratings returns rating list")
    void test7_getUserRatings_returnsUserRatingHistory() {
        RatingSubmitRequest r = new RatingSubmitRequest();
        r.setRaterUserId(sender.getId());
        r.setRateeUserId(traveler.getId());
        r.setParcelRequestId(parcel.getId());
        r.setScore(5);
        r.setReviewText("Excellent service!");
        trustAndDisputeService.submitRating(r);

        List<Rating> ratings = trustAndDisputeService.getUserRatings(traveler.getId());
        assertEquals(1, ratings.size());
        assertEquals("Excellent service!", ratings.get(0).getReviewText());
    }

    @Test
    @DisplayName("Phase 4 - Test 8: Get disputes by status returns filtered open disputes")
    void test8_getDisputesByStatus_returnsFilteredDisputes() {
        DisputeCreateRequest req = new DisputeCreateRequest();
        req.setReporterUserId(sender.getId());
        req.setParcelRequestId(parcel.getId());
        req.setDisputeReason("Item damaged");
        trustAndDisputeService.createDispute(req);

        List<Dispute> openDisputes = trustAndDisputeService.getDisputesByStatus(Dispute.DisputeStatus.OPEN);
        assertFalse(openDisputes.isEmpty());
        assertEquals(Dispute.DisputeStatus.OPEN, openDisputes.get(0).getStatus());
    }

    @Test
    @DisplayName("Phase 4 - Test 9: Get pending ratings returns completed but unrated transactions")
    void test9_getPendingRatings_returnsDeliveredParcelRatings() {
        // Set parcel status to DELIVERED
        parcel.setStatus(ParcelRequest.ParcelStatus.DELIVERED);
        parcelRequestRepository.save(parcel);

        // Check pending ratings for sender
        List<PendingRatingInfo> pending = trustAndDisputeService.getPendingRatings(sender.getId());
        assertEquals(1, pending.size());
        assertEquals("parcel", pending.get(0).getType());
        assertEquals(parcel.getId(), pending.get(0).getTargetId());
        assertEquals(traveler.getId(), pending.get(0).getCounterpartyId());

        // Submit rating from sender to traveler
        RatingSubmitRequest r = new RatingSubmitRequest();
        r.setRaterUserId(sender.getId());
        r.setRateeUserId(traveler.getId());
        r.setParcelRequestId(parcel.getId());
        r.setScore(5);
        r.setReviewText("Done!");
        trustAndDisputeService.submitRating(r);

        // Pending rating list should now be empty
        pending = trustAndDisputeService.getPendingRatings(sender.getId());
        assertTrue(pending.isEmpty());
    }

    @Test
    @DisplayName("Phase 4 - Test 10: Submitting duplicate rating throws IllegalStateException")
    void test10_submitDuplicateRating_throwsIllegalStateException() {
        // Set parcel status to DELIVERED
        parcel.setStatus(ParcelRequest.ParcelStatus.DELIVERED);
        parcelRequestRepository.save(parcel);

        RatingSubmitRequest r = new RatingSubmitRequest();
        r.setRaterUserId(sender.getId());
        r.setRateeUserId(traveler.getId());
        r.setParcelRequestId(parcel.getId());
        r.setScore(5);
        r.setReviewText("Done!");
        trustAndDisputeService.submitRating(r);

        // Submitting duplicate should fail
        assertThrows(IllegalStateException.class, () -> {
            trustAndDisputeService.submitRating(r);
        });
    }
}
