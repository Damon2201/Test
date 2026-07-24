package com.example.project.blabla_porter.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    public enum EscrowStatus {
        HELD,
        RELEASED,
        REFUNDED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long parcelRequestId;

    private Long senderId;

    private Long rideRequestId;
    private Long riderId;
    private Long localTaxiBookingId;

    private Long travelerId;

    @Column(nullable = false)
    private Double amount;

    private Double valueSurcharge;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EscrowStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String razorpaySignature;


    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = EscrowStatus.HELD;
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
