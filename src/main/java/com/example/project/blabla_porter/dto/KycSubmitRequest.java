package com.example.project.blabla_porter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class KycSubmitRequest {
    @NotNull(message = "User ID is required")
    @Positive(message = "User ID must be positive")
    private Long userId;

    @NotBlank(message = "Aadhaar number is required")
    @Pattern(regexp = "^\\d{12}$", message = "Aadhaar number must be exactly 12 digits")
    private String aadhaarNumber;

    private String panNumber;
    private String drivingLicenceNumber;
    private String rcNumber;
    private String travelMode;

    private String insuranceNumber;
    private String pucNumber;
    private String selfieUrl;
    private String bankAccountDetails;
}
