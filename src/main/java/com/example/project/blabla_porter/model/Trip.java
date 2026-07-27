package com.example.project.blabla_porter.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "trips")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@org.hibernate.annotations.Check(constraints = "available_capacity_kg >= 0 AND available_seats >= 0")
public class Trip {

    public enum TripStatus {
        PLANNED,
        ACTIVE,
        COMPLETED,
        CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Traveler ID is required")
    @Column(nullable = false)
    private Long travelerId;

    @NotBlank(message = "Source location is required")
    @Column(nullable = false)
    private String source;

    @NotBlank(message = "Destination location is required")
    @Column(nullable = false)
    private String destination;

    @NotNull(message = "Departure time is required")
    @Column(nullable = false)
    private LocalDateTime departureTime;

    private LocalDateTime estimatedArrivalTime;

    @jakarta.validation.constraints.PositiveOrZero(message = "Available capacity cannot be negative")
    private Double availableCapacityKg;

    @jakarta.validation.constraints.PositiveOrZero(message = "Available seats cannot be negative")
    private Integer availableSeats;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TripStatus status;

    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = TripStatus.PLANNED;
        }
    }
}
