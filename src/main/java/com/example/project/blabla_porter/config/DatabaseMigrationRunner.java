package com.example.project.blabla_porter.config;

import com.example.project.blabla_porter.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DatabaseMigrationRunner implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Override
    public void run(String... args) {
        try {
            userRepository.updateNullTravelModesToDriving();
            System.out.println(">>> DatabaseMigrationRunner: Successfully set all existing NULL travelModes to DRIVING");
        } catch (Exception e) {
            System.err.println(">>> DatabaseMigrationRunner failed to run: " + e.getMessage());
        }
    }
}
