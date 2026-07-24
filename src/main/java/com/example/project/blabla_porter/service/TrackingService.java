package com.example.project.blabla_porter.service;

import com.example.project.blabla_porter.dto.TrackingDto.*;
import com.example.project.blabla_porter.model.LocationPing;
import com.example.project.blabla_porter.model.Trip;
import com.example.project.blabla_porter.repository.LocationPingRepository;
import com.example.project.blabla_porter.repository.TripRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TrackingService {

    @Autowired
    private LocationPingRepository locationPingRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private com.example.project.blabla_porter.repository.LocalTaxiBookingRepository localTaxiBookingRepository;

    @Autowired
    private com.example.project.blabla_porter.repository.RideRequestRepository rideRequestRepository;

    @Autowired
    private com.example.project.blabla_porter.repository.ParcelRequestRepository parcelRequestRepository;

    private static final Map<String, double[]> CITY_COORDINATES = new HashMap<>();

    static {
        CITY_COORDINATES.put("bengaluru", new double[]{12.9716, 77.5946});
        CITY_COORDINATES.put("bangalore", new double[]{12.9716, 77.5946});
        CITY_COORDINATES.put("chennai", new double[]{13.0827, 80.2707});
        CITY_COORDINATES.put("delhi", new double[]{28.6139, 77.2090});
        CITY_COORDINATES.put("mumbai", new double[]{19.0760, 72.8777});
        CITY_COORDINATES.put("hyderabad", new double[]{17.3850, 78.4867});
        CITY_COORDINATES.put("pune", new double[]{18.5204, 73.8567});
    }

    @Transactional
    public LocationPing recordLocationPing(LocationPingRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Location ping request cannot be null");
        }
        if (request.getTripId() == null || request.getTravelerId() == null) {
            throw new IllegalArgumentException("Trip ID and Traveler ID are required");
        }
        if (request.getLatitude() == null || request.getLatitude() < -90.0 || request.getLatitude() > 90.0) {
            throw new IllegalArgumentException("Invalid latitude value. Must be between -90 and 90");
        }
        if (request.getLongitude() == null || request.getLongitude() < -180.0 || request.getLongitude() > 180.0) {
            throw new IllegalArgumentException("Invalid longitude value. Must be between -180 and 180");
        }

        Trip trip = tripRepository.findById(request.getTripId())
                .orElseThrow(() -> new RuntimeException("Trip not found with ID: " + request.getTripId()));

        if (!trip.getTravelerId().equals(request.getTravelerId())) {
            throw new IllegalArgumentException("Only the designated traveler can send location pings for this trip");
        }

        LocationPing ping = new LocationPing(
                request.getTripId(),
                request.getTravelerId(),
                request.getLatitude(),
                request.getLongitude(),
                request.getSpeedKmh() != null ? request.getSpeedKmh() : 0.0,
                request.getHeadingDegrees() != null ? request.getHeadingDegrees() : 0.0
        );
        if (request.getBatteryLevel() != null) {
            ping.setBatteryLevel(request.getBatteryLevel());
        }

        return locationPingRepository.save(ping);
    }

    public LiveTrackingResponse getLiveTracking(Long tripId) {
        if (tripId == null) {
            throw new IllegalArgumentException("Trip ID cannot be null");
        }

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found with ID: " + tripId));

        List<LocationPing> pings = locationPingRepository.findByTripIdOrderByTimestampAsc(tripId);

        LiveTrackingResponse response = new LiveTrackingResponse();
        response.setTripId(tripId);
        response.setTripStatus(trip.getStatus().name());
        response.setTotalPingsCount(pings.size());

        double[][] coords = resolvePreciseCoordinates(tripId, trip.getSource(), trip.getDestination());
        double[] srcCoords = coords[0];
        double[] destCoords = coords[1];

        if (pings.isEmpty()) {
            response.setCurrentLatitude(srcCoords[0]);
            response.setCurrentLongitude(srcCoords[1]);
            response.setSpeedKmh(0.0);
            response.setHeadingDegrees(0.0);
            response.setLastUpdated(LocalDateTime.now());
            response.setBreadcrumbTrail(new ArrayList<>());
            double totalDist = calculateHaversineDistance(srcCoords[0], srcCoords[1], destCoords[0], destCoords[1]);
            response.setDistanceRemainingKm(Math.round(totalDist * 10.0) / 10.0);
            response.setEstimatedMinutesRemaining((int) Math.round((totalDist / 50.0) * 60));
            return response;
        }

        LocationPing latestPing = pings.get(pings.size() - 1);
        response.setCurrentLatitude(latestPing.getLatitude());
        response.setCurrentLongitude(latestPing.getLongitude());
        response.setSpeedKmh(latestPing.getSpeedKmh());
        response.setHeadingDegrees(latestPing.getHeadingDegrees());
        response.setLastUpdated(latestPing.getTimestamp());

        List<GpsPoint> trail = pings.stream()
                .map(p -> new GpsPoint(p.getLatitude(), p.getLongitude(), p.getTimestamp()))
                .collect(Collectors.toList());
        response.setBreadcrumbTrail(trail);

        double distRemaining = calculateHaversineDistance(latestPing.getLatitude(), latestPing.getLongitude(), destCoords[0], destCoords[1]);
        response.setDistanceRemainingKm(Math.round(distRemaining * 10.0) / 10.0);

        double currentSpeed = latestPing.getSpeedKmh() > 5.0 ? latestPing.getSpeedKmh() : 50.0;
        int minutesRemaining = (int) Math.round((distRemaining / currentSpeed) * 60);
        response.setEstimatedMinutesRemaining(Math.max(1, minutesRemaining));

        return response;
    }

    public RouteMapResponse getRouteMap(Long tripId) {
        if (tripId == null) {
            throw new IllegalArgumentException("Trip ID cannot be null");
        }

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found with ID: " + tripId));

        double[][] coords = resolvePreciseCoordinates(tripId, trip.getSource(), trip.getDestination());
        double[] srcCoords = coords[0];
        double[] destCoords = coords[1];

        RouteMapResponse response = new RouteMapResponse();
        response.setTripId(tripId);
        response.setSourceName(trip.getSource());
        response.setDestinationName(trip.getDestination());
        response.setSourceLatitude(srcCoords[0]);
        response.setSourceLongitude(srcCoords[1]);
        response.setDestinationLatitude(destCoords[0]);
        response.setDestinationLongitude(destCoords[1]);

        double distKm = calculateHaversineDistance(srcCoords[0], srcCoords[1], destCoords[0], destCoords[1]);
        response.setTotalRouteDistanceKm(Math.round(distKm * 10.0) / 10.0);

        // Generate 5 interpolated waypoints between source & destination
        List<GpsPoint> waypoints = new ArrayList<>();
        for (int i = 0; i <= 5; i++) {
            double ratio = i / 5.0;
            double lat = srcCoords[0] + (destCoords[0] - srcCoords[0]) * ratio;
            double lng = srcCoords[1] + (destCoords[1] - srcCoords[1]) * ratio;
            waypoints.add(new GpsPoint(lat, lng, LocalDateTime.now()));
        }
        response.setPolylineWaypoints(waypoints);

        return response;
    }

    public double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Earth radius in kilometers
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private double[] resolveCityCoordinates(String cityName) {
        if (cityName == null) return new double[]{12.9716, 77.5946};
        String key = cityName.trim().toLowerCase();
        for (Map.Entry<String, double[]> entry : CITY_COORDINATES.entrySet()) {
            if (key.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return new double[]{12.9716, 77.5946}; // Default Bengaluru
    }

    private double[][] resolvePreciseCoordinates(Long tripId, String source, String destination) {
        double[] src = null;
        double[] dest = null;

        // 1. Check Local Taxi
        try {
            java.util.Optional<com.example.project.blabla_porter.model.LocalTaxiBooking> taxiOpt = localTaxiBookingRepository.findByTripId(tripId);
            if (taxiOpt.isPresent()) {
                com.example.project.blabla_porter.model.LocalTaxiBooking taxi = taxiOpt.get();
                src = new double[]{taxi.getPickupLatitude(), taxi.getPickupLongitude()};
                dest = new double[]{taxi.getDropoffLatitude(), taxi.getDropoffLongitude()};
            }
        } catch (Exception e) {}

        // 2. Check Co-Ride
        if (dest == null) {
            try {
                List<com.example.project.blabla_porter.model.RideRequest> rides = rideRequestRepository.findByTripId(tripId);
                if (!rides.isEmpty()) {
                    com.example.project.blabla_porter.model.RideRequest ride = rides.get(0);
                    src = new double[]{ride.getPickupLatitude(), ride.getPickupLongitude()};
                    dest = new double[]{ride.getDropoffLatitude(), ride.getDropoffLongitude()};
                }
            } catch (Exception e) {}
        }

        // 3. Check Parcel
        if (dest == null) {
            try {
                List<com.example.project.blabla_porter.model.ParcelRequest> parcels = parcelRequestRepository.findByTripId(tripId);
                if (!parcels.isEmpty()) {
                    com.example.project.blabla_porter.model.ParcelRequest parcel = parcels.get(0);
                    src = new double[]{parcel.getPickupLatitude(), parcel.getPickupLongitude()};
                    dest = new double[]{parcel.getDropoffLatitude(), parcel.getDropoffLongitude()};
                }
            } catch (Exception e) {}
        }

        // Fallback to City Coordinates
        if (src == null) {
            src = resolveCityCoordinates(source);
        }
        if (dest == null) {
            dest = resolveCityCoordinates(destination);
        }

        return new double[][]{src, dest};
    }
}
