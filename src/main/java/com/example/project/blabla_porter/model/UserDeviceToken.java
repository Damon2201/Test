package com.example.project.blabla_porter.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_device_tokens", 
       uniqueConstraints = {@UniqueConstraint(columnNames = {"fcm_token"})})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "fcm_token", nullable = false, unique = true)
    private String fcmToken;

    @Column(name = "device_type")
    private String deviceType; // e.g. "ANDROID", "IOS", "WEB"

    @Column(name = "last_active", nullable = false)
    private LocalDateTime lastActive;
}
