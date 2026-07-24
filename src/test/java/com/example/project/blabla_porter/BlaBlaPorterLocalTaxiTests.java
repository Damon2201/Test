package com.example.project.blabla_porter;

import com.example.project.blabla_porter.model.*;
import com.example.project.blabla_porter.repository.*;
import com.example.project.blabla_porter.service.LocalTaxiService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.example.project.Application.class)
@TestPropertySource(properties = {
    "blabla.seeder.enabled=false",
    "spring.main.allow-bean-definition-overriding=true",
    "spring.datasource.url=jdbc:h2:mem:local_taxi_tests_db;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=true"
})
@Transactional
@ActiveProfiles("test")
public class BlaBlaPorterLocalTaxiTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LocalCaptainStatusRepository captainStatusRepository;

    @Autowired
    private LocalTaxiBookingRepository taxiBookingRepository;

    @Autowired
    private LocalTaxiService localTaxiService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    @DisplayName("Verify Dynamic Local Taxi Fare Calculation Formula")
    public void testLocalTaxiFareCalculations() {
        // Save Rider & active Captain
        User rider = userRepository.save(User.builder()
                .fullName("Charlie Rider")
                .mobileNumber("9876543212")
                .role(User.UserRole.RIDER)
                .kycStatus(User.KycStatus.NOT_SUBMITTED)
                .build());

        User captain = userRepository.save(User.builder()
                .fullName("Bob Captain")
                .mobileNumber("9876543211")
                .role(User.UserRole.TRAVELER)
                .kycStatus(User.KycStatus.APPROVED)
                .build());

        // Captain goes online at Koramangala
        localTaxiService.toggleAvailability(captain.getId(), true, 12.9352, 77.6245);

        // 1. 3 Km Ride (Koramangala to Domlur: ~3 km)
        // Lat/Lng coords: 12.9352, 77.6245 to 12.9610, 77.6387 (distance ~3.2km)
        LocalTaxiBooking booking3km = localTaxiService.bookTaxi(
                rider.getId(),
                "Koramangala", 12.9352, 77.6245,
                "Domlur", 12.9610, 77.6387,
                true
        );

        assertNotNull(booking3km);
        assertTrue(booking3km.getCalculatedFare() > 40.0 && booking3km.getCalculatedFare() < 60.0, "Expected fare for 3.2km under bike-taxi tier to be around 46.6");
        assertEquals(LocalTaxiBookingStatus.REQUESTED, booking3km.getStatus());
    }

    @Test
    @DisplayName("Verify Proximity Matching pairs with nearest Captain")
    public void testProximityMatching() {
        User rider = userRepository.save(User.builder()
                .fullName("Charlie Rider")
                .mobileNumber("9876543212")
                .role(User.UserRole.RIDER)
                .build());

        // Save two active Captains: one close (Koramangala), one far (Deharadun)
        User closeCap = userRepository.save(User.builder()
                .fullName("Close Captain")
                .mobileNumber("9876543288")
                .role(User.UserRole.TRAVELER)
                .build());

        User farCap = userRepository.save(User.builder()
                .fullName("Far Captain")
                .mobileNumber("9876543299")
                .role(User.UserRole.TRAVELER)
                .build());

        // Toggle both online
        localTaxiService.toggleAvailability(closeCap.getId(), true, 12.9352, 77.6245);
        localTaxiService.toggleAvailability(farCap.getId(), true, 30.3165, 78.0322);

        // Book ride from Koramangala (lat: 12.93, lng: 77.62)
        LocalTaxiBooking booking = localTaxiService.bookTaxi(
                rider.getId(),
                "Koramangala Start", 12.9300, 77.6200,
                "Indiranagar End", 12.9719, 77.6412,
                true
        );

        assertNotNull(booking);
        assertEquals(closeCap.getId(), booking.getCaptainId(), "Should match with nearest captain (Close Captain)");
    }

    @Test
    @DisplayName("Verify Razorpay Escrow payment flow, Trip initialization, and status transitions")
    public void testPaymentAndStateTransitions() {
        User rider = userRepository.save(User.builder()
                .fullName("Charlie Rider")
                .mobileNumber("9876543212")
                .role(User.UserRole.RIDER)
                .build());

        User captain = userRepository.save(User.builder()
                .fullName("Bob Captain")
                .mobileNumber("9876543211")
                .role(User.UserRole.TRAVELER)
                .build());

        localTaxiService.toggleAvailability(captain.getId(), true, 12.9352, 77.6245);

        // 1. Create booking
        LocalTaxiBooking booking = localTaxiService.bookTaxi(
                rider.getId(),
                "Pickup Location", 12.9352, 77.6245,
                "Dropoff Location", 12.9719, 77.6412,
                true
        );

        // 2. Generate Razorpay checkout details
        Map<String, Object> orderDetails = localTaxiService.createPaymentOrder(booking.getId(), rider.getId());
        assertNotNull(orderDetails);
        assertTrue(((String) orderDetails.get("orderId")).startsWith("order_taxi_mock_"));

        // 3. Verify Payment
        Payment payment = localTaxiService.verifyPayment(
                booking.getId(),
                (String) orderDetails.get("orderId"),
                "pay_mock_verified_123",
                "sig_mock_verified_123"
        );

        assertNotNull(payment);
        assertEquals(Payment.EscrowStatus.HELD, payment.getStatus());

        // Refetch booking
        LocalTaxiBooking paidBooking = taxiBookingRepository.findById(booking.getId()).orElseThrow();
        assertEquals(LocalTaxiBookingStatus.PAID, paidBooking.getStatus());
        assertNotNull(paidBooking.getTripId(), "A mock trip should be initialized for active telemetry");

        // 4. Start journey
        LocalTaxiBooking inProgressBooking = localTaxiService.updateBookingStatus(booking.getId(), captain.getId(), LocalTaxiBookingStatus.IN_PROGRESS);
        assertEquals(LocalTaxiBookingStatus.IN_PROGRESS, inProgressBooking.getStatus());

        // 5. Complete journey and release escrow
        LocalTaxiBooking completedBooking = localTaxiService.updateBookingStatus(booking.getId(), captain.getId(), LocalTaxiBookingStatus.COMPLETED);
        assertEquals(LocalTaxiBookingStatus.COMPLETED, completedBooking.getStatus());

        // Check payment released
        List<Payment> finalPayments = paymentRepository.findAll();
        boolean releasedFound = false;
        for (Payment p : finalPayments) {
            if (booking.getId().equals(p.getLocalTaxiBookingId()) && p.getStatus() == Payment.EscrowStatus.RELEASED) {
                releasedFound = true;
                break;
            }
        }
        assertTrue(releasedFound, "Escrow payment must be released upon ride completion");
    }

    @Test
    @DisplayName("Verify exception is thrown when no local Captain is online/available")
    public void testNoCaptainsAvailable() {
        User rider = userRepository.save(User.builder()
                .fullName("Charlie Rider")
                .mobileNumber("9876543212")
                .role(User.UserRole.RIDER)
                .build());

        // Make sure database has no available captains
        captainStatusRepository.deleteAll();

        assertThrows(RuntimeException.class, () -> {
            localTaxiService.bookTaxi(
                    rider.getId(),
                    "Koramangala", 12.9352, 77.6245,
                    "Indiranagar", 12.9719, 77.6412,
                    true
            );
        }, "Should throw exception when no captains are online");
    }

    @Test
    @DisplayName("Verify matching engine selects the truly closest online Captain")
    public void testProximityMatchingPicksTrulyNearest() {
        User rider = userRepository.save(User.builder()
                .fullName("Charlie Rider")
                .mobileNumber("9876543212")
                .role(User.UserRole.RIDER)
                .build());

        // Save three online Captains at different coordinates:
        // 1. Very close (approx 1.5 km away)
        User closeCap = userRepository.save(User.builder()
                .fullName("Close Captain")
                .mobileNumber("9876543281")
                .role(User.UserRole.TRAVELER)
                .build());
        localTaxiService.toggleAvailability(closeCap.getId(), true, 12.9300, 77.6200);

        // 2. Medium distance (approx 4.5 km away)
        User mediumCap = userRepository.save(User.builder()
                .fullName("Medium Captain")
                .mobileNumber("9876543282")
                .role(User.UserRole.TRAVELER)
                .build());
        localTaxiService.toggleAvailability(mediumCap.getId(), true, 12.9500, 77.6500);

        // 3. Very far (approx 50 km away, out of search radius)
        User farCap = userRepository.save(User.builder()
                .fullName("Far Captain")
                .mobileNumber("9876543283")
                .role(User.UserRole.TRAVELER)
                .build());
        localTaxiService.toggleAvailability(farCap.getId(), true, 13.5000, 77.9000);

        // Request ride from Koramangala (12.9352, 77.6245)
        LocalTaxiBooking booking = localTaxiService.bookTaxi(
                rider.getId(),
                "Koramangala", 12.9352, 77.6245,
                "Indiranagar", 12.9719, 77.6412,
                true
        );

        assertNotNull(booking);
        assertEquals(closeCap.getId(), booking.getCaptainId(), "Proximity engine must pair with closeCap (1.5 km) as it is the closest active driver");
    }
}
