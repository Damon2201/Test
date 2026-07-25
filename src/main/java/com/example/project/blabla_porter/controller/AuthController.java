package com.example.project.blabla_porter.controller;

import com.example.project.blabla_porter.dto.AuthResponse;
import com.example.project.blabla_porter.dto.LoginRequest;
import com.example.project.blabla_porter.dto.RegisterRequest;
import com.example.project.blabla_porter.model.TrustedContact;
import com.example.project.blabla_porter.model.User;
import com.example.project.blabla_porter.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private com.example.project.blabla_porter.service.SmsService smsService;

    @Autowired
    private com.example.project.blabla_porter.service.RefreshTokenService refreshTokenService;

    @Autowired
    private com.example.project.blabla_porter.service.JwtService jwtService;

    @Autowired
    private com.example.project.blabla_porter.service.RateLimitingService rateLimitingService;

    @Autowired
    private jakarta.servlet.http.HttpServletRequest httpServletRequest;

    private String getClientIp() {
        String xff = httpServletRequest.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return httpServletRequest.getRemoteAddr();
    }

    private final java.util.concurrent.ConcurrentHashMap<String, String> registrationOtpCache = new java.util.concurrent.ConcurrentHashMap<>();

    @PostMapping("/send-registration-otp")
    public java.util.Map<String, String> sendRegistrationOtp(@Valid @RequestBody com.example.project.blabla_porter.dto.RegistrationOtpRequest request) {
        String mobile = request.getMobileNumber().trim();
        if (!rateLimitingService.tryAcquire("otp", mobile, 3, java.time.Duration.ofHours(1))) {
            log.warn("Security Alert: OTP rate limit exceeded for mobile number: {}", mobile);
            throw new com.example.project.blabla_porter.exception.RateLimitExceededException("Too many OTP requests. Please try again after 1 hour.");
        }
        String otp = String.format("%06d", new java.security.SecureRandom().nextInt(1000000));
        registrationOtpCache.put(mobile, otp);

        String message = "Your BlaBla+Porter registration verification code is: " + otp + ". Do not share this code.";
        smsService.sendSms(mobile, message);

        java.util.Map<String, String> response = new java.util.HashMap<>();
        response.put("status", "success");
        response.put("message", "OTP sent successfully to " + mobile);
        return response;
    }

    @Autowired
    private org.springframework.core.env.Environment env;

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        String ip = getClientIp();
        if (!rateLimitingService.tryAcquire("register", ip, 5, java.time.Duration.ofHours(1))) {
            log.warn("Security Alert: Registration rate limit exceeded for IP address: {}", ip);
            throw new com.example.project.blabla_porter.exception.RateLimitExceededException("Too many registration attempts. Please try again after 1 hour.");
        }
        boolean isTestProfile = java.util.Arrays.asList(env.getActiveProfiles()).contains("test");
        String mobile = request.getMobileNumber().trim();
        String expectedOtp = registrationOtpCache.get(mobile);

        if (!isTestProfile) {
            if (expectedOtp == null || request.getRegistrationOtp() == null || !request.getRegistrationOtp().trim().equals(expectedOtp)) {
                throw new IllegalArgumentException("Invalid registration verification OTP!");
            }
            registrationOtpCache.remove(mobile);
        } else {
            if (expectedOtp != null) {
                if (request.getRegistrationOtp() == null || !request.getRegistrationOtp().trim().equals(expectedOtp)) {
                    throw new IllegalArgumentException("Invalid registration verification OTP!");
                }
                registrationOtpCache.remove(mobile);
            }
        }
        return userService.registerPublic(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        String mobile = request.getMobileNumber().trim();
        if (!rateLimitingService.tryAcquire("login", mobile, 5, java.time.Duration.ofMinutes(15))) {
            log.warn("Security Alert: Login rate limit exceeded for mobile number: {}", mobile);
            throw new com.example.project.blabla_porter.exception.RateLimitExceededException("Too many login attempts. Please try again after 15 minutes.");
        }
        return userService.login(request);
    }

    @GetMapping("/users/{id}")
    public User getUserById(@PathVariable Long id) {
        return userService.getById(id);
    }

    @GetMapping("/users")
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @PostMapping("/trusted-contacts")
    public TrustedContact addTrustedContact(@RequestParam Long userId,
                                            @RequestParam String contactName,
                                            @RequestParam String contactPhoneNumber,
                                            @RequestParam(required = false) String relationship) {
        return userService.addTrustedContact(userId, contactName, contactPhoneNumber, relationship);
    }

    @GetMapping("/trusted-contacts/{userId}")
    public List<TrustedContact> getTrustedContacts(@PathVariable Long userId) {
        return userService.getTrustedContacts(userId);
    }

    @PostMapping("/refresh")
    public com.example.project.blabla_porter.dto.TokenRefreshResponse refreshToken(@Valid @RequestBody com.example.project.blabla_porter.dto.TokenRefreshRequest request) {
        String requestRefreshToken = request.getRefreshToken();
        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(token -> {
                    User user = userService.getById(token.getUserId());
                    String accessToken = jwtService.generateToken(user);
                    return new com.example.project.blabla_porter.dto.TokenRefreshResponse(accessToken, token.getToken());
                })
                .orElseThrow(() -> new RuntimeException("Refresh token is not in database!"));
    }

    @PostMapping("/logout")
    public java.util.Map<String, String> logoutUser(@RequestParam(required = false) Long userId,
                                                    jakarta.servlet.http.HttpServletRequest httpRequest) {
        Long authenticatedUserId = (Long) httpRequest.getAttribute("authenticatedUserId");
        Long targetUserId = (authenticatedUserId != null) ? authenticatedUserId : userId;
        if (targetUserId == null) {
            throw new IllegalArgumentException("User ID is required for logout!");
        }
        refreshTokenService.deleteByUserId(targetUserId);
        java.util.Map<String, String> response = new java.util.HashMap<>();
        response.put("status", "success");
        response.put("message", "User logged out successfully!");
        return response;
    }

    @PostMapping("/logout/all")
    public java.util.Map<String, String> logoutAll(jakarta.servlet.http.HttpServletRequest httpRequest) {
        Long authenticatedUserId = (Long) httpRequest.getAttribute("authenticatedUserId");
        if (authenticatedUserId == null) {
            throw new IllegalArgumentException("Unauthorized access!");
        }
        refreshTokenService.deleteByUserId(authenticatedUserId);
        java.util.Map<String, String> response = new java.util.HashMap<>();
        response.put("status", "success");
        response.put("message", "Logged out from all devices successfully!");
        return response;
    }

    @PostMapping("/change-password")
    public java.util.Map<String, String> changePassword(@Valid @RequestBody com.example.project.blabla_porter.dto.ChangePasswordRequest request,
                                                         jakarta.servlet.http.HttpServletRequest httpRequest) {
        Long authenticatedUserId = (Long) httpRequest.getAttribute("authenticatedUserId");
        if (authenticatedUserId == null) {
            throw new IllegalArgumentException("Unauthorized access!");
        }
        userService.changePassword(authenticatedUserId, request.getOldPassword(), request.getNewPassword());
        java.util.Map<String, String> response = new java.util.HashMap<>();
        response.put("status", "success");
        response.put("message", "Password changed successfully and sessions revoked!");
        return response;
    }
}
