package com.example.project.blabla_porter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OtpVerificationRequest {
    @NotNull(message = "Parcel Request ID is required")
    private Long parcelRequestId;

    @NotBlank(message = "OTP is required")
    private String otp;

    @NotBlank(message = "Photo URL is required")
    private String photoUrl;
}
