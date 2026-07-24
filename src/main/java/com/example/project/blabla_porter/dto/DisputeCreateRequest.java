package com.example.project.blabla_porter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class DisputeCreateRequest {
    @NotNull(message = "Reporter User ID is required")
    @Positive(message = "Reporter User ID must be positive")
    private Long reporterUserId;

    @Positive(message = "Parcel request ID must be positive")
    private Long parcelRequestId;

    @Positive(message = "Ride request ID must be positive")
    private Long rideRequestId;

    @NotBlank(message = "Dispute reason is required")
    private String disputeReason;

    private String evidencePhotoUrl;
}
