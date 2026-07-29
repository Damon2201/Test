package com.example.project.blabla_porter.controller;

import com.example.project.blabla_porter.config.RequireRole;
import com.example.project.blabla_porter.dto.DisputeCreateRequest;
import com.example.project.blabla_porter.dto.RatingSubmitRequest;
import com.example.project.blabla_porter.dto.PendingRatingInfo;
import com.example.project.blabla_porter.model.Dispute;
import com.example.project.blabla_porter.model.Rating;
import com.example.project.blabla_porter.model.User;
import com.example.project.blabla_porter.service.TrustAndDisputeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/governance")
public class TrustAndDisputeController {

    @Autowired
    private TrustAndDisputeService trustAndDisputeService;

    @PostMapping("/ratings")
    // No @RequireRole — any authenticated user can submit a rating
    public Rating submitRating(@Valid @RequestBody RatingSubmitRequest request) {
        return trustAndDisputeService.submitRating(request);
    }

    @GetMapping("/ratings/user/{userId}")
    // No @RequireRole — read-only
    public List<Rating> getUserRatings(@PathVariable Long userId) {
        return trustAndDisputeService.getUserRatings(userId);
    }

    @GetMapping("/ratings/by-rater/{raterId}")
    // No @RequireRole — read-only
    public List<Rating> getRatingsSubmittedBy(@PathVariable Long raterId) {
        return trustAndDisputeService.getRatingsSubmittedBy(raterId);
    }

    @GetMapping("/ratings/unrated-completed-trips/{userId}")
    // No @RequireRole — read-only
    public List<PendingRatingInfo> getPendingRatings(@PathVariable Long userId) {
        return trustAndDisputeService.getPendingRatings(userId);
    }

    @PostMapping("/disputes")
    // No @RequireRole — any authenticated user can file a dispute
    public Dispute createDispute(@Valid @RequestBody DisputeCreateRequest request) {
        return trustAndDisputeService.createDispute(request);
    }

    @PutMapping("/disputes/{id}/resolve")
    @RequireRole(User.UserRole.ADMIN)
    public Dispute resolveDispute(@PathVariable Long id,
                                   @RequestParam Dispute.DisputeStatus resolutionStatus,
                                   @RequestParam(required = false) String adminNotes,
                                   jakarta.servlet.http.HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("authenticatedUserId");
        return trustAndDisputeService.resolveDispute(id, resolutionStatus, adminNotes, adminId);
    }

    @GetMapping("/disputes/{id}")
    // No @RequireRole — any authenticated user can view their dispute
    public Dispute getDisputeById(@PathVariable Long id) {
        return trustAndDisputeService.getDisputeById(id);
    }

    @GetMapping("/disputes/status/{status}")
    @RequireRole(User.UserRole.ADMIN)
    public List<Dispute> getDisputesByStatus(@PathVariable Dispute.DisputeStatus status) {
        return trustAndDisputeService.getDisputesByStatus(status);
    }
}
