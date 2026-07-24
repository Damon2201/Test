package com.example.project.blabla_porter.dto;

import java.time.LocalDateTime;
import java.util.List;

public class TrackingDto {

    public static class LocationPingRequest {
        private Long tripId;
        private Long travelerId;
        private Double latitude;
        private Double longitude;
        private Double speedKmh = 0.0;
        private Double headingDegrees = 0.0;
        private Integer batteryLevel = 100;

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
    }

    public static class LiveTrackingResponse {
        private Long tripId;
        private String tripStatus;
        private Double currentLatitude;
        private Double currentLongitude;
        private Double speedKmh;
        private Double headingDegrees;
        private Double distanceRemainingKm;
        private Integer estimatedMinutesRemaining;
        private LocalDateTime lastUpdated;
        private Integer totalPingsCount;
        private List<GpsPoint> breadcrumbTrail;

        public Long getTripId() { return tripId; }
        public void setTripId(Long tripId) { this.tripId = tripId; }

        public String getTripStatus() { return tripStatus; }
        public void setTripStatus(String tripStatus) { this.tripStatus = tripStatus; }

        public Double getCurrentLatitude() { return currentLatitude; }
        public void setCurrentLatitude(Double currentLatitude) { this.currentLatitude = currentLatitude; }

        public Double getCurrentLongitude() { return currentLongitude; }
        public void setCurrentLongitude(Double currentLongitude) { this.currentLongitude = currentLongitude; }

        public Double getSpeedKmh() { return speedKmh; }
        public void setSpeedKmh(Double speedKmh) { this.speedKmh = speedKmh; }

        public Double getHeadingDegrees() { return headingDegrees; }
        public void setHeadingDegrees(Double headingDegrees) { this.headingDegrees = headingDegrees; }

        public Double getDistanceRemainingKm() { return distanceRemainingKm; }
        public void setDistanceRemainingKm(Double distanceRemainingKm) { this.distanceRemainingKm = distanceRemainingKm; }

        public Integer getEstimatedMinutesRemaining() { return estimatedMinutesRemaining; }
        public void setEstimatedMinutesRemaining(Integer estimatedMinutesRemaining) { this.estimatedMinutesRemaining = estimatedMinutesRemaining; }

        public LocalDateTime getLastUpdated() { return lastUpdated; }
        public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }

        public Integer getTotalPingsCount() { return totalPingsCount; }
        public void setTotalPingsCount(Integer totalPingsCount) { this.totalPingsCount = totalPingsCount; }

        public List<GpsPoint> getBreadcrumbTrail() { return breadcrumbTrail; }
        public void setBreadcrumbTrail(List<GpsPoint> breadcrumbTrail) { this.breadcrumbTrail = breadcrumbTrail; }
    }

    public static class RouteMapResponse {
        private Long tripId;
        private String sourceName;
        private String destinationName;
        private Double sourceLatitude;
        private Double sourceLongitude;
        private Double destinationLatitude;
        private Double destinationLongitude;
        private Double totalRouteDistanceKm;
        private List<GpsPoint> polylineWaypoints;

        public Long getTripId() { return tripId; }
        public void setTripId(Long tripId) { this.tripId = tripId; }

        public String getSourceName() { return sourceName; }
        public void setSourceName(String sourceName) { this.sourceName = sourceName; }

        public String getDestinationName() { return destinationName; }
        public void setDestinationName(String destinationName) { this.destinationName = destinationName; }

        public Double getSourceLatitude() { return sourceLatitude; }
        public void setSourceLatitude(Double sourceLatitude) { this.sourceLatitude = sourceLatitude; }

        public Double getSourceLongitude() { return sourceLongitude; }
        public void setSourceLongitude(Double sourceLongitude) { this.sourceLongitude = sourceLongitude; }

        public Double getDestinationLatitude() { return destinationLatitude; }
        public void setDestinationLatitude(Double destinationLatitude) { this.destinationLatitude = destinationLatitude; }

        public Double getDestinationLongitude() { return destinationLongitude; }
        public void setDestinationLongitude(Double destinationLongitude) { this.destinationLongitude = destinationLongitude; }

        public Double getTotalRouteDistanceKm() { return totalRouteDistanceKm; }
        public void setTotalRouteDistanceKm(Double totalRouteDistanceKm) { this.totalRouteDistanceKm = totalRouteDistanceKm; }

        public List<GpsPoint> getPolylineWaypoints() { return polylineWaypoints; }
        public void setPolylineWaypoints(List<GpsPoint> polylineWaypoints) { this.polylineWaypoints = polylineWaypoints; }
    }

    public static class GpsPoint {
        private Double latitude;
        private Double longitude;
        private LocalDateTime timestamp;

        public GpsPoint() {}
        public GpsPoint(Double latitude, Double longitude, LocalDateTime timestamp) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.timestamp = timestamp;
        }

        public Double getLatitude() { return latitude; }
        public void setLatitude(Double latitude) { this.latitude = latitude; }

        public Double getLongitude() { return longitude; }
        public void setLongitude(Double longitude) { this.longitude = longitude; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }
}
