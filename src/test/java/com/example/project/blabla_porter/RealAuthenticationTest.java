package com.example.project.blabla_porter;

import com.example.project.blabla_porter.dto.AuthResponse;
import com.example.project.blabla_porter.dto.LoginRequest;
import com.example.project.blabla_porter.dto.RegisterRequest;
import com.example.project.blabla_porter.model.User;
import com.example.project.blabla_porter.repository.UserRepository;
import com.example.project.blabla_porter.service.JwtService;
import com.example.project.blabla_porter.service.UserService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.example.project.Application.class)
@TestPropertySource(properties = {
    "blabla.seeder.enabled=false",
    "spring.main.allow-bean-definition-overriding=true",
    "spring.datasource.url=jdbc:h2:mem:real_auth_tests_db;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=true"
})
@Transactional
@ActiveProfiles("test")
public class RealAuthenticationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private com.example.project.blabla_porter.service.RefreshTokenService refreshTokenService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    @DisplayName("Requirement 1: Registration hashes password with BCrypt and never stores plaintext")
    void testPasswordHashedWithBCrypt() {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Test User");
        req.setMobileNumber("9990001111");
        req.setEmail("test@example.com");
        req.setRole(User.UserRole.SENDER);
        req.setPassword("SecretPass123");

        User resp = userService.register(req);

        User savedUser = userRepository.findById(resp.getId()).orElseThrow();
        assertNotEquals("SecretPass123", savedUser.getPasswordHash());
        assertTrue(passwordEncoder.matches("SecretPass123", savedUser.getPasswordHash()));
    }

    @Test
    @DisplayName("Requirement 2 & 3: Login requires mobile + password and issues JWT with embedded id and role")
    void testLoginIssuesJwtWithClaims() {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Captain Damon");
        req.setMobileNumber("9990002222");
        req.setRole(User.UserRole.TRAVELER);
        req.setPassword("DamonPass456");
        userService.register(req);

        LoginRequest loginReq = new LoginRequest("9990002222", "DamonPass456");
        AuthResponse authResp = userService.login(loginReq);

        assertNotNull(authResp.getToken());
        assertNotNull(authResp.getRefreshToken());
        Claims claims = jwtService.validateTokenAndGetClaims(authResp.getToken());

        assertEquals(String.valueOf(authResp.getId()), claims.getSubject());
        assertEquals("TRAVELER", claims.get("role", String.class));
    }

    @Test
    @DisplayName("Requirement 5: Server-side blocks public registration for ADMIN role")
    void testAdminRoleBlockedInPublicRegistration() {
        RegisterRequest adminReq = new RegisterRequest();
        adminReq.setFullName("Hacker Admin");
        adminReq.setMobileNumber("9990003333");
        adminReq.setRole(User.UserRole.ADMIN);
        adminReq.setPassword("MaliciousPass");

        Exception exception = assertThrows(IllegalArgumentException.class, () -> userService.registerPublic(adminReq));
        assertTrue(exception.getMessage().contains("Public registration for ADMIN role is strictly forbidden"));
    }

    @Test
    @DisplayName("Verify Aadhaar and PAN are encrypted at rest in the database")
    void testAadhaarAndPanEncryptedAtRest() {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Encrypted User");
        req.setMobileNumber("9990005555");
        req.setRole(User.UserRole.TRAVELER);
        req.setPassword("password123");
        req.setAadhaarNumber("1234-5678-9012");
        req.setPanNumber("ABCDE1234F");

        User savedUser = userService.register(req);

        // Verify that entity getters return the decrypted values automatically
        assertEquals("1234-5678-9012", savedUser.getAadhaarNumber());
        assertEquals("ABCDE1234F", savedUser.getPanNumber());

        // Query the database directly via native SQL to inspect raw columns at rest
        Object[] rawDbRecord = (Object[]) entityManager.createNativeQuery(
                "SELECT aadhaar_number, pan_number FROM users WHERE id = :id")
                .setParameter("id", savedUser.getId())
                .getSingleResult();

        String rawAadhaar = (String) rawDbRecord[0];
        String rawPan = (String) rawDbRecord[1];

        // The values stored in the DB columns MUST not match the plaintext values
        assertNotEquals("1234-5678-9012", rawAadhaar);
        assertNotEquals("ABCDE1234F", rawPan);

        // Verify that they look like encrypted base64 strings
        assertNotNull(rawAadhaar);
        assertNotNull(rawPan);
        assertTrue(rawAadhaar.length() > 10);
        assertTrue(rawPan.length() > 10);

        System.out.println("=== Database Encryption Test ===");
        System.out.println("Plaintext Aadhaar: 1234-5678-9012");
        System.out.println("Encrypted Aadhaar in DB: " + rawAadhaar);
        System.out.println("Plaintext PAN: ABCDE1234F");
        System.out.println("Encrypted PAN in DB: " + rawPan);
        System.out.println("=================================");
    }

    @Test
    @DisplayName("Verify JWT access and refresh token authentication flows")
    void testTokenRefreshFlow() {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Token User");
        req.setMobileNumber("9990006666");
        req.setRole(User.UserRole.SENDER);
        req.setPassword("Password456");

        AuthResponse authResp = userService.registerWithToken(req);
        assertNotNull(authResp.getToken());
        assertNotNull(authResp.getRefreshToken());

        // Verify access token is valid
        Long userId = jwtService.extractUserId(authResp.getToken());
        assertEquals(authResp.getId(), userId);

        // Simulate token refresh using RefreshToken
        com.example.project.blabla_porter.model.RefreshToken dbToken = refreshTokenService.findByToken(authResp.getRefreshToken()).orElseThrow();
        assertEquals(authResp.getId(), dbToken.getUserId());

        // Check verification does not fail on valid token
        assertNotNull(refreshTokenService.verifyExpiration(dbToken));
    }
}
