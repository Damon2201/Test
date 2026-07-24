package com.example.project.blabla_porter.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "local_captain_status")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalCaptainStatus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "captain_id", unique = true, nullable = false)
    private Long captainId;

    @Column(name = "available", nullable = false)
    private boolean available;

    @Column(name = "current_latitude")
    private Double currentLatitude;

    @Column(name = "current_longitude")
    private Double currentLongitude;

    @Column(name = "last_active_time")
    private LocalDateTime lastActiveTime;
}
