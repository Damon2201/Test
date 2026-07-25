package com.example.project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.util.TimeZone;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        // Force JVM-wide default timezone before Spring Boot / Hibernate bootstrap
        System.setProperty("user.timezone", "Asia/Kolkata");
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));

        // Load .env file programmatically if exists
        try {
            java.nio.file.Path envPath = java.nio.file.Paths.get(".env");
            if (java.nio.file.Files.exists(envPath)) {
                java.nio.file.Files.lines(envPath)
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .forEach(line -> {
                        int delim = line.indexOf('=');
                        if (delim > 0) {
                            String key = line.substring(0, delim).trim();
                            String value = line.substring(delim + 1).trim();
                            if (System.getProperty(key) == null && System.getenv(key) == null) {
                                System.setProperty(key, value);
                            }
                        }
                    });
            }
        } catch (Exception e) {
            System.err.println("Could not load .env file: " + e.getMessage());
        }
        
        // Initialize Sentry DSN if configured
        String sentryDsn = System.getenv("SENTRY_DSN");
        if (sentryDsn == null || sentryDsn.isBlank()) {
            sentryDsn = System.getProperty("SENTRY_DSN");
        }
        if (sentryDsn != null && !sentryDsn.isBlank()) {
            final String finalDsn = sentryDsn;
            io.sentry.Sentry.init(options -> {
                options.setDsn(finalDsn);
                options.setEnableUncaughtExceptionHandler(true);
            });
            System.out.println("Sentry monitoring initialized successfully.");
        } else {
            System.out.println("Sentry DSN not set. Sentry initialized in silent fallback mode.");
        }

        SpringApplication.run(Application.class, args);
    }
}
