package com.example.project.blabla_porter.dto;

import com.example.project.blabla_porter.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String refreshToken;
    private Long id;
    private String fullName;
    private String mobileNumber;
    private String email;
    private User.UserRole role;
    private java.util.Set<User.UserRole> capabilities;
    private User.KycStatus kycStatus;
    private String travelMode;
    private String ticketOrPnrNumber;
    private Boolean passengerApproved;
}
