package com.example.project.blabla_porter.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

@Entity
@Table(name = "location_pings")
public class LocationPing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @NotNull
    @Column(name = "traveler_id", nullable = false)
    private Long travelerId;

    @NotNull
    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @NotNull
    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Column(name = "speed_kmh")
    private Double speedKmh = 0.0;

    @Column(name = "heading_degrees")
    private Double headingDegrees = 0.0;

    @Column(name = "battery_level")
    private Integer batteryLevel = 100;

    @Column(name = "timestamp")
    private LocalDateTime timestamp = LocalDateTime.now();

    public LocationPing() {}

    public LocationPing(Long tripId, Long travelerId, Double latitude, Double longitude, Double speedKmh, Double headingDegrees) {
        this.tripId = tripId;
        this.travelerId = travelerId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.speedKmh = speedKmh;
        this.headingDegrees = headingDegrees;
        this.timestamp = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTripId() { return tripId; }
    public void setTripId(Long tripId) { this.tripId = tripId; }

    public Long getTravelerId() { return travelerId; }
    public void setTravelerId(Long travelerId) { this.travelerId = travelerId; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Double getSpeedKmh() { return speedKmh; }
    public void setSpeedKmh(Double speedKmh) { this.speedKmh = speedKmh; }

    public Double getHeadingDegrees() { return headingDegrees; }
    public void setHeadingDegrees(Double headingDegrees) { this.headingDegrees = headingDegrees; }

    public Integer getBatteryLevel() { return batteryLevel; }
    public void setBatteryLevel(Integer batteryLevel) { this.batteryLevel = batteryLevel; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
