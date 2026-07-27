package com.example.project.blabla_porter.service;

import com.example.project.blabla_porter.dto.TrackingDto.*;
import com.example.project.blabla_porter.model.LocationPing;
import com.example.project.blabla_porter.model.Trip;
import com.example.project.blabla_porter.repository.LocationPingRepository;
import com.example.project.blabla_porter.repository.TripRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TrackingService.class);

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

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private OsrmRoutingService osrmRoutingService;

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

    /**
     * Verifies that the authenticated user has a legitimate relationship to this trip
     * (traveler, rider with booking/ride, or sender with parcel on this trip).
     * Throws IllegalArgumentException with a 403-appropriate message if access is denied.
     */
    public void assertUserHasAccessToTrip(Long authenticatedUserId, Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found with ID: " + tripId));

        // Check 1: Is the user the traveler/captain on this trip?
        if (trip.getTravelerId().equals(authenticatedUserId)) {
            return;
        }

        // Check 2: Is the user a rider with a RideRequest on this trip?
        boolean isRider = rideRequestRepository.findByTripId(tripId).stream()
                .anyMatch(r -> r.getRiderId().equals(authenticatedUserId));
        if (isRider) {
            return;
        }

        // Check 3: Is the user a sender with a ParcelRequest on this trip?
        boolean isSender = parcelRequestRepository.findByTripId(tripId).stream()
                .anyMatch(p -> p.getSenderId().equals(authenticatedUserId));
        if (isSender) {
            return;
        }

        // Check 4: Is the user a rider with a LocalTaxiBooking linked to this trip?
        try {
            java.util.Optional<com.example.project.blabla_porter.model.LocalTaxiBooking> taxiOpt =
                    localTaxiBookingRepository.findByTripId(tripId);
            if (taxiOpt.isPresent() && taxiOpt.get().getRiderId().equals(authenticatedUserId)) {
                return;
            }
        } catch (Exception e) {
            // Repository method may not exist yet — fail safe
        }

        // No relationship found — deny access
        log.warn("Security Alert: User {} attempted to access tracking data for trip {} without authorization",
                authenticatedUserId, tripId);
        throw new IllegalArgumentException(
                "Access denied: You are not authorized to view tracking data for trip " + tripId);
    }

    @Transactional
    public LocationPing recordLocationPing(LocationPingRequest request) {
        return recordLocationPing(request, null);
    }

    @Transactional
    public LocationPing recordLocationPing(LocationPingRequest request, Long authenticatedUserId) {
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

        // Verify the authenticated user is the traveler they claim to be (prevent impersonation)
        if (authenticatedUserId != null && !authenticatedUserId.equals(request.getTravelerId())) {
            log.warn("Security Alert: User {} attempted to impersonate traveler {} on trip {}",
                    authenticatedUserId, request.getTravelerId(), request.getTripId());
            throw new IllegalArgumentException(
                    "Access denied: You can only send location pings as yourself, not as traveler " + request.getTravelerId());
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

        LocationPing savedPing = locationPingRepository.save(ping);

        // Broadcast GPS coordinate updates in real time to subscribers of this trip
        try {
            LiveTrackingResponse liveResponse = getLiveTracking(trip.getId(), null);
            messagingTemplate.convertAndSend("/topic/tracking/" + trip.getId(), liveResponse);
        } catch (Exception e) {
            log.warn("Failed to broadcast real-time live tracking update: {}", e.getMessage());
        }

        return savedPing;
    }

    public LiveTrackingResponse getLiveTracking(Long tripId) {
        return getLiveTracking(tripId, null);
    }

    public LiveTrackingResponse getLiveTracking(Long tripId, Long authenticatedUserId) {
        if (tripId == null) {
            throw new IllegalArgumentException("Trip ID cannot be null");
        }

        // Enforce trip-ownership authorization
        if (authenticatedUserId != null) {
            assertUserHasAccessToTrip(authenticatedUserId, tripId);
        }

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found with ID: " + tripId));

        // Block access to completed/cancelled trip location data
        if (trip.getStatus() == Trip.TripStatus.COMPLETED || trip.getStatus() == Trip.TripStatus.CANCELLED) {
            LiveTrackingResponse emptyResponse = new LiveTrackingResponse();
            emptyResponse.setTripId(tripId);
            emptyResponse.setTripStatus(trip.getStatus().name());
            emptyResponse.setTotalPingsCount(0);
            emptyResponse.setBreadcrumbTrail(new ArrayList<>());
            emptyResponse.setDistanceRemainingKm(0.0);
            emptyResponse.setEstimatedMinutesRemaining(0);
            emptyResponse.setLastUpdated(LocalDateTime.now());
            return emptyResponse;
        }

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
        return getRouteMap(tripId, null);
    }

    public RouteMapResponse getRouteMap(Long tripId, Long authenticatedUserId) {
        if (tripId == null) {
            throw new IllegalArgumentException("Trip ID cannot be null");
        }

        // Enforce trip-ownership authorization
        if (authenticatedUserId != null) {
            assertUserHasAccessToTrip(authenticatedUserId, tripId);
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

        OsrmRoutingService.RouteDetails details = osrmRoutingService.getRouteDetails(srcCoords[0], srcCoords[1], destCoords[0], destCoords[1]);
        response.setTotalRouteDistanceKm(Math.round(details.getDistanceKm() * 10.0) / 10.0);
        response.setPolylineWaypoints(details.getWaypoints());

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
