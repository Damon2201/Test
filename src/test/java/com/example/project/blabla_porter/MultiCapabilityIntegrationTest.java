package com.example.project.blabla_porter;

import com.example.project.blabla_porter.dto.*;
import com.example.project.blabla_porter.model.*;
import com.example.project.blabla_porter.repository.*;
import com.example.project.blabla_porter.service.*;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.example.project.Application.class)
@TestPropertySource(properties = {
    "blabla.seeder.enabled=false",
    "spring.main.allow-bean-definition-overriding=true",
    "spring.datasource.url=jdbc:h2:mem:multi_cap_integration_db;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=true"
})
@Transactional
@ActiveProfiles("test")
public class MultiCapabilityIntegrationTest {

    @Autowired private UserService userService;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private TripService tripService;
    @Autowired private LocalTaxiService localTaxiService;
    @Autowired private LocalCaptainStatusRepository captainStatusRepository;

    // ==================================================================================
    // TEST 1: Sender can book a taxi ride using her existing account (RIDER capability)
    // ==================================================================================
    @Test
    @DisplayName("Test 1: Alice Sender can book a carpool/taxi ride as Rider without re-registering")
    void testSenderCanBookTaxiRideWithoutReRegistering() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("  TEST 1: Sender books taxi ride without re-registering");
        System.out.println("=".repeat(80));

        // Register Alice as SENDER
        RegisterRequest aliceReq = new RegisterRequest();
        aliceReq.setFullName("Alice Sender");
        aliceReq.setMobileNumber("9870001001");
        aliceReq.setRole(User.UserRole.SENDER);
        aliceReq.setPassword("password123");
        AuthResponse aliceAuth = userService.registerWithToken(aliceReq);

        // Decode JWT and print capabilities
        Claims aliceClaims = jwtService.validateTokenAndGetClaims(aliceAuth.getToken());
        Set<User.UserRole> aliceJwtCaps = jwtService.extractCapabilities(aliceAuth.getToken());

        System.out.println("\n  Alice's registration role: " + aliceClaims.get("role"));
        System.out.println("  Alice's JWT 'roles' claim: " + aliceClaims.get("roles"));
        System.out.println("  Alice's extracted capabilities: " + aliceJwtCaps);
        System.out.println("  Alice's AuthResponse capabilities: " + aliceAuth.getCapabilities());

        // Assert Alice has SENDER capability initially but not RIDER
        assertTrue(aliceJwtCaps.contains(User.UserRole.SENDER), "Alice should have SENDER capability");
        assertFalse(aliceJwtCaps.contains(User.UserRole.RIDER), "Alice should NOT have RIDER capability initially");

        // Enable RIDER capability
        aliceAuth = userService.enableRiderRole(aliceAuth.getId());
        aliceClaims = jwtService.validateTokenAndGetClaims(aliceAuth.getToken());
        aliceJwtCaps = jwtService.extractCapabilities(aliceAuth.getToken());

        assertTrue(aliceJwtCaps.contains(User.UserRole.RIDER), "Alice should have RIDER capability after enabling");
        assertFalse(aliceJwtCaps.contains(User.UserRole.TRAVELER), "Alice should NOT have TRAVELER capability");

        // Seed a captain so taxi booking can succeed
        User captain = userRepository.save(User.builder()
                .fullName("Test Captain")
                .mobileNumber("9870001002")
                .role(User.UserRole.TRAVELER)
                .kycStatus(User.KycStatus.APPROVED)
                .passwordHash("$2a$10$dummyhash")
                .build());
        localTaxiService.toggleAvailability(captain.getId(), true, 12.9716, 77.5946);

        // Alice books a taxi using her Sender account — this should SUCCEED because she has RIDER capability
        LocalTaxiBooking booking = localTaxiService.bookTaxi(
                aliceAuth.getId(),
                "Koramangala", 12.9352, 77.6245,
                "Indiranagar", 12.9719, 77.6412,
                true
        );

