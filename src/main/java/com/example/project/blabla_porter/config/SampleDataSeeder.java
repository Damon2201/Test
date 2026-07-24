package com.example.project.blabla_porter.config;

import com.example.project.blabla_porter.dto.*;
import com.example.project.blabla_porter.model.*;
import com.example.project.blabla_porter.repository.*;
import com.example.project.blabla_porter.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(name = "blabla.seeder.enabled", havingValue = "true", matchIfMissing = false)
public class SampleDataSeeder implements CommandLineRunner {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private ParcelRequestRepository parcelRequestRepository;

    @Autowired
    private RideRequestRepository rideRequestRepository;

    @Autowired
    private LocalCaptainStatusRepository localCaptainStatusRepository;

    @Autowired
    private LocalTaxiBookingRepository localTaxiBookingRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private TripService tripService;

    @Autowired
    private ParcelService parcelService;

    @Autowired
    private RideService rideService;

    @Override
    public void run(String... args) throws Exception {
        // ALWAYS clean up same-city taxi and ride records on startup to avoid stale session locks or null fares
        localTaxiBookingRepository.deleteAll();
        localCaptainStatusRepository.deleteAll();
        paymentRepository.deleteAll();
        rideRequestRepository.deleteAll();
        parcelRequestRepository.deleteAll();
        tripRepository.deleteAll();

        // Seed Sender
        User sender = userRepository.findByMobileNumber("9876543210").orElseGet(() -> 
            userRepository.save(User.builder()
                .fullName("Alice Sender")
                .mobileNumber("9876543210")
                .role(User.UserRole.SENDER)
                .passwordHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("password123"))
                .kycStatus(User.KycStatus.NOT_SUBMITTED)
                .build())
        );

        // Seed Captain (Traveler) & Approve KYC
        User captain = userRepository.findByMobileNumber("9876543211").orElseGet(() -> 
            userRepository.save(User.builder()
                .fullName("Bob Captain")
                .mobileNumber("9876543211")
                .role(User.UserRole.TRAVELER)
                .passwordHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("password123"))
                .kycStatus(User.KycStatus.APPROVED)
                .build())
        );

        // Seed Rider
        User rider = userRepository.findByMobileNumber("9876543212").orElseGet(() -> {
            User r = userRepository.save(User.builder()
                .fullName("Charlie Rider")
                .mobileNumber("9876543212")
                .role(User.UserRole.RIDER)
                .passwordHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("password123"))
                .kycStatus(User.KycStatus.NOT_SUBMITTED)
                .build());
            try {
                userService.addTrustedContact(r.getId(), "David (Brother)", "9876543299", "Sibling");
            } catch (Exception e) {}
            return r;
        });

        // Seed Admin
        User admin = userRepository.findByMobileNumber("9876543213").orElseGet(() -> 
            userRepository.save(User.builder()
                .fullName("Platform Admin")
                .mobileNumber("9876543213")
                .role(User.UserRole.ADMIN)
                .passwordHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("password123"))
                .kycStatus(User.KycStatus.APPROVED)
                .build())
        );

        // Seed Trip
        TripCreateRequest trReq = new TripCreateRequest();
        trReq.setTravelerId(captain.getId());
        trReq.setSource("Bengaluru");
        trReq.setDestination("Chennai");
        trReq.setDepartureTime(LocalDateTime.now().plusDays(1));
        trReq.setAvailableCapacityKg(25.0);
        trReq.setAvailableSeats(3);
        Trip trip = tripService.createTrip(trReq);

        // Seed Parcel Request & Escrow
        ParcelBookingRequest pReq = new ParcelBookingRequest();
        pReq.setSenderId(sender.getId());
        pReq.setTripId(trip.getId());
        pReq.setGoodsDescription("MacBook Pro & Documents");
        pReq.setDeclaredValue(1200.0);
        pReq.setEstimatedWeightKg(2.5);
        pReq.setPickupLocation("Indiranagar, Bengaluru");
        pReq.setDropoffLocation("T. Nagar, Chennai");
        ParcelRequest parcel = parcelService.createParcelRequest(pReq);
        parcelService.acceptParcelRequest(parcel.getId(), captain.getId());
        parcelService.payEscrow(parcel.getId(), sender.getId());

        // Seed Ride Request
        RideBookingRequest rBooking = new RideBookingRequest();
        rBooking.setRiderId(rider.getId());
        rBooking.setTripId(trip.getId());
        rBooking.setPickupLocation("Koramangala, Bengaluru");
        rBooking.setDropoffLocation("Guindy, Chennai");
        rBooking.setSafetyModeEnabled(true);
        rBooking.setEstimatedDurationMinutes(300);
        rBooking.setPickupLatitude(12.9352);
        rBooking.setPickupLongitude(77.6245);
        rBooking.setDropoffLatitude(13.0067);
        rBooking.setDropoffLongitude(80.2206);
        rideService.requestRide(rBooking);

        // ALWAYS seed Bob Captain as Active and Available for Same-City Local Taxi
        localCaptainStatusRepository.save(LocalCaptainStatus.builder()
                .captainId(captain.getId())
                .available(true)
                .currentLatitude(12.9716)
                .currentLongitude(77.5946)
                .lastActiveTime(LocalDateTime.now())
                .build());

        System.out.println("==========================================================================");
        System.out.println("  BlaBla + Porter Platform Seeded Successfully (Cleaned & Seeded)!");
        System.out.println("  Swagger UI: http://localhost:8080/swagger-ui.html");
        System.out.println("==========================================================================");
    }
}
