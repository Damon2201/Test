package com.example.project.blabla_porter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TripCreateRequest {
    @NotNull(message = "Traveler ID is required")
    @Positive(message = "Traveler ID must be positive")
    private Long travelerId;

    @NotBlank(message = "Source location is required")
    private String source;

    @NotBlank(message = "Destination location is required")
    private String destination;

    @NotNull(message = "Departure time is required")
    private LocalDateTime departureTime;

    private LocalDateTime estimatedArrivalTime;

    @PositiveOrZero(message = "Available capacity cannot be negative")
    private Double availableCapacityKg;

    @PositiveOrZero(message = "Available seats cannot be negative")
    private Integer availableSeats;

    private String ticketOrPnrNumber;

    private String travelMode;
}
