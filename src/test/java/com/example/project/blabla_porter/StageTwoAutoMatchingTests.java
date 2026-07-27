package com.example.project.blabla_porter;

import com.example.project.blabla_porter.dto.ChatMessageRequest;
import com.example.project.blabla_porter.dto.ParcelBookingRequest;
import com.example.project.blabla_porter.dto.TripCreateRequest;
import com.example.project.blabla_porter.model.ChatMessage;
import com.example.project.blabla_porter.model.ParcelRequest;
import com.example.project.blabla_porter.model.Trip;
import com.example.project.blabla_porter.model.User;
import com.example.project.blabla_porter.repository.ParcelRequestRepository;
import com.example.project.blabla_porter.repository.TripRepository;
import com.example.project.blabla_porter.repository.UserRepository;
import com.example.project.blabla_porter.service.ChatService;
import com.example.project.blabla_porter.service.ParcelService;
import com.example.project.blabla_porter.service.TripService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.example.project.Application.class)
@TestPropertySource(properties = {
    "blabla.seeder.enabled=false",
    "spring.main.allow-bean-definition-overriding=true",
    "spring.datasource.url=jdbc:h2:mem:stage_two_tests_db;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=true"
})
@Transactional
@ActiveProfiles("test")
public class StageTwoAutoMatchingTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private ParcelRequestRepository parcelRequestRepository;

    @Autowired
    private TripService tripService;

    @Autowired
    private ParcelService parcelService;

    @Autowired
    private ChatService chatService;

    @Test
    @DisplayName("Test 1: Auto-matching parcel request to existing planned trip")
    void testAutoMatchParcelToTrip() {
        // Create Sender
        User sender = User.builder().fullName("Sender S").mobileNumber("9999911111").role(User.UserRole.SENDER).build();
        userRepository.save(sender);

        // Create Traveler
        User traveler = User.builder().fullName("Traveler T").mobileNumber("9999922222").role(User.UserRole.TRAVELER).kycStatus(User.KycStatus.APPROVED).build();
        userRepository.save(traveler);

        // Register a Planned trip
        TripCreateRequest tripReq = new TripCreateRequest();
        tripReq.setTravelerId(traveler.getId());
        tripReq.setSource("Bengaluru");
        tripReq.setDestination("Hyderabad");
        tripReq.setDepartureTime(LocalDateTime.now().plusDays(2));
        tripReq.setEstimatedArrivalTime(LocalDateTime.now().plusDays(3));
        tripReq.setAvailableCapacityKg(20.0);
        tripReq.setAvailableSeats(3);
        Trip trip = tripService.createTrip(tripReq);

        // Create Parcel Request (with null tripId)
        ParcelBookingRequest pReq = new ParcelBookingRequest();
        pReq.setSenderId(sender.getId());
        pReq.setGoodsDescription("Important Files");
        pReq.setPickupLocation("Indiranagar, Bengaluru");
        pReq.setDropoffLocation("Banjara Hills, Hyderabad");
        pReq.setEstimatedWeightKg(5.0);
        pReq.setDeclaredValue(5000.0);
        pReq.setTripId(null);

        ParcelRequest createdPr = parcelService.createParcelRequest(pReq);

        assertNotNull(createdPr.getTripId());
        assertEquals(trip.getId(), createdPr.getTripId());
    }

    @Test
    @DisplayName("Test 2: Auto-matching pending parcel request when trip is created")
    void testAutoMatchPendingParcelOnTripCreation() {
        // Create Sender
        User sender = User.builder().fullName("Sender S2").mobileNumber("9999933333").role(User.UserRole.SENDER).build();
        userRepository.save(sender);

        // Create Traveler
        User traveler = User.builder().fullName("Traveler T2").mobileNumber("9999944444").role(User.UserRole.TRAVELER).kycStatus(User.KycStatus.APPROVED).build();
        userRepository.save(traveler);

        // Create Parcel Request (with null tripId)
        ParcelBookingRequest pReq = new ParcelBookingRequest();
        pReq.setSenderId(sender.getId());
        pReq.setGoodsDescription("Medicines");
        pReq.setPickupLocation("Whitefield, Bengaluru");
        pReq.setDropoffLocation("Madhapur, Hyderabad");
        pReq.setEstimatedWeightKg(2.0);
        pReq.setDeclaredValue(3000.0);
        pReq.setTripId(null);

        ParcelRequest createdPr = parcelService.createParcelRequest(pReq);
        assertNull(createdPr.getTripId()); // No trip matched initially

        // Now traveler creates a matching trip
        TripCreateRequest tripReq = new TripCreateRequest();
        tripReq.setTravelerId(traveler.getId());
        tripReq.setSource("Bengaluru");
        tripReq.setDestination("Hyderabad");
        tripReq.setDepartureTime(LocalDateTime.now().plusDays(2));
        tripReq.setEstimatedArrivalTime(LocalDateTime.now().plusDays(3));
        tripReq.setAvailableCapacityKg(20.0);
        tripReq.setAvailableSeats(3);
        Trip trip = tripService.createTrip(tripReq);

        // Verify the pending request was matched
        ParcelRequest updatedPr = parcelRequestRepository.findById(createdPr.getId()).orElseThrow();
        assertNotNull(updatedPr.getTripId());
        assertEquals(trip.getId(), updatedPr.getTripId());
    }

    @Test
    @DisplayName("Test 3: Auto-routing to next traveler on rejection")
    void testAutoRoutingOnRejection() {
        // Create Sender
        User sender = User.builder().fullName("Sender S3").mobileNumber("9999955555").role(User.UserRole.SENDER).build();
        userRepository.save(sender);

        // Create Traveler 1 & 2
        User traveler1 = User.builder().fullName("Traveler 1").mobileNumber("9999966666").role(User.UserRole.TRAVELER).kycStatus(User.KycStatus.APPROVED).build();
        userRepository.save(traveler1);

        User traveler2 = User.builder().fullName("Traveler 2").mobileNumber("9999977777").role(User.UserRole.TRAVELER).kycStatus(User.KycStatus.APPROVED).build();
        userRepository.save(traveler2);

        LocalDateTime now = LocalDateTime.now();
        // Register trip 1
        TripCreateRequest tripReq1 = new TripCreateRequest();
        tripReq1.setTravelerId(traveler1.getId());
        tripReq1.setSource("Bengaluru");
        tripReq1.setDestination("Hyderabad");
        tripReq1.setDepartureTime(now.plusDays(2).withHour(8).withMinute(0));
        tripReq1.setEstimatedArrivalTime(now.plusDays(2).withHour(16).withMinute(0));
        tripReq1.setAvailableCapacityKg(10.0);
        tripReq1.setAvailableSeats(3);
        Trip trip1 = tripService.createTrip(tripReq1);

        // Register trip 2
        TripCreateRequest tripReq2 = new TripCreateRequest();
        tripReq2.setTravelerId(traveler2.getId());
        tripReq2.setSource("Bengaluru");
        tripReq2.setDestination("Hyderabad");
        tripReq2.setDepartureTime(now.plusDays(2).withHour(10).withMinute(0)); // departs later on same day
        tripReq2.setEstimatedArrivalTime(now.plusDays(2).withHour(18).withMinute(0));
        tripReq2.setAvailableCapacityKg(10.0);
        tripReq2.setAvailableSeats(3);
        Trip trip2 = tripService.createTrip(tripReq2);

        // Create parcel booking matched to trip 1 (earlier)
        ParcelBookingRequest pReq = new ParcelBookingRequest();
        pReq.setSenderId(sender.getId());
        pReq.setGoodsDescription("Clothes");
        pReq.setPickupLocation("Indiranagar, Bengaluru");
        pReq.setDropoffLocation("Banjara Hills, Hyderabad");
        pReq.setEstimatedWeightKg(4.0);
        pReq.setDeclaredValue(4000.0);
        pReq.setTripId(null);

        ParcelRequest pr = parcelService.createParcelRequest(pReq);
        assertEquals(trip1.getId(), pr.getTripId());

        // Traveler 1 rejects the request
        ParcelRequest routedPr = parcelService.cancelAndRefund(pr.getId(), traveler1.getId());

        // Verify it routed to trip 2 (next available)
        assertEquals(trip2.getId(), routedPr.getTripId());
        assertEquals(ParcelRequest.ParcelStatus.CREATED, routedPr.getStatus());

        // Traveler 2 also cancels/rejects the request
        ParcelRequest cancelledPr = parcelService.cancelAndRefund(pr.getId(), traveler2.getId());

        // Since no more trips, status should be CANCELLED and tripId should be null
        assertNull(cancelledPr.getTripId());
        assertEquals(ParcelRequest.ParcelStatus.CANCELLED, cancelledPr.getStatus());
    }

    @Test
    @DisplayName("Test 4: In-app chat messages validation")
    void testChatHandshake() {
        // Create Sender
        User sender = User.builder().fullName("Sender S4").mobileNumber("9999988888").role(User.UserRole.SENDER).build();
        userRepository.save(sender);

        // Create Traveler
        User traveler = User.builder().fullName("Traveler T4").mobileNumber("9999999999").role(User.UserRole.TRAVELER).kycStatus(User.KycStatus.APPROVED).build();
        userRepository.save(traveler);

        // Create Trip
        TripCreateRequest tripReq = new TripCreateRequest();
        tripReq.setTravelerId(traveler.getId());
        tripReq.setSource("Bengaluru");
        tripReq.setDestination("Hyderabad");
        tripReq.setDepartureTime(LocalDateTime.now().plusDays(2));
        tripReq.setEstimatedArrivalTime(LocalDateTime.now().plusDays(3));
        tripReq.setAvailableCapacityKg(10.0);
        tripReq.setAvailableSeats(3);
        Trip trip = tripService.createTrip(tripReq);

        // Create Parcel request matched to trip
        ParcelBookingRequest pReq = new ParcelBookingRequest();
        pReq.setSenderId(sender.getId());
        pReq.setGoodsDescription("Books");
        pReq.setPickupLocation("Bengaluru");
        pReq.setDropoffLocation("Hyderabad");
        pReq.setEstimatedWeightKg(2.0);
        pReq.setDeclaredValue(1000.0);
        pReq.setTripId(trip.getId());
        ParcelRequest pr = parcelService.createParcelRequest(pReq);
        parcelService.acceptParcelRequest(pr.getId(), traveler.getId());

        // Send a message from Sender to Traveler
        ChatMessageRequest c1 = new ChatMessageRequest();
        c1.setSenderUserId(sender.getId());
        c1.setMessage("Hi traveler, please pick up near metro station");
        ChatMessage msg1 = chatService.sendMessage(pr.getId(), c1);
        assertNotNull(msg1.getId());
        assertEquals(traveler.getId(), msg1.getRecipientUserId());

        // Send response message from Traveler to Sender
        ChatMessageRequest c2 = new ChatMessageRequest();
        c2.setSenderUserId(traveler.getId());
        c2.setMessage("Sure, I'll be there at 5 PM");
        ChatMessage msg2 = chatService.sendMessage(pr.getId(), c2);
        assertNotNull(msg2.getId());
        assertEquals(sender.getId(), msg2.getRecipientUserId());

        // Load chat history
        List<ChatMessage> history = chatService.getChatHistory(pr.getId());
        assertEquals(2, history.size());
        assertEquals("Hi traveler, please pick up near metro station", history.get(0).getMessage());
        assertEquals("Sure, I'll be there at 5 PM", history.get(1).getMessage());
    }
}
