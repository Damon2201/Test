package com.example.project.blabla_porter.controller;

import com.example.project.blabla_porter.config.RequireRole;
import com.example.project.blabla_porter.dto.KycSubmitRequest;
import com.example.project.blabla_porter.model.User;
import com.example.project.blabla_porter.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/kyc")
public class KycController {

    @Autowired
    private UserService userService;

    @PostMapping("/submit")
    @RequireRole({User.UserRole.TRAVELER, User.UserRole.SENDER, User.UserRole.RIDER})
    public User submitKyc(@Valid @RequestBody KycSubmitRequest request) {
        return userService.submitKyc(request);
    }

    @GetMapping("/admin/pending")
    @RequireRole(User.UserRole.ADMIN)
    public List<User> getPendingKycUsers() {
        return userService.getPendingKycUsers();
    }

    @PostMapping("/admin/{userId}/approve")
    @RequireRole(User.UserRole.ADMIN)
    public User approveKyc(@PathVariable Long userId, jakarta.servlet.http.HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("authenticatedUserId");
        return userService.approveKyc(userId, adminId);
    }

    @PutMapping("/admin/{userId}/review")
    @RequireRole(User.UserRole.ADMIN)
    public User reviewKyc(@PathVariable Long userId, @RequestParam boolean approve, jakarta.servlet.http.HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("authenticatedUserId");
        return userService.reviewKyc(userId, approve, adminId);
    }
}
