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

    @Transactional
    public Rating submitRating(RatingSubmitRequest req) {
        userRepository.findById(req.getRaterUserId())
                .orElseThrow(() -> new RuntimeException("Rater user not found with id: " + req.getRaterUserId()));

        User ratee = userRepository.findById(req.getRateeUserId())
                .orElseThrow(() -> new RuntimeException("Ratee user not found with id: " + req.getRateeUserId()));

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