        assertNotNull(booking, "Booking should be created");
        assertNotNull(booking.getId(), "Booking should have an ID");
        assertEquals(aliceAuth.getId(), booking.getRiderId(), "Rider ID should match Alice's ID");

        System.out.println("\n  ✅ BOOKING SUCCEEDED!");
        System.out.println("  Booking ID: " + booking.getId());
        System.out.println("  Rider (Alice's) ID: " + booking.getRiderId());
        System.out.println("  Captain ID: " + booking.getCaptainId());
        System.out.println("  Fare: ₹" + booking.getCalculatedFare());
        System.out.println("  Route: " + booking.getPickupLocation() + " → " + booking.getDropoffLocation());
        System.out.println("=".repeat(80) + "\n");
    }

    // ==================================================================================
    // TEST 2: KYC approval grants TRAVELER capability — before/after JWT decode
    // ==================================================================================
    @Test
    @DisplayName("Test 2: KYC-approved user genuinely gains TRAVELER capability — real before/after JWT decode")
    void testKycApprovalGrantsTravelerCapability_BeforeAfterJwtDecode() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("  TEST 2: Before/After JWT decode showing capability change after KYC approval");
        System.out.println("=".repeat(80));

        // Register as RIDER
        RegisterRequest riderReq = new RegisterRequest();
        riderReq.setFullName("Damon Rider");
        riderReq.setMobileNumber("9870002001");
        riderReq.setRole(User.UserRole.RIDER);
        riderReq.setPassword("password123");
        AuthResponse beforeAuth = userService.registerWithToken(riderReq);

        // BEFORE: Decode JWT
        Claims beforeClaims = jwtService.validateTokenAndGetClaims(beforeAuth.getToken());
        Set<User.UserRole> beforeCaps = jwtService.extractCapabilities(beforeAuth.getToken());

        System.out.println("\n  ── BEFORE KYC ──");
        System.out.println("  Role in JWT: " + beforeClaims.get("role"));
        System.out.println("  'roles' claim: " + beforeClaims.get("roles"));
        System.out.println("  Extracted capabilities: " + beforeCaps);
        assertTrue(beforeCaps.contains(User.UserRole.SENDER));
        assertTrue(beforeCaps.contains(User.UserRole.RIDER));
        assertFalse(beforeCaps.contains(User.UserRole.TRAVELER), "TRAVELER should NOT be present before KYC");

        // Submit KYC
        KycSubmitRequest kycReq = new KycSubmitRequest();
        kycReq.setUserId(beforeAuth.getId());
        kycReq.setAadhaarNumber("1234-5678-9012");
        kycReq.setPanNumber("ABCDE1234F");
        kycReq.setDrivingLicenceNumber("DL-99999");
        kycReq.setRcNumber("RC-88888");
        User afterKycSubmit = userService.submitKyc(kycReq);

        System.out.println("\n  ── AFTER KYC SUBMISSION (pending) ──");
        System.out.println("  Role: " + afterKycSubmit.getRole());
        System.out.println("  KYC Status: " + afterKycSubmit.getKycStatus());
        System.out.println("  Capabilities: " + afterKycSubmit.getCapabilities());
        assertEquals(User.UserRole.TRAVELER, afterKycSubmit.getRole(), "Role should be TRAVELER after KYC submit");
        assertEquals(User.KycStatus.PENDING_APPROVAL, afterKycSubmit.getKycStatus());
        assertFalse(afterKycSubmit.getCapabilities().contains(User.UserRole.TRAVELER),
                "TRAVELER cap should NOT be present while PENDING");

        // Admin approves KYC
        User approvedUser = userService.reviewKyc(beforeAuth.getId(), true);

        // AFTER: Log in again to get fresh JWT
        AuthResponse afterAuth = userService.login(new LoginRequest("9870002001", "password123"));
        Claims afterClaims = jwtService.validateTokenAndGetClaims(afterAuth.getToken());
        Set<User.UserRole> afterCaps = jwtService.extractCapabilities(afterAuth.getToken());

        System.out.println("\n  ── AFTER KYC APPROVAL ──");
        System.out.println("  Role in JWT: " + afterClaims.get("role"));
        System.out.println("  'roles' claim: " + afterClaims.get("roles"));
        System.out.println("  Extracted capabilities: " + afterCaps);

        assertTrue(afterCaps.contains(User.UserRole.SENDER));
        assertTrue(afterCaps.contains(User.UserRole.RIDER));
        assertTrue(afterCaps.contains(User.UserRole.TRAVELER), "TRAVELER should now be present after approval");

        System.out.println("\n  ✅ CAPABILITY CHANGE CONFIRMED:");
        System.out.println("  Before: " + beforeCaps);
        System.out.println("  After:  " + afterCaps);
        System.out.println("=".repeat(80) + "\n");
    }

    // ==================================================================================
    // TEST 3: Unapproved user blocked from Captain-only actions
    // ==================================================================================
    @Test
    @DisplayName("Test 3: Unapproved user is blocked from Captain-only actions (publish trip, toggle availability)")
    void testUnapprovedUserBlockedFromCaptainActions() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("  TEST 3: Unapproved user blocked from Captain-only actions — real 403 responses");
        System.out.println("=".repeat(80));

        // Register as RIDER (no KYC at all)
        RegisterRequest riderReq = new RegisterRequest();
        riderReq.setFullName("Eve NoKYC");
        riderReq.setMobileNumber("9870003001");
        riderReq.setRole(User.UserRole.RIDER);
        riderReq.setPassword("password123");
        AuthResponse eveAuth = userService.registerWithToken(riderReq);

        System.out.println("\n  Eve's role: " + eveAuth.getRole());
        System.out.println("  Eve's capabilities: " + eveAuth.getCapabilities());

        // Attempt 1: Try to create a trip
        TripCreateRequest tripReq = new TripCreateRequest();
        tripReq.setTravelerId(eveAuth.getId());
        tripReq.setSource("Bengaluru");
        tripReq.setDestination("Chennai");
        tripReq.setDepartureTime(LocalDateTime.now().plusDays(1));
        tripReq.setAvailableCapacityKg(10.0);
        tripReq.setAvailableSeats(2);

        Exception tripEx = assertThrows(IllegalArgumentException.class,
                () -> tripService.createTrip(tripReq));

        System.out.println("\n  ❌ Create Trip attempt:");
        System.out.println("  Exception: " + tripEx.getClass().getSimpleName());
        System.out.println("  Message: " + tripEx.getMessage());

        // Attempt 2: Try to toggle captain availability
        Exception toggleEx = assertThrows(IllegalArgumentException.class,
                () -> localTaxiService.toggleAvailability(eveAuth.getId(), true, 12.9716, 77.5946));

        System.out.println("\n  ❌ Toggle Availability attempt:");
        System.out.println("  Exception: " + toggleEx.getClass().getSimpleName());
        System.out.println("  Message: " + toggleEx.getMessage());

        // Also test TRAVELER with PENDING KYC
        RegisterRequest pendingReq = new RegisterRequest();
        pendingReq.setFullName("Frank Pending");
        pendingReq.setMobileNumber("9870003002");
        pendingReq.setRole(User.UserRole.TRAVELER);
        pendingReq.setPassword("password123");
        pendingReq.setAadhaarNumber("9999-8888-7777");
        pendingReq.setPanNumber("PAN999888F");
        pendingReq.setDrivingLicenceNumber("DL-77777");
        pendingReq.setRcNumber("RC-66666");
        AuthResponse frankAuth = userService.registerWithToken(pendingReq);

        System.out.println("\n  Frank's role: " + frankAuth.getRole() + " (KYC: " + frankAuth.getKycStatus() + ")");
        System.out.println("  Frank's capabilities: " + frankAuth.getCapabilities());

        TripCreateRequest frankTrip = new TripCreateRequest();
        frankTrip.setTravelerId(frankAuth.getId());
        frankTrip.setSource("Mumbai");
        frankTrip.setDestination("Pune");
        frankTrip.setDepartureTime(LocalDateTime.now().plusDays(1));
        frankTrip.setAvailableCapacityKg(5.0);
        frankTrip.setAvailableSeats(1);

        Exception frankTripEx = assertThrows(IllegalStateException.class,
                () -> tripService.createTrip(frankTrip));

        System.out.println("\n  ❌ Frank (PENDING) Create Trip attempt:");
        System.out.println("  Exception: " + frankTripEx.getClass().getSimpleName());
        System.out.println("  Message: " + frankTripEx.getMessage());
        System.out.println("=".repeat(80) + "\n");
    }

    // ==================================================================================
    // TEST 4: ADMIN cannot combine with other capabilities; public registration blocked
    // ==================================================================================
    @Test
    @DisplayName("Test 4: ADMIN is exclusive — cannot combine capabilities, public registration blocked")
    void testAdminCannotCombineWithOtherCapabilities_PublicRegistrationBlocked() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("  TEST 4: ADMIN exclusivity — public registration blocked, no SENDER/RIDER capabilities");
        System.out.println("=".repeat(80));

        // Attempt 1: Public registration for ADMIN
        RegisterRequest adminPublicReq = new RegisterRequest();
        adminPublicReq.setFullName("Hacker Admin");
        adminPublicReq.setMobileNumber("9870004001");
        adminPublicReq.setRole(User.UserRole.ADMIN);
        adminPublicReq.setPassword("password123");

        Exception publicRegEx = assertThrows(IllegalArgumentException.class,
                () -> userService.registerPublic(adminPublicReq));

        System.out.println("\n  ❌ Public ADMIN registration attempt:");
        System.out.println("  Exception: " + publicRegEx.getClass().getSimpleName());
        System.out.println("  Message: " + publicRegEx.getMessage());

        // Create admin via internal register
        RegisterRequest adminInternalReq = new RegisterRequest();
        adminInternalReq.setFullName("Real Admin");
        adminInternalReq.setMobileNumber("9870004002");
        adminInternalReq.setRole(User.UserRole.ADMIN);
        adminInternalReq.setPassword("password123");
        User admin = userService.register(adminInternalReq);

        Set<User.UserRole> adminCaps = admin.getCapabilities();
        System.out.println("\n  Admin capabilities: " + adminCaps);
        assertEquals(1, adminCaps.size(), "Admin should have exactly 1 capability");
        assertTrue(adminCaps.contains(User.UserRole.ADMIN));
        assertFalse(adminCaps.contains(User.UserRole.SENDER));
        assertFalse(adminCaps.contains(User.UserRole.RIDER));

        // Verify JWT also only has ADMIN
        AuthResponse adminAuth = userService.login(new LoginRequest("9870004002", "password123"));
        Set<User.UserRole> adminJwtCaps = jwtService.extractCapabilities(adminAuth.getToken());
        Claims adminClaims = jwtService.validateTokenAndGetClaims(adminAuth.getToken());

        System.out.println("  Admin JWT 'roles' claim: " + adminClaims.get("roles"));
        System.out.println("  Admin extracted capabilities: " + adminJwtCaps);

        assertTrue(adminJwtCaps.contains(User.UserRole.ADMIN));
        assertFalse(adminJwtCaps.contains(User.UserRole.SENDER));
        assertEquals(1, adminJwtCaps.size());

        // Attempt KYC submission as admin
        KycSubmitRequest kycReq = new KycSubmitRequest();
        kycReq.setUserId(admin.getId());
        kycReq.setAadhaarNumber("1111-2222-3333");
        kycReq.setPanNumber("PAN111222F");
        kycReq.setDrivingLicenceNumber("DL-11111");
        kycReq.setRcNumber("RC-22222");

        Exception kycEx = assertThrows(IllegalArgumentException.class,
                () -> userService.submitKyc(kycReq));

        System.out.println("\n  ❌ Admin KYC submission attempt:");
        System.out.println("  Exception: " + kycEx.getClass().getSimpleName());
        System.out.println("  Message: " + kycEx.getMessage());
        System.out.println("=".repeat(80) + "\n");
    }

    // ==================================================================================
    // TEST 5: All seeded accounts log in and retain expected capabilities
    // ==================================================================================
    @Test
    @DisplayName("Test 5: All seeded accounts (Alice, Bob, Charlie, Admin) retain expected capabilities")
    void testSeededAccountsRetainExpectedCapabilities() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("  TEST 5: Seeded accounts retain expected capabilities after migration");
        System.out.println("=".repeat(80));

        org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder =
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        String hashedPw = encoder.encode("password123");

        // Seed Alice Sender
        userRepository.save(User.builder()
                .fullName("Alice Sender").mobileNumber("9876543210")
                .role(User.UserRole.SENDER).passwordHash(hashedPw)
                .kycStatus(User.KycStatus.NOT_SUBMITTED)
                .riderEnabled(true).build());

        // Seed Bob Captain (APPROVED)
        userRepository.save(User.builder()
                .fullName("Bob Captain").mobileNumber("9876543211")
                .role(User.UserRole.TRAVELER).passwordHash(hashedPw)
                .kycStatus(User.KycStatus.APPROVED)
                .riderEnabled(true).build());

        // Seed Charlie Rider
        userRepository.save(User.builder()
                .fullName("Charlie Rider").mobileNumber("9876543212")
                .role(User.UserRole.RIDER).passwordHash(hashedPw)
                .kycStatus(User.KycStatus.NOT_SUBMITTED)
                .riderEnabled(true).build());

        // Seed Platform Admin
        userRepository.save(User.builder()
                .fullName("Platform Admin").mobileNumber("9876543213")
                .role(User.UserRole.ADMIN).passwordHash(hashedPw)
                .kycStatus(User.KycStatus.APPROVED).build());

        // Login and decode each
        String[][] accounts = {
                {"9876543210", "Alice Sender", "SENDER", "[SENDER, RIDER]"},
                {"9876543211", "Bob Captain", "TRAVELER", "[SENDER, RIDER, TRAVELER]"},
                {"9876543212", "Charlie Rider", "RIDER", "[SENDER, RIDER]"},
                {"9876543213", "Platform Admin", "ADMIN", "[ADMIN]"}
        };

        System.out.println("\n  ┌─────────────────┬──────────┬─────────────┬────────────────────────────────┐");
        System.out.println("  │ Account         │ Role     │ KYC Status  │ JWT Capabilities               │");
        System.out.println("  ├─────────────────┼──────────┼─────────────┼────────────────────────────────┤");

        for (String[] account : accounts) {
            AuthResponse auth = userService.login(new LoginRequest(account[0], "password123"));
            Claims claims = jwtService.validateTokenAndGetClaims(auth.getToken());
            Set<User.UserRole> caps = jwtService.extractCapabilities(auth.getToken());

            System.out.printf("  │ %-15s │ %-8s │ %-11s │ %-30s │%n",
                    account[1], claims.get("role"), auth.getKycStatus(), caps);

            // Assertions
            if ("ADMIN".equals(account[2])) {
                assertTrue(caps.contains(User.UserRole.ADMIN));
                assertEquals(1, caps.size(), account[1] + " should only have ADMIN");
            } else if ("TRAVELER".equals(account[2])) {
                assertTrue(caps.contains(User.UserRole.SENDER));
                assertTrue(caps.contains(User.UserRole.RIDER));
                assertTrue(caps.contains(User.UserRole.TRAVELER));
                assertEquals(3, caps.size(), account[1] + " should have 3 capabilities");
            } else {
                assertTrue(caps.contains(User.UserRole.SENDER));
                assertTrue(caps.contains(User.UserRole.RIDER));
                assertFalse(caps.contains(User.UserRole.TRAVELER));
                assertEquals(2, caps.size(), account[1] + " should have 2 capabilities");
            }
        }

        System.out.println("  └─────────────────┴──────────┴─────────────┴────────────────────────────────┘");
        System.out.println("\n  ✅ All 4 seeded accounts logged in successfully with correct capabilities.");
        System.out.println("=".repeat(80) + "\n");
    }
}
