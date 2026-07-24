package com.example.project.blabla_porter.service;

import com.example.project.blabla_porter.dto.TripCreateRequest;
import com.example.project.blabla_porter.model.Trip;
import com.example.project.blabla_porter.model.User;
import com.example.project.blabla_porter.repository.TripRepository;
import com.example.project.blabla_porter.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TripService {

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.example.project.blabla_porter.repository.ParcelRequestRepository parcelRequestRepository;

    public Trip createTrip(TripCreateRequest req) {
        User traveler = userRepository.findById(req.getTravelerId())
                .orElseThrow(() -> new RuntimeException("Traveler not found with id: " + req.getTravelerId()));

        if (traveler.getRole() != User.UserRole.TRAVELER) {
            throw new IllegalArgumentException("User must have TRAVELER role to declare a trip!");
        }

        if (traveler.getKycStatus() != User.KycStatus.APPROVED) {
            throw new IllegalStateException("Traveler KYC must be APPROVED before registering trips!");
        }

        Trip trip = Trip.builder()
                .travelerId(req.getTravelerId())
                .source(req.getSource())
                .destination(req.getDestination())
                .departureTime(req.getDepartureTime())
                .estimatedArrivalTime(req.getEstimatedArrivalTime())
                .availableCapacityKg(req.getAvailableCapacityKg())
                .availableSeats(req.getAvailableSeats())
                .status(Trip.TripStatus.PLANNED)
                .build();

        Trip savedTrip = tripRepository.save(trip);
        autoMatchPendingParcels(savedTrip);
        return savedTrip;
    }

    private void autoMatchPendingParcels(Trip trip) {
        List<com.example.project.blabla_porter.model.ParcelRequest> pending = parcelRequestRepository.findByStatus(com.example.project.blabla_porter.model.ParcelRequest.ParcelStatus.CREATED);
        for (com.example.project.blabla_porter.model.ParcelRequest pr : pending) {
            if (pr.getTripId() == null) {
                if (matchesLocation(trip.getSource(), pr.getPickupLocation()) &&
                    matchesLocation(trip.getDestination(), pr.getDropoffLocation())) {
                    double reqWeight = pr.getEstimatedWeightKg() != null ? pr.getEstimatedWeightKg() : 0.0;
                    if (trip.getAvailableCapacityKg() >= reqWeight) {
                        if (trip.getDepartureTime().isAfter(java.time.LocalDateTime.now())) {
                            pr.setTripId(trip.getId());
                            parcelRequestRepository.save(pr);
                        }
                    }
                }
            }
        }
    }

    private boolean matchesLocation(String tripLoc, String reqLoc) {
        if (tripLoc == null || reqLoc == null) return false;
        String tl = tripLoc.toLowerCase().trim();
        String rl = reqLoc.toLowerCase().trim();
        return tl.contains(rl) || rl.contains(tl);
    }

    public List<Trip> searchTrips(String source, String destination) {
        if (source == null) source = "";
        if (destination == null) destination = "";
        return tripRepository.findBySourceContainingIgnoreCaseAndDestinationContainingIgnoreCaseAndStatus(
                source, destination, Trip.TripStatus.PLANNED);
    }

    public Trip getById(Long id) {
        return tripRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Trip not found with id: " + id));
    }

    public List<Trip> getTripsByTraveler(Long travelerId) {
        return tripRepository.findByTravelerId(travelerId);
    }

    public List<Trip> getAllPlannedTrips() {
        return tripRepository.findByStatus(Trip.TripStatus.PLANNED);
    }
}
