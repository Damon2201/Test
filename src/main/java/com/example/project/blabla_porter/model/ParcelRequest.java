package com.example.project.blabla_porter.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "parcel_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParcelRequest {

    public enum ParcelStatus {
        CREATED,
        ACCEPTED,
        PAID_ESCROW,
        PICKED_UP,
        IN_TRANSIT,
        DELIVERED,
        CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Sender ID is required")
    @Column(nullable = false)
    private Long senderId;

    private Long tripId;

    @NotBlank(message = "Goods description is required")
    private String goodsDescription;

    private Double declaredValue;
    private Double estimatedWeightKg;

    @NotBlank(message = "Pickup location is required")
    private String pickupLocation;

    @NotBlank(message = "Dropoff location is required")
    private String dropoffLocation;

    private Double pickupLatitude;
    private Double pickupLongitude;
    private Double dropoffLatitude;
    private Double dropoffLongitude;

    private String pickupOtp;
    private String deliveryOtp;
    private String pickupPhotoUrl;
    private String deliveryPhotoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ParcelStatus status;

    private Double calculatedFare;
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = ParcelStatus.CREATED;
        }
    }
}
