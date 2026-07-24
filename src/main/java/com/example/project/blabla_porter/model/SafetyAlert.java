package com.example.project.blabla_porter.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "safety_alerts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SafetyAlert {

    public enum EscalationStage {
        STAGE_1_SILENT_PING,
        STAGE_2_IN_APP_CHECKIN,
        STAGE_3_TRUSTED_CONTACT_ALERT
    }

    public enum AlertStatus {
        TRIGGERED,
        ACKNOWLEDGED,
        ESCALATED,
        RESOLVED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Ride Request ID is required")
    @Column(nullable = false)
    private Long rideRequestId;

    @NotNull(message = "Rider ID is required")
    @Column(nullable = false)
    private Long riderId;

    private String triggerReason;
    private String lastKnownLocation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EscalationStage escalationStage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = AlertStatus.TRIGGERED;
        }
    }
}
