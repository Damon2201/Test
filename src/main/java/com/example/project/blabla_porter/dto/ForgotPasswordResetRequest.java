package com.example.project.blabla_porter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ForgotPasswordResetRequest {
    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Mobile number must be exactly 10 digits")
    private String mobileNumber;

    @NotBlank(message = "OTP is required")
    private String otp;

    @NotBlank(message = "New password is required")
    private String newPassword;
}
