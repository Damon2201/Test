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
    "spring.datasource.url=jdbc:h2:mem:testdb_phase2;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=true"
})
@Transactional
public class BlaBlaPorterPhase2Tests {

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
        // Register Sender
        RegisterRequest senderReq = new RegisterRequest();
        senderReq.setFullName("Sam Sender");
        senderReq.setMobileNumber("9000011111");
        senderReq.setRole(User.UserRole.SENDER);
        sender = userService.register(senderReq);

        // Register & Approve Traveler
        RegisterRequest travelerReq = new RegisterRequest();
        travelerReq.setFullName("Travis Traveler");
        travelerReq.setMobileNumber("9000022222");
        travelerReq.setRole(User.UserRole.TRAVELER);
        traveler = userService.register(travelerReq);

        KycSubmitRequest kycReq = new KycSubmitRequest();
        kycReq.setUserId(traveler.getId());
        kycReq.setAadhaarNumber("9999-8888-7777");
        kycReq.setPanNumber("ABCDE9999Z");
        kycReq.setDrivingLicenceNumber("DL-999000");
        kycReq.setRcNumber("KA-05-AA-9999");
        userService.submitKyc(kycReq);
        userService.reviewKyc(traveler.getId(), true);

        // Declare Trip
        TripCreateRequest tripReq = new TripCreateRequest();
        tripReq.setTravelerId(traveler.getId());
        tripReq.setSource("Mumbai");
        tripReq.setDestination("Goa");
        tripReq.setDepartureTime(LocalDateTime.now().plusDays(1));
        tripReq.setAvailableCapacityKg(20.0);
        trip = tripService.createTrip(tripReq);
    }

    @Test
    @DisplayName("Phase 2 - Test 1: Sender creates Parcel Request with calculated fare")
    void test1_createParcelRequest_calculatesFare() {
        ParcelBookingRequest req = new ParcelBookingRequest();
        req.setSenderId(sender.getId());
        req.setTripId(trip.getId());
        req.setGoodsDescription("Electronics & Documents");
        req.setDeclaredValue(500.0); // 2% of $500 = $10 surcharge + $15 base = $25
        req.setEstimatedWeightKg(2.5);
        req.setPickupLocation("Bandra, Mumbai");
        req.setDropoffLocation("Panjim, Goa");

        ParcelRequest parcel = parcelService.createParcelRequest(req);

        assertNotNull(parcel.getId());
        assertEquals(ParcelRequest.ParcelStatus.CREATED, parcel.getStatus());
        assertEquals(25.0, parcel.getCalculatedFare(), 0.01);
    }

    @Test
    @DisplayName("Phase 2 - Test 2: Traveler accepts Parcel Request")
    void test2_travelerAcceptsParcelRequest() {
        ParcelBookingRequest req = createSampleRequest();
        ParcelRequest parcel = parcelService.createParcelRequest(req);

        ParcelRequest acceptedParcel = parcelService.acceptParcelRequest(parcel.getId(), traveler.getId());

        assertEquals(ParcelRequest.ParcelStatus.ACCEPTED, acceptedParcel.getStatus());
    }

    @Test
    @DisplayName("Phase 2 - Test 3: Unauthorized traveler cannot accept request")
    void test3_nonTripTravelerCannotAccept() {
        ParcelBookingRequest req = createSampleRequest();
        ParcelRequest parcel = parcelService.createParcelRequest(req);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            parcelService.acceptParcelRequest(parcel.getId(), 999L);
        });

        assertTrue(ex.getMessage().contains("Only the designated traveler"));
    }

    @Test
    @DisplayName("Phase 2 - Test 4: Sender pays Escrow generating OTPs & HELD payment")
    void test4_senderPaysEscrow_generatesOtpsAndHoldsPayment() {
        ParcelBookingRequest req = createSampleRequest();
        ParcelRequest parcel = parcelService.createParcelRequest(req);
        parcelService.acceptParcelRequest(parcel.getId(), traveler.getId());

        Payment payment = parcelService.payEscrow(parcel.getId(), sender.getId());

        assertNotNull(payment.getId());
        assertEquals(Payment.EscrowStatus.HELD, payment.getStatus());
        assertEquals(parcel.getCalculatedFare(), payment.getAmount());

        ParcelRequest updatedParcel = parcelService.getById(parcel.getId());
        assertEquals(ParcelRequest.ParcelStatus.PAID_ESCROW, updatedParcel.getStatus());
        assertNotNull(updatedParcel.getPickupOtp());
        assertNotNull(updatedParcel.getDeliveryOtp());
        assertEquals(4, updatedParcel.getPickupOtp().length());
        assertEquals(4, updatedParcel.getDeliveryOtp().length());
    }

    @Test
    @DisplayName("Phase 2 - Test 5: In-app chat messaging between Sender and Traveler")
    void test5_inAppChatOpensAfterAcceptance() {
        ParcelBookingRequest req = createSampleRequest();
        ParcelRequest parcel = parcelService.createParcelRequest(req);
        parcelService.acceptParcelRequest(parcel.getId(), traveler.getId());

        ChatMessageRequest msg1 = new ChatMessageRequest();
        msg1.setSenderUserId(sender.getId());
        msg1.setMessage("Hi, where can I hand over the package?");
        chatService.sendMessage(parcel.getId(), msg1);

        ChatMessageRequest msg2 = new ChatMessageRequest();
        msg2.setSenderUserId(traveler.getId());
        msg2.setMessage("Meet me at Bandra station at 9 AM.");
        chatService.sendMessage(parcel.getId(), msg2);

        List<ChatMessage> history = chatService.getChatHistory(parcel.getId());
        assertEquals(2, history.size());
        assertEquals("Hi, where can I hand over the package?", history.get(0).getMessage());
        assertEquals("Meet me at Bandra station at 9 AM.", history.get(1).getMessage());
    }

    @Test
    @DisplayName("Phase 2 - Test 6: Verify Pickup with invalid OTP throws exception")
    void test6_verifyPickup_withInvalidOtp_throwsException() {
        ParcelBookingRequest req = createSampleRequest();
        ParcelRequest parcel = parcelService.createParcelRequest(req);
        parcelService.acceptParcelRequest(parcel.getId(), traveler.getId());
        parcelService.payEscrow(parcel.getId(), sender.getId());

        OtpVerificationRequest verifyReq = new OtpVerificationRequest();
        verifyReq.setParcelRequestId(parcel.getId());
        verifyReq.setOtp("0000"); // Wrong OTP
        verifyReq.setPhotoUrl("http://storage.com/pickup.jpg");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            parcelService.verifyPickup(verifyReq);
        });

        assertTrue(ex.getMessage().contains("Invalid Pickup OTP"));
    }

    @Test
    @DisplayName("Phase 2 - Test 7: Verify Pickup with valid OTP updates status to PICKED_UP")
    void test7_verifyPickup_withValidOtp_updatesStatus() {
        ParcelBookingRequest req = createSampleRequest();
        ParcelRequest parcel = parcelService.createParcelRequest(req);
        parcelService.acceptParcelRequest(parcel.getId(), traveler.getId());
        parcelService.payEscrow(parcel.getId(), sender.getId());

        ParcelRequest paidParcel = parcelService.getById(parcel.getId());

        OtpVerificationRequest verifyReq = new OtpVerificationRequest();
        verifyReq.setParcelRequestId(parcel.getId());
        verifyReq.setOtp(paidParcel.getPickupOtp());
        verifyReq.setPhotoUrl("http://storage.com/pickup.jpg");

        ParcelRequest pickedUpParcel = parcelService.verifyPickup(verifyReq);

        assertEquals(ParcelRequest.ParcelStatus.PICKED_UP, pickedUpParcel.getStatus());
        assertEquals("http://storage.com/pickup.jpg", pickedUpParcel.getPickupPhotoUrl());
    }

    @Test
    @DisplayName("Phase 2 - Test 8: Verify Delivery with valid OTP releases Escrow funds to Traveler")
    void test8_verifyDelivery_withValidOtp_releasesEscrow() {
        ParcelBookingRequest req = createSampleRequest();
        ParcelRequest parcel = parcelService.createParcelRequest(req);
        parcelService.acceptParcelRequest(parcel.getId(), traveler.getId());
        parcelService.payEscrow(parcel.getId(), sender.getId());

        ParcelRequest paidParcel = parcelService.getById(parcel.getId());

        // Perform Pickup
        OtpVerificationRequest pickupReq = new OtpVerificationRequest();
        pickupReq.setParcelRequestId(parcel.getId());
        pickupReq.setOtp(paidParcel.getPickupOtp());
        pickupReq.setPhotoUrl("http://storage.com/pickup.jpg");
        parcelService.verifyPickup(pickupReq);

        // Perform Delivery
        OtpVerificationRequest deliveryReq = new OtpVerificationRequest();
        deliveryReq.setParcelRequestId(parcel.getId());
        deliveryReq.setOtp(paidParcel.getDeliveryOtp());
        deliveryReq.setPhotoUrl("http://storage.com/delivery.jpg");

        ParcelRequest deliveredParcel = parcelService.verifyDelivery(deliveryReq);

        assertEquals(ParcelRequest.ParcelStatus.DELIVERED, deliveredParcel.getStatus());
        assertEquals("http://storage.com/delivery.jpg", deliveredParcel.getDeliveryPhotoUrl());

        // Assert Escrow payment status updated to RELEASED
        Payment payment = paymentRepository.findByParcelRequestId(parcel.getId()).orElseThrow();
        assertEquals(Payment.EscrowStatus.RELEASED, payment.getStatus());
    }

    @Test
    @DisplayName("Phase 2 - Test 9: Cancellation auto-refunds Escrow to Sender")
    void test9_cancellation_autoRefundsEscrow() {
        ParcelBookingRequest req = createSampleRequest();
        ParcelRequest parcel = parcelService.createParcelRequest(req);
        parcelService.acceptParcelRequest(parcel.getId(), traveler.getId());
        parcelService.payEscrow(parcel.getId(), sender.getId());

        ParcelRequest cancelledParcel = parcelService.cancelAndRefund(parcel.getId(), sender.getId());

        assertEquals(ParcelRequest.ParcelStatus.CANCELLED, cancelledParcel.getStatus());

        Payment payment = paymentRepository.findByParcelRequestId(parcel.getId()).orElseThrow();
        assertEquals(Payment.EscrowStatus.REFUNDED, payment.getStatus());
    }

    private ParcelBookingRequest createSampleRequest() {
        ParcelBookingRequest req = new ParcelBookingRequest();
        req.setSenderId(sender.getId());
        req.setTripId(trip.getId());
        req.setGoodsDescription("Box of clothes");
        req.setDeclaredValue(100.0);
        req.setEstimatedWeightKg(1.0);
        req.setPickupLocation("Bandra, Mumbai");
        req.setDropoffLocation("Panjim, Goa");
        return req;
    }
}
