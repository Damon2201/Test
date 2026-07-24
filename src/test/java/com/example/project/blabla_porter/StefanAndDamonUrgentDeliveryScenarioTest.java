package com.example.project.blabla_porter;

import com.example.project.blabla_porter.dto.*;
import com.example.project.blabla_porter.dto.TrackingDto.*;
import com.example.project.blabla_porter.model.*;
import com.example.project.blabla_porter.repository.UserRepository;
import com.example.project.blabla_porter.service.*;
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
    "spring.datasource.url=jdbc:h2:mem:stefan_damon_db;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=true"
})
@Transactional
public class StefanAndDamonUrgentDeliveryScenarioTest {

    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private TripService tripService;
    @Autowired private ParcelService parcelService;
    @Autowired private TrustAndDisputeService trustAndDisputeService;
    @Autowired private TrackingService trackingService;

    @Test
    @DisplayName("Real-World Scenario: Stefan Urgent Parcel Delivery to Hyderabad via Captain Damon")
    public void testStefanAndDamonUrgentDeliveryWorkflow() {
        System.out.println("==========================================================================");
        System.out.println("🎬 STARTING SCENARIO: Stefan's Urgent Parcel Delivery (Bengaluru -> Hyderabad)");
        System.out.println("==========================================================================");

        // STEP 1: Damon Signs Up as a Captain/Traveler going to Hyderabad
        RegisterRequest damonReg = new RegisterRequest();
        damonReg.setFullName("Damon Salvatore");
        damonReg.setMobileNumber("9888822222");
        damonReg.setEmail("damon@mystic.com");
        damonReg.setRole(User.UserRole.TRAVELER);
        User damon = userService.register(damonReg);
        assertNotNull(damon.getId());
        System.out.println("✅ Step 1: Damon Salvatore signed up as Traveler (ID: " + damon.getId() + ")");

        // STEP 2: Damon Submits KYC & Admin Approves Him
        KycSubmitRequest kycReq = new KycSubmitRequest();
        kycReq.setUserId(damon.getId());
        kycReq.setAadhaarNumber("1111-2222-3333");
        kycReq.setPanNumber("DAMON1234F");
        kycReq.setDrivingLicenceNumber("DL-99999-KA");
        kycReq.setRcNumber("KA-05-DS-7777");
        userService.submitKyc(kycReq);
        User damonApproved = userService.reviewKyc(damon.getId(), true);
        assertEquals(User.KycStatus.APPROVED, damonApproved.getKycStatus());
        System.out.println("✅ Step 2: Damon's KYC Approved by Admin! Status: APPROVED");

        // STEP 3: Damon Publishes His Trip from Bengaluru to Hyderabad
        TripCreateRequest tripReq = new TripCreateRequest();
        tripReq.setTravelerId(damon.getId());
        tripReq.setSource("Bengaluru");
        tripReq.setDestination("Hyderabad");
        tripReq.setDepartureTime(LocalDateTime.now().plusHours(4));
        tripReq.setAvailableCapacityKg(25.0);
        tripReq.setAvailableSeats(3);
        Trip damonTrip = tripService.createTrip(tripReq);
        assertNotNull(damonTrip.getId());
        assertEquals("Bengaluru", damonTrip.getSource());
        assertEquals("Hyderabad", damonTrip.getDestination());
        System.out.println("✅ Step 3: Damon published Trip (ID: " + damonTrip.getId() + ") from Bengaluru to Hyderabad");

        // STEP 4: Stefan Signs Up as a Sender with an Urgent Delivery Requirement
        RegisterRequest stefanReg = new RegisterRequest();
        stefanReg.setFullName("Stefan Salvatore");
        stefanReg.setMobileNumber("9888811111");
        stefanReg.setEmail("stefan@mystic.com");
        stefanReg.setRole(User.UserRole.SENDER);
        User stefan = userService.register(stefanReg);
        assertNotNull(stefan.getId());
        System.out.println("✅ Step 4: Stefan Salvatore signed up as Sender (ID: " + stefan.getId() + ")");

        // STEP 5: Stefan Searches for Trips going from Bengaluru to Hyderabad
        List<Trip> availableTrips = tripService.searchTrips("Bengaluru", "Hyderabad");
        assertFalse(availableTrips.isEmpty());
        Trip foundTrip = availableTrips.stream()
                .filter(t -> t.getTravelerId().equals(damon.getId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Damon's trip not found in search results"));
        System.out.println("✅ Step 5: Stefan searched for trips to Hyderabad and found Damon's trip!");

        // STEP 6: Stefan Sends Urgent Parcel Delivery Request to Damon
        ParcelBookingRequest parcelReq = new ParcelBookingRequest();
        parcelReq.setSenderId(stefan.getId());
        parcelReq.setTripId(foundTrip.getId());
        parcelReq.setGoodsDescription("Urgent Medical Supplies & Legal Documents");
        parcelReq.setDeclaredValue(2000.0); // $2000 declared value
        parcelReq.setEstimatedWeightKg(5.0);
        parcelReq.setPickupLocation("Indiranagar, Bengaluru");
        parcelReq.setDropoffLocation("Banjara Hills, Hyderabad");
        ParcelRequest parcel = parcelService.createParcelRequest(parcelReq);
        assertNotNull(parcel.getId());

        // Fare Calculation Verification: $15 base fare + (2% of $2000 = $40 value surcharge) = $55
        assertEquals(55.0, parcel.getCalculatedFare());
        assertEquals(ParcelRequest.ParcelStatus.CREATED, parcel.getStatus());
        System.out.println("✅ Step 6: Stefan requested delivery. Calculated Fare: $" + parcel.getCalculatedFare() + " ($15 base + $40 value surcharge)");

        // STEP 7: Damon Accepts Stefan's Parcel Delivery Request
        ParcelRequest acceptedParcel = parcelService.acceptParcelRequest(parcel.getId(), damon.getId());
        assertEquals(ParcelRequest.ParcelStatus.ACCEPTED, acceptedParcel.getStatus());
        System.out.println("✅ Step 7: Damon accepted Stefan's urgent parcel request!");

        // STEP 8: Stefan Pays Fare into Escrow Vault
        Payment payment = parcelService.payEscrow(parcel.getId(), stefan.getId());
        assertNotNull(payment.getId());
        
        ParcelRequest paidParcel = parcelService.getParcelRequestById(parcel.getId());
        assertEquals(ParcelRequest.ParcelStatus.PAID_ESCROW, paidParcel.getStatus());
        assertNotNull(paidParcel.getPickupOtp());
        assertNotNull(paidParcel.getDeliveryOtp());
        System.out.println("✅ Step 8: Stefan paid $55.0 into Escrow Vault! Status: PAID_ESCROW");
        System.out.println("   🔒 Generated Pickup OTP: " + paidParcel.getPickupOtp());
        System.out.println("   🔒 Generated Delivery OTP: " + paidParcel.getDeliveryOtp());

        // STEP 9: Damon Meets Stefan in Bengaluru, Enters Pickup OTP, and Uploads Pickup Photo
        OtpVerificationRequest pickupReq = new OtpVerificationRequest();
        pickupReq.setParcelRequestId(parcel.getId());
        pickupReq.setOtp(paidParcel.getPickupOtp());
        pickupReq.setPhotoUrl("http://photos.blabla-porter.com/pickup_stefan_damon.jpg");
        ParcelRequest pickedUpParcel = parcelService.verifyPickup(pickupReq);
        assertEquals(ParcelRequest.ParcelStatus.PICKED_UP, pickedUpParcel.getStatus());
        System.out.println("✅ Step 9: Damon verified Pickup OTP with Stefan & uploaded pickup photo proof!");

        // STEP 10: Damon Transmits Real-Time Live GPS Telemetry En Route on National Highway 44
        LocationPingRequest ping1 = new LocationPingRequest();
        ping1.setTripId(foundTrip.getId());
        ping1.setTravelerId(damon.getId());
        ping1.setLatitude(13.5000); // Near Anantapur en route to Hyderabad
        ping1.setLongitude(77.8000);
        ping1.setSpeedKmh(85.0);
        ping1.setHeadingDegrees(15.0);
        ping1.setBatteryLevel(92);
        LocationPing recordedPing = trackingService.recordLocationPing(ping1);
        assertNotNull(recordedPing.getId());

        LiveTrackingResponse tracking = trackingService.getLiveTracking(foundTrip.getId());
        assertTrue(tracking.getDistanceRemainingKm() > 0);
        assertTrue(tracking.getEstimatedMinutesRemaining() > 0);
        System.out.println("✅ Step 10: Live GPS Telemetry Pings Active! Remaining Distance: " + tracking.getDistanceRemainingKm() + " km | Dynamic ETA: " + tracking.getEstimatedMinutesRemaining() + " mins");

        // STEP 11: Damon Arrives in Hyderabad, Enters Delivery OTP, and Uploads Delivery Photo
        OtpVerificationRequest deliveryReq = new OtpVerificationRequest();
        deliveryReq.setParcelRequestId(parcel.getId());
        deliveryReq.setOtp(paidParcel.getDeliveryOtp());
        deliveryReq.setPhotoUrl("http://photos.blabla-porter.com/delivery_stefan_damon.jpg");
        ParcelRequest deliveredParcel = parcelService.verifyDelivery(deliveryReq);
        assertEquals(ParcelRequest.ParcelStatus.DELIVERED, deliveredParcel.getStatus());
        System.out.println("✅ Step 11: Damon arrived in Hyderabad! Verified Delivery OTP & uploaded delivery photo proof!");
        System.out.println("   💰 Escrow Funds ($55.0) Released to Captain Damon!");

        // STEP 12: Stefan Rates Damon 5 Stars for Exceptional Urgent Delivery Service
        RatingSubmitRequest ratingReq = new RatingSubmitRequest();
        ratingReq.setRaterUserId(stefan.getId());
        ratingReq.setRateeUserId(damon.getId());
        ratingReq.setParcelRequestId(parcel.getId());
        ratingReq.setScore(5);
        ratingReq.setReviewText("Urgent delivery saved the day! Delivered safely from Bengaluru to Hyderabad in record time. Highly recommend Damon!");
        Rating review = trustAndDisputeService.submitRating(ratingReq);
        assertNotNull(review.getId());
        assertEquals(5, review.getScore());
        
        User damonRated = userRepository.findById(damon.getId()).orElseThrow();
        assertEquals(5.0, damonRated.getAverageRating());
        assertEquals(1, damonRated.getTotalRatingsCount());
        System.out.println("✅ Step 12: Stefan rated Damon 5 Stars! Damon's Average Rating updated to: 5.0 ⭐");

        System.out.println("==========================================================================");
        System.out.println("🎉 SCENARIO COMPLETED SUCCESSFULLY! ZERO ERRORS, PERFECT EXECUTION!");
        System.out.println("==========================================================================");
    }
}
