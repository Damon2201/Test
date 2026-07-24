package com.example.project.blabla_porter.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "disputes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dispute {

    public enum DisputeStatus {
        OPEN,
        UNDER_INVESTIGATION,
        RESOLVED_REFUND_SENDER,
        RESOLVED_RELEASE_TRAVELER,
        REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Reporter User ID is required")
    @Column(nullable = false)
    private Long reporterUserId;

    private Long parcelRequestId;
    private Long rideRequestId;

    @NotBlank(message = "Dispute reason is required")
    @Column(nullable = false)
    private String disputeReason;

    private String evidencePhotoUrl;
    private String adminNotes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisputeStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = DisputeStatus.OPEN;
        }
    }
}
