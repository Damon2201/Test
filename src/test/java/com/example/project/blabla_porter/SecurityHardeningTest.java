package com.example.project.blabla_porter;

import com.example.project.blabla_porter.config.SecurityHeadersFilter;
import com.example.project.blabla_porter.dto.RatingSubmitRequest;
import com.example.project.blabla_porter.model.*;
import com.example.project.blabla_porter.repository.*;
import com.example.project.blabla_porter.service.RefreshTokenService;
import com.example.project.blabla_porter.service.TrustAndDisputeService;
import com.example.project.blabla_porter.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.example.project.Application.class)
@TestPropertySource(properties = {
    "blabla.seeder.enabled=false",
    "spring.main.allow-bean-definition-overriding=true",
    "spring.datasource.url=jdbc:h2:mem:security_test_db;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=true"
})
@Transactional
@ActiveProfiles("test")
public class SecurityHardeningTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private SecurityHeadersFilter securityHeadersFilter;

    @Autowired
    private TrustAndDisputeService trustAndDisputeService;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private ParcelRequestRepository parcelRequestRepository;

    @Test
    @DisplayName("Admin Password Strength validation check")
    void testAdminPasswordPolicy() {
        // Regular user password policy: min length 8
        assertThrows(IllegalArgumentException.class, () -> 
            userService.validatePasswordPolicy("short", User.UserRole.RIDER)
        );
        assertDoesNotThrow(() -> 
            userService.validatePasswordPolicy("validpass123", User.UserRole.RIDER)
        );

        // Admin user password policy: min length 12 and complexity (upper, lower, digit, special)
        assertThrows(IllegalArgumentException.class, () -> 
            userService.validatePasswordPolicy("password123", User.UserRole.ADMIN) // too short
        );
        assertThrows(IllegalArgumentException.class, () -> 
            userService.validatePasswordPolicy("password123456", User.UserRole.ADMIN) // missing uppercase, special
        );
        assertThrows(IllegalArgumentException.class, () -> 
            userService.validatePasswordPolicy("PASSWORD12345", User.UserRole.ADMIN) // missing lowercase, special
        );
        assertThrows(IllegalArgumentException.class, () -> 
            userService.validatePasswordPolicy("Password12345", User.UserRole.ADMIN) // missing special
        );
        assertDoesNotThrow(() -> 
            userService.validatePasswordPolicy("StrongPass@123", User.UserRole.ADMIN) // fully compliant
        );
    }

    @Test
    @DisplayName("Change password invalidates old refresh tokens")
    void testPasswordChangeInvalidatesSessions() {
        // Register a user
        User user = userRepository.save(User.builder()
                .fullName("Test User").mobileNumber("9999991111").role(User.UserRole.RIDER)
                .passwordHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("oldpassword"))
                .build());

        // Create refresh token
        RefreshToken rt = refreshTokenService.createRefreshToken(user.getId());
        assertNotNull(rt);
        assertTrue(refreshTokenService.findByToken(rt.getToken()).isPresent());

        // Change password
        userService.changePassword(user.getId(), "oldpassword", "newpassword");

        // Verify refresh token is deleted
        assertFalse(refreshTokenService.findByToken(rt.getToken()).isPresent());
    }

    @Test
    @DisplayName("Security Headers and HTTPS redirect check")
    void testSecurityHeadersAndHttpsRedirect() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/api/trips");
        request.setServerName("localhost");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // Test normal request injects security headers
        securityHeadersFilter.doFilter(request, response, chain);
        assertEquals("max-age=31536000; includeSubDomains; preload", response.getHeader("Strict-Transport-Security"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("DENY", response.getHeader("X-Frame-Options"));
        assertNotNull(response.getHeader("Content-Security-Policy"));

        // Test HTTP to HTTPS redirect
        MockHttpServletRequest httpReq = new MockHttpServletRequest();
        httpReq.setMethod("GET");
        httpReq.setRequestURI("/api/trips");
        httpReq.setServerName("blabla-porter.onrender.com");
        httpReq.addHeader("X-Forwarded-Proto", "http");

        MockHttpServletResponse redirectRes = new MockHttpServletResponse();
        securityHeadersFilter.doFilter(httpReq, redirectRes, chain);

        assertEquals(302, redirectRes.getStatus());
        assertEquals("https://blabla-porter.onrender.com/api/trips", redirectRes.getRedirectedUrl());
    }

    @Test
    @DisplayName("Rating collusion and status check validations")
    void testRatingCollusionAndStatusChecks() {
        User sender = userRepository.save(User.builder().fullName("Sender").mobileNumber("9876540001").role(User.UserRole.SENDER).build());
        User traveler = userRepository.save(User.builder().fullName("Traveler").mobileNumber("9876540002").role(User.UserRole.TRAVELER).build());
        User stranger = userRepository.save(User.builder().fullName("Stranger").mobileNumber("9876540003").role(User.UserRole.RIDER).build());

        Trip trip = tripRepository.save(Trip.builder()
                .travelerId(traveler.getId()).source("Blr").destination("Che")
                .departureTime(LocalDateTime.now().plusDays(1)).availableSeats(2).availableCapacityKg(10.0)
                .build());

        ParcelRequest parcel = parcelRequestRepository.save(ParcelRequest.builder()
                .senderId(sender.getId()).tripId(trip.getId()).goodsDescription("Books")
                .pickupLocation("Blr").dropoffLocation("Che").status(ParcelRequest.ParcelStatus.PAID_ESCROW) // NOT DELIVERED
                .build());

        // 1. Try to rate a parcel that is NOT DELIVERED -> should fail
        RatingSubmitRequest reqUndelivered = new RatingSubmitRequest();
        reqUndelivered.setRaterUserId(sender.getId());
        reqUndelivered.setRateeUserId(traveler.getId());
        reqUndelivered.setParcelRequestId(parcel.getId());
        reqUndelivered.setScore(5);
        reqUndelivered.setReviewText("Nice");

        assertThrows(IllegalStateException.class, () -> trustAndDisputeService.submitRating(reqUndelivered));

        // Update status to DELIVERED
        parcel.setStatus(ParcelRequest.ParcelStatus.DELIVERED);
        parcelRequestRepository.save(parcel);

        // 2. Try to rate with a stranger rater (not counterparty) -> should fail
        RatingSubmitRequest reqStranger = new RatingSubmitRequest();
        reqStranger.setRaterUserId(stranger.getId());
        reqStranger.setRateeUserId(traveler.getId());
        reqStranger.setParcelRequestId(parcel.getId());
        reqStranger.setScore(5);
        reqStranger.setReviewText("Nice");

        assertThrows(IllegalArgumentException.class, () -> trustAndDisputeService.submitRating(reqStranger));

        // 3. Valid rating by genuine counterparty -> should succeed
        RatingSubmitRequest reqValid = new RatingSubmitRequest();
        reqValid.setRaterUserId(sender.getId());
        reqValid.setRateeUserId(traveler.getId());
        reqValid.setParcelRequestId(parcel.getId());
        reqValid.setScore(5);
        reqValid.setReviewText("Nice");

        assertDoesNotThrow(() -> trustAndDisputeService.submitRating(reqValid));
    }
}
