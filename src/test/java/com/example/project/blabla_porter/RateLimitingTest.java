package com.example.project.blabla_porter;

import com.example.project.blabla_porter.controller.AuthController;
import com.example.project.blabla_porter.dto.LoginRequest;
import com.example.project.blabla_porter.dto.RegistrationOtpRequest;
import com.example.project.blabla_porter.dto.RegisterRequest;
import com.example.project.blabla_porter.exception.RateLimitExceededException;
import com.example.project.blabla_porter.service.RateLimitingService;
import com.example.project.blabla_porter.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.example.project.Application.class)
@TestPropertySource(properties = {
    "blabla.seeder.enabled=false",
    "spring.main.allow-bean-definition-overriding=true",
    "spring.datasource.url=jdbc:h2:mem:rate_limit_tests_db;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=true"
})
@Transactional
@ActiveProfiles("test")
public class RateLimitingTest {

    @Autowired
    private AuthController authController;

    @Autowired
    private RateLimitingService rateLimitingService;

    @BeforeEach
    void setUp() {
        // Reset rate limiter categories to prevent bleed between test cases
        rateLimitingService.resetCategory("login");
        rateLimitingService.resetCategory("otp");
        rateLimitingService.resetCategory("register");
    }

    @Test
    @DisplayName("Verify that the 6th login attempt within 15 minutes is rejected with HTTP 429 RateLimitExceededException")
    void testLoginRateLimiting() {
        LoginRequest loginReq = new LoginRequest("9991112222", "wrong_password");

        // The first 5 attempts should fail with IllegalArgumentException (user not found / bad credentials)
        for (int i = 1; i <= 5; i++) {
            assertThrows(IllegalArgumentException.class, () -> {
                authController.login(loginReq);
            }, "Attempt " + i + " should have failed with bad credentials exception");
        }

        // The 6th attempt must be blocked by the rate limiter and throw RateLimitExceededException
        Exception rateLimitEx = assertThrows(RateLimitExceededException.class, () -> {
            authController.login(loginReq);
        });

        assertTrue(rateLimitEx.getMessage().contains("Too many login attempts"));
    }

    @Test
    @DisplayName("Verify that the 4th OTP request within an hour is rejected with RateLimitExceededException")
    void testOtpRateLimiting() {
        RegistrationOtpRequest otpReq = new RegistrationOtpRequest();
        otpReq.setMobileNumber("9991112222");

        // The first 3 requests should succeed
        for (int i = 1; i <= 3; i++) {
            var response = authController.sendRegistrationOtp(otpReq);
            assertEquals("success", response.get("status"));
        }

        // The 4th request must be blocked by the rate limiter and throw RateLimitExceededException
        Exception rateLimitEx = assertThrows(RateLimitExceededException.class, () -> {
            authController.sendRegistrationOtp(otpReq);
        });

        assertTrue(rateLimitEx.getMessage().contains("Too many OTP requests"));
    }

    @Test
    @DisplayName("Verify that the 6th registration attempt from same IP within an hour is rate limited")
    void testRegisterRateLimiting() {
        RegisterRequest regReq = new RegisterRequest();
        regReq.setFullName("Test User");
        regReq.setMobileNumber("9991112222");
        regReq.setEmail("test@example.com");
        regReq.setRole(User.UserRole.ADMIN);
        regReq.setPassword("password123");

        // First 5 attempts: since OTP is missing or incorrect, they throw IllegalArgumentException
        for (int i = 1; i <= 5; i++) {
            assertThrows(IllegalArgumentException.class, () -> {
                authController.register(regReq);
            });
        }

        // 6th attempt throws RateLimitExceededException due to IP limit
        Exception rateLimitEx = assertThrows(RateLimitExceededException.class, () -> {
            authController.register(regReq);
        });

        assertTrue(rateLimitEx.getMessage().contains("Too many registration attempts"));
    }
}
