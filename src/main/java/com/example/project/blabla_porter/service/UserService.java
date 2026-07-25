package com.example.project.blabla_porter.service;

import com.example.project.blabla_porter.dto.AuthResponse;
import com.example.project.blabla_porter.dto.KycSubmitRequest;
import com.example.project.blabla_porter.dto.LoginRequest;
import com.example.project.blabla_porter.dto.RegisterRequest;
import com.example.project.blabla_porter.model.TrustedContact;
import com.example.project.blabla_porter.model.User;
import com.example.project.blabla_porter.repository.TrustedContactRepository;
import com.example.project.blabla_porter.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class UserService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TrustedContactRepository trustedContactRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public User register(RegisterRequest req) {
        if (userRepository.existsByMobileNumber(req.getMobileNumber())) {
            throw new IllegalArgumentException("Mobile number is already registered!");
        }

        // Requirement 1: BCrypt Password Hashing (never stored in plaintext)
        String passwordStr = (req.getPassword() != null && !req.getPassword().isBlank()) ? req.getPassword() : "password123";
        String hashed = passwordEncoder.encode(passwordStr);

        // Auto-approve KYC for RIDER, SENDER, ADMIN; only TRAVELER requires manual KYC
        User.KycStatus initialKycStatus;
        if (req.getRole() == User.UserRole.TRAVELER) {
            boolean hasKycDocs = (req.getAadhaarNumber() != null && !req.getAadhaarNumber().isBlank()) ||
                                 (req.getPanNumber() != null && !req.getPanNumber().isBlank()) ||
                                 (req.getDrivingLicenceNumber() != null && !req.getDrivingLicenceNumber().isBlank()) ||
                                 (req.getRcNumber() != null && !req.getRcNumber().isBlank());
            initialKycStatus = hasKycDocs ? User.KycStatus.PENDING_APPROVAL : User.KycStatus.NOT_SUBMITTED;
        } else {
            initialKycStatus = User.KycStatus.APPROVED;
        }

        User user = User.builder()
                .fullName(req.getFullName())
                .mobileNumber(req.getMobileNumber())
                .email(req.getEmail())
                .role(req.getRole())
                .passwordHash(hashed)
                .kycStatus(initialKycStatus)
                .aadhaarNumber(req.getAadhaarNumber())
                .panNumber(req.getPanNumber())
                .drivingLicenceNumber(req.getDrivingLicenceNumber())
                .rcNumber(req.getRcNumber())
                .build();

        return userRepository.save(user);
    }

    public AuthResponse registerPublic(RegisterRequest req) {
        if (req.getRole() == User.UserRole.ADMIN) {
            throw new IllegalArgumentException("Public registration for ADMIN role is strictly forbidden!");
        }
        return registerWithToken(req);
    }

    public AuthResponse registerWithToken(RegisterRequest req) {
        User user = register(req);
        String token = jwtService.generateToken(user);
        com.example.project.blabla_porter.model.RefreshToken rt = refreshTokenService.createRefreshToken(user.getId());
        return AuthResponse.builder()
                .token(token)
                .refreshToken(rt.getToken())
                .id(user.getId())
                .fullName(user.getFullName())
                .mobileNumber(user.getMobileNumber())
                .email(user.getEmail())
                .role(user.getRole())
                .capabilities(user.getCapabilities())
                .kycStatus(user.getKycStatus())
                .build();
    }

    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByMobileNumber(req.getMobileNumber())
                .orElse(null);
        if (user == null) {
            log.warn("Security Alert: Failed login attempt. No account found with mobile number: {}", req.getMobileNumber());
            throw new IllegalArgumentException("No account found with mobile number: " + req.getMobileNumber());
        }

        // Requirement 2: Password Verification against BCrypt Hash
        if (user.getPasswordHash() == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            log.warn("Security Alert: Failed login attempt. Invalid password provided for mobile number: {}", req.getMobileNumber());
            throw new IllegalArgumentException("Invalid password provided!");
        }

        // Requirement 3: Issue JWT with userId and role embedded as claims
        String token = jwtService.generateToken(user);
        com.example.project.blabla_porter.model.RefreshToken rt = refreshTokenService.createRefreshToken(user.getId());

        return AuthResponse.builder()
                .token(token)
                .refreshToken(rt.getToken())
                .id(user.getId())
                .fullName(user.getFullName())
                .mobileNumber(user.getMobileNumber())
                .email(user.getEmail())
                .role(user.getRole())
                .capabilities(user.getCapabilities())
                .kycStatus(user.getKycStatus())
                .build();
    }

    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User submitKyc(KycSubmitRequest req) {
        User user = getById(req.getUserId());
        if (user.getRole() == User.UserRole.ADMIN) {
            throw new IllegalArgumentException("Admins cannot submit KYC!");
        }

        if (user.getRole() != User.UserRole.TRAVELER) {
            user.setRole(User.UserRole.TRAVELER);
        }

        if (req.getAadhaarNumber() == null || req.getAadhaarNumber().isBlank() ||
            req.getPanNumber() == null || req.getPanNumber().isBlank() ||
            req.getDrivingLicenceNumber() == null || req.getDrivingLicenceNumber().isBlank() ||
            req.getRcNumber() == null || req.getRcNumber().isBlank()) {
            throw new IllegalArgumentException("Aadhaar, PAN, Driving Licence, and RC numbers are mandatory for KYC submission!");
        }

        user.setAadhaarNumber(req.getAadhaarNumber());
        user.setPanNumber(req.getPanNumber());
        user.setDrivingLicenceNumber(req.getDrivingLicenceNumber());
        user.setRcNumber(req.getRcNumber());
        user.setKycStatus(User.KycStatus.PENDING_APPROVAL);

        return userRepository.save(user);
    }

    public User approveKyc(Long userId) {
        User user = getById(userId);
        user.setKycStatus(User.KycStatus.APPROVED);
        User saved = userRepository.save(user);

        try {
            notificationService.sendPushToUser(
                    userId,
                    "KYC Approved",
                    "Your KYC verification is approved! You are now a Captain.",
                    Map.of("type", "KYC_STATUS", "status", "APPROVED")
            );
        } catch (Exception e) {
            System.err.println("Failed to send FCM push on approveKyc: " + e.getMessage());
        }

        return saved;
    }

    public List<User> getPendingKycUsers() {
        return userRepository.findByKycStatus(User.KycStatus.PENDING_APPROVAL);
    }

    public User reviewKyc(Long userId, boolean approved) {
        User user = getById(userId);
        if (user.getKycStatus() != User.KycStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("User is not in PENDING_APPROVAL status! Current status: " + user.getKycStatus());
        }
        user.setKycStatus(approved ? User.KycStatus.APPROVED : User.KycStatus.REJECTED);
        User saved = userRepository.save(user);

        try {
            String statusStr = approved ? "APPROVED" : "REJECTED";
            String msg = approved ? "Your KYC verification is approved! You are now a Captain." : "Your KYC verification was rejected. Please check your documents.";
            notificationService.sendPushToUser(
                    userId,
                    "KYC Status Update",
                    msg,
                    Map.of("type", "KYC_STATUS", "status", statusStr)
            );
        } catch (Exception e) {
            System.err.println("Failed to send FCM push on reviewKyc: " + e.getMessage());
        }

        return saved;
    }

    public TrustedContact addTrustedContact(Long userId, String contactName, String contactPhoneNumber, String relationship) {
        User user = getById(userId);
        TrustedContact contact = TrustedContact.builder()
                .userId(user.getId())
                .contactName(contactName)
                .contactPhoneNumber(contactPhoneNumber)
                .relationship(relationship)
                .build();
        return trustedContactRepository.save(contact);
    }

    public List<TrustedContact> getTrustedContacts(Long userId) {
        return trustedContactRepository.findByUserId(userId);
    }
}
