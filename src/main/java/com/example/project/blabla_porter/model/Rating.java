package com.example.project.blabla_porter.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ratings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Rater User ID is required")
    @Column(nullable = false)
    private Long raterUserId;

    @NotNull(message = "Ratee User ID is required")
    @Column(nullable = false)
    private Long rateeUserId;

    private Long parcelRequestId;
    private Long rideRequestId;

    @Min(value = 1, message = "Score must be at least 1")
    @Max(value = 5, message = "Score cannot exceed 5")
    @Column(nullable = false)
    private Integer score;

    private String reviewText;

    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
