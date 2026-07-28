package com.example.project.blabla_porter.config;

import com.example.project.blabla_porter.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DatabaseMigrationRunner implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.example.project.blabla_porter.repository.TripRepository tripRepository;

    @Override
    public void run(String... args) {
        try {
            userRepository.updateNullTravelModesToDriving();
            System.out.println(">>> DatabaseMigrationRunner: Successfully set all existing NULL travelModes to DRIVING");
            tripRepository.updateNullTravelModesToDriving();
            System.out.println(">>> DatabaseMigrationRunner: Successfully set all existing NULL trip travelModes to DRIVING");
            userRepository.updateNullRiderEnabledToTrue();
            System.out.println(">>> DatabaseMigrationRunner: Successfully set all existing NULL riderEnabled to true");
            userRepository.migrateExistingPassengerCouriers();
            System.out.println(">>> DatabaseMigrationRunner: Successfully migrated existing Passenger Couriers to SENDER with passengerApproved = true");
            userRepository.updateNullPassengerApprovedToFalse();
            System.out.println(">>> DatabaseMigrationRunner: Successfully set remaining NULL passengerApproved to false");
        } catch (Exception e) {
            System.err.println(">>> DatabaseMigrationRunner failed to run: " + e.getMessage());
        }
    }
}
