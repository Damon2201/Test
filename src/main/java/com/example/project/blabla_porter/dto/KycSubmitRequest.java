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

    @NotBlank(message = "PAN number is required")
    @Pattern(regexp = "^[a-zA-Z]{5}[0-9]{4}[a-zA-Z]{1}$", message = "Invalid PAN number format")
    private String panNumber;

    @NotBlank(message = "Driving Licence number is required")
    private String drivingLicenceNumber;

    @NotBlank(message = "RC number is required")
    private String rcNumber;

    private String insuranceNumber;
    private String pucNumber;
    private String selfieUrl;
    private String bankAccountDetails;
}
