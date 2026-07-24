package com.example.project.blabla_porter.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class ParcelBookingRequest {
    @NotNull(message = "Sender ID is required")
    @Positive(message = "Sender ID must be positive")
    private Long senderId;

    @Positive(message = "Trip ID must be positive")
    private Long tripId;

    @NotBlank(message = "Goods description is required")
    private String goodsDescription;

    @Positive(message = "Declared value must be positive")
    private Double declaredValue;

    @Positive(message = "Estimated weight must be positive")
    private Double estimatedWeightKg;

    @NotBlank(message = "Pickup location is required")
    private String pickupLocation;

    @NotBlank(message = "Dropoff location is required")
    private String dropoffLocation;

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
