package com.example.project.blabla_porter.dto;

import com.example.project.blabla_porter.model.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Mobile number must be exactly 10 digits")
    private String mobileNumber;

    @Email(message = "Invalid email format")
    private String email;

    @NotNull(message = "Role is required (SENDER, TRAVELER, RIDER)")
    private User.UserRole role;

    @NotBlank(message = "Password is required")
    private String password;

    @Pattern(regexp = "^(\\d{12})?$", message = "Aadhaar number must be exactly 12 digits if provided")
    private String aadhaarNumber;

    @Pattern(regexp = "^([a-zA-Z]{5}[0-9]{4}[a-zA-Z]{1})?$", message = "Invalid PAN number format")
    private String panNumber;

    private String drivingLicenceNumber;
    private String rcNumber;

    private String registrationOtp;

    private String travelMode;
}
