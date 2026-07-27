package com.example.project.blabla_porter.service;

import com.example.project.blabla_porter.dto.DisputeCreateRequest;
import com.example.project.blabla_porter.dto.RatingSubmitRequest;
import com.example.project.blabla_porter.model.*;
import com.example.project.blabla_porter.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TrustAndDisputeService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TrustAndDisputeService.class);

    @Autowired
    private RatingRepository ratingRepository;

    @Autowired
    private DisputeRepository disputeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ParcelRequestRepository parcelRequestRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private RideRequestRepository rideRequestRepository;

    @Transactional
    public Rating submitRating(RatingSubmitRequest req) {
        userRepository.findById(req.getRaterUserId())
                .orElseThrow(() -> new RuntimeException("Rater user not found with id: " + req.getRaterUserId()));

        userRepository.findById(req.getRateeUserId())
                .orElseThrow(() -> new RuntimeException("Ratee user not found with id: " + req.getRateeUserId()));

        if (req.getParcelRequestId() != null) {
            if (ratingRepository.findByRaterUserIdAndParcelRequestId(req.getRaterUserId(), req.getParcelRequestId()).isPresent()) {
                throw new IllegalStateException("You have already submitted a rating for this parcel request!");
            }
        }
        if (req.getRideRequestId() != null) {
            if (ratingRepository.findByRaterUserIdAndRideRequestId(req.getRaterUserId(), req.getRideRequestId()).isPresent()) {
                throw new IllegalStateException("You have already submitted a rating for this ride request!");
            }
        }

        // Verification of completed transaction and counterparty validation
        if (req.getParcelRequestId() != null) {
            ParcelRequest parcel = parcelRequestRepository.findById(req.getParcelRequestId())
                    .orElseThrow(() -> new IllegalArgumentException("Parcel request not found with id: " + req.getParcelRequestId()));

            if (parcel.getStatus() != ParcelRequest.ParcelStatus.DELIVERED) {
                throw new IllegalStateException("Cannot rate for a parcel request that is not DELIVERED! Current status: " + parcel.getStatus());
            }

            Trip trip = tripRepository.findById(parcel.getTripId())
                    .orElseThrow(() -> new IllegalArgumentException("Trip not found for parcel request"));

            boolean isValidCounterparties = (req.getRaterUserId().equals(parcel.getSenderId()) && req.getRateeUserId().equals(trip.getTravelerId()))
                    || (req.getRaterUserId().equals(trip.getTravelerId()) && req.getRateeUserId().equals(parcel.getSenderId()));

            if (!isValidCounterparties) {
                throw new IllegalArgumentException("Rater and ratee must be the sender and traveler of the completed parcel request!");
            }

        } else if (req.getRideRequestId() != null) {
            RideRequest ride = rideRequestRepository.findById(req.getRideRequestId())
                    .orElseThrow(() -> new IllegalArgumentException("Ride request not found with id: " + req.getRideRequestId()));

            if (ride.getStatus() != RideRequest.RideStatus.COMPLETED) {
                throw new IllegalStateException("Cannot rate for a ride request that is not COMPLETED! Current status: " + ride.getStatus());
            }

            Trip trip = tripRepository.findById(ride.getTripId())
                    .orElseThrow(() -> new IllegalArgumentException("Trip not found for ride request"));

            boolean isValidCounterparties = (req.getRaterUserId().equals(ride.getRiderId()) && req.getRateeUserId().equals(trip.getTravelerId()))
                    || (req.getRaterUserId().equals(trip.getTravelerId()) && req.getRateeUserId().equals(ride.getRiderId()));

            if (!isValidCounterparties) {
                throw new IllegalArgumentException("Rater and ratee must be the rider and traveler of the completed ride request!");
            }
        } else {
            throw new IllegalArgumentException("Rating must be linked to either a parcel request or a ride request!");
        }

        User ratee = userRepository.findById(req.getRateeUserId()).orElseThrow();

        Rating rating = Rating.builder()
                .raterUserId(req.getRaterUserId())
                .rateeUserId(req.getRateeUserId())
                .parcelRequestId(req.getParcelRequestId())
                .rideRequestId(req.getRideRequestId())
                .score(req.getScore())
                .reviewText(req.getReviewText())
                .build();

        Rating saved = ratingRepository.save(rating);

        // Recalculate average rating for ratee
        int currentCount = (ratee.getTotalRatingsCount() != null) ? ratee.getTotalRatingsCount() : 0;
        double currentAvg = (ratee.getAverageRating() != null) ? ratee.getAverageRating() : 5.0;

        double newAvg = ((currentAvg * currentCount) + req.getScore()) / (currentCount + 1);
        ratee.setAverageRating(Math.round(newAvg * 100.0) / 100.0);
        ratee.setTotalRatingsCount(currentCount + 1);
        userRepository.save(ratee);

        return saved;
    }

    public Dispute createDispute(DisputeCreateRequest req) {
        userRepository.findById(req.getReporterUserId())
                .orElseThrow(() -> new RuntimeException("Reporter user not found with id: " + req.getReporterUserId()));

        if (req.getParcelRequestId() == null && req.getRideRequestId() == null) {
            throw new IllegalArgumentException("Dispute must be associated with either a parcel request or a ride request!");
        }

        Dispute dispute = Dispute.builder()
                .reporterUserId(req.getReporterUserId())
                .parcelRequestId(req.getParcelRequestId())
                .rideRequestId(req.getRideRequestId())
                .disputeReason(req.getDisputeReason())
                .evidencePhotoUrl(req.getEvidencePhotoUrl())
                .status(Dispute.DisputeStatus.OPEN)
                .build();

        return disputeRepository.save(dispute);
    }

    @Transactional
    public Dispute resolveDispute(Long disputeId, Dispute.DisputeStatus resolutionStatus, String adminNotes) {
        return resolveDispute(disputeId, resolutionStatus, adminNotes, null);
    }

    @Transactional
    public Dispute resolveDispute(Long disputeId, Dispute.DisputeStatus resolutionStatus, String adminNotes, Long adminId) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new RuntimeException("Dispute not found with id: " + disputeId));

        if (dispute.getStatus() == Dispute.DisputeStatus.RESOLVED_REFUND_SENDER ||
            dispute.getStatus() == Dispute.DisputeStatus.RESOLVED_RELEASE_TRAVELER ||
            dispute.getStatus() == Dispute.DisputeStatus.REJECTED) {
            throw new IllegalStateException("Dispute is already resolved or closed!");
        }

        dispute.setStatus(resolutionStatus);
        dispute.setAdminNotes(adminNotes);
        dispute.setResolvedAt(LocalDateTime.now());

        log.info("AUDIT LOG: Admin [ID: {}] resolved Dispute [ID: {}] with status: '{}'. Notes: '{}'. Timestamp: {}",
                adminId != null ? adminId : "SYSTEM", disputeId, resolutionStatus, adminNotes, java.time.LocalDateTime.now());

        // Perform financial arbitration if linked to a parcel request
        if (dispute.getParcelRequestId() != null) {
            Long parcelId = dispute.getParcelRequestId();
            paymentRepository.findByParcelRequestId(parcelId).ifPresent(payment -> {
                ParcelRequest parcel = parcelRequestRepository.findById(parcelId).orElse(null);
                if (resolutionStatus == Dispute.DisputeStatus.RESOLVED_REFUND_SENDER) {
                    payment.setStatus(Payment.EscrowStatus.REFUNDED);
                    if (parcel != null) parcel.setStatus(ParcelRequest.ParcelStatus.CANCELLED);
                } else if (resolutionStatus == Dispute.DisputeStatus.RESOLVED_RELEASE_TRAVELER) {
                    payment.setStatus(Payment.EscrowStatus.RELEASED);
                    if (parcel != null) parcel.setStatus(ParcelRequest.ParcelStatus.DELIVERED);
                }
                paymentRepository.save(payment);
                if (parcel != null) parcelRequestRepository.save(parcel);
            });
        }

        return disputeRepository.save(dispute);
    }

    public List<Rating> getUserRatings(Long rateeUserId) {
        return ratingRepository.findByRateeUserId(rateeUserId);
    }

    public List<Dispute> getDisputesByStatus(Dispute.DisputeStatus status) {
        return disputeRepository.findByStatus(status);
    }

    public Dispute getDisputeById(Long id) {
        return disputeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Dispute not found with id: " + id));
    }
}
