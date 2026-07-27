package com.example.project.blabla_porter.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    public enum UserRole {
        SENDER,
        TRAVELER,
        RIDER,
        ADMIN
    }

    public enum KycStatus {
        NOT_SUBMITTED,
        PENDING_APPROVAL,
        APPROVED,
        REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Mobile number is required")
    @Column(unique = true, nullable = false)
    private String mobileNumber;

    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KycStatus kycStatus;

    // KYC Document Metadata
    @Convert(converter = com.example.project.blabla_porter.config.AesAttributeConverter.class)
    private String aadhaarNumber;

    @Convert(converter = com.example.project.blabla_porter.config.AesAttributeConverter.class)
    private String panNumber;
    private String drivingLicenceNumber;
    private String rcNumber;
    private String insuranceNumber;
    private String pucNumber;
    private String selfieUrl;
    private String bankAccountDetails;

    private String travelMode;
    private String ticketOrPnrNumber;

    private Double averageRating;
    private Integer totalRatingsCount;

    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (kycStatus == null) {
            kycStatus = (role == UserRole.TRAVELER) ? KycStatus.NOT_SUBMITTED : KycStatus.APPROVED;
        }
        if (averageRating == null) {
            averageRating = 5.0;
        }
        if (totalRatingsCount == null) {
            totalRatingsCount = 0;
        }
        if (travelMode == null) {
            travelMode = "DRIVING";
        }
    }

    public String getTravelMode() {
        return travelMode == null ? "DRIVING" : travelMode;
    }

    public java.util.Set<UserRole> getCapabilities() {
        java.util.Set<UserRole> caps = new java.util.HashSet<>();
        if (this.role == UserRole.ADMIN) {
            caps.add(UserRole.ADMIN);
            return caps;
        }
        caps.add(UserRole.SENDER);
        caps.add(UserRole.RIDER);
        if (this.role == UserRole.TRAVELER && this.kycStatus == KycStatus.APPROVED) {
            caps.add(UserRole.TRAVELER);
        }
        return caps;
    }
}
