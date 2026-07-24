package com.example.project.blabla_porter.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class RideBookingRequest {
    @NotNull(message = "Rider ID is required")
    @Positive(message = "Rider ID must be positive")
    private Long riderId;

    @NotNull(message = "Trip ID is required")
    @Positive(message = "Trip ID must be positive")
    private Long tripId;

    @NotBlank(message = "Pickup location is required")
    private String pickupLocation;

    @NotBlank(message = "Dropoff location is required")
    private String dropoffLocation;

    private Boolean safetyModeEnabled;

    @Positive(message = "Estimated duration must be positive")
    private Integer estimatedDurationMinutes;

    @NotNull(message = "Pickup latitude is required")
    @Min(value = -90, message = "Latitude must be between -90 and 90")
    @Max(value = 90, message = "Latitude must be between -90 and 90")
    private Double pickupLatitude;

    @NotNull(message = "Pickup longitude is required")
    @Min(value = -180, message = "Longitude must be between -180 and 180")
    @Max(value = 180, message = "Longitude must be between -180 and 180")
    private Double pickupLongitude;

    @NotNull(message = "Dropoff latitude is required")
    @Min(value = -90, message = "Latitude must be between -90 and 90")
    @Max(value = 90, message = "Latitude must be between -90 and 90")
    private Double dropoffLatitude;

    @NotNull(message = "Dropoff longitude is required")
    @Min(value = -180, message = "Longitude must be between -180 and 180")
    @Max(value = 180, message = "Longitude must be between -180 and 180")
    private Double dropoffLongitude;
}
