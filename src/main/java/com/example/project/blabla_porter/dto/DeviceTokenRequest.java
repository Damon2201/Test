package com.example.project.blabla_porter.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeviceTokenRequest {
    @NotBlank(message = "FCM registration token is required")
    private String fcmToken;
    
    private String deviceType; // "ANDROID", "IOS", "WEB"
}
