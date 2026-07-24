package com.example.project.blabla_porter.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "local_taxi_bookings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalTaxiBooking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rider_id", nullable = false)
    private Long riderId;

    @Column(name = "captain_id")
    private Long captainId;

    @Column(name = "pickup_location", nullable = false)
    private String pickupLocation;

    @Column(name = "dropoff_location", nullable = false)
    private String dropoffLocation;

    @Column(name = "pickup_latitude", nullable = false)
    private Double pickupLatitude;

    @Column(name = "pickup_longitude", nullable = false)
    private Double pickupLongitude;

    @Column(name = "dropoff_latitude", nullable = false)
    private Double dropoffLatitude;

    @Column(name = "dropoff_longitude", nullable = false)
    private Double dropoffLongitude;

    @Column(name = "calculated_fare", nullable = false)
    private Double calculatedFare;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LocalTaxiBookingStatus status;

    @Column(name = "safety_mode_enabled", nullable = false)
    private boolean safetyModeEnabled;

    @Column(name = "razorpay_order_id")
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id")
    private String razorpayPaymentId;

    @Column(name = "razorpay_signature")
    private String razorpaySignature;

    @Column(name = "trip_id")
    private Long tripId; // Link to a Trip for tracking telemetry

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
