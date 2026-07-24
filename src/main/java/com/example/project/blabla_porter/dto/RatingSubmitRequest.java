package com.example.project.blabla_porter.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RatingSubmitRequest {
    @NotNull(message = "Rater User ID is required")
    private Long raterUserId;

    @NotNull(message = "Ratee User ID is required")
    private Long rateeUserId;

    private Long parcelRequestId;
    private Long rideRequestId;

    @NotNull(message = "Score is required")
    @Min(value = 1, message = "Score must be at least 1")
    @Max(value = 5, message = "Score cannot exceed 5")
    private Integer score;

    private String reviewText;
}
