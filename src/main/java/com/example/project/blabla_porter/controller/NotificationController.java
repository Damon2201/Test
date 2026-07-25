package com.example.project.blabla_porter.controller;

import com.example.project.blabla_porter.dto.DeviceTokenRequest;
import com.example.project.blabla_porter.model.UserDeviceToken;
import com.example.project.blabla_porter.repository.UserDeviceTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NotificationController.class);

    @Autowired
    private UserDeviceTokenRepository userDeviceTokenRepository;

    @PostMapping("/devices")
    @Transactional
    public ResponseEntity<?> registerDeviceToken(@Valid @RequestBody DeviceTokenRequest request,
                                                 HttpServletRequest httpRequest) {
        Long authenticatedUserId = (Long) httpRequest.getAttribute("authenticatedUserId");
        if (authenticatedUserId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized access"));
        }

        String fcmToken = request.getFcmToken().trim();
        Optional<UserDeviceToken> existingTokenOpt = userDeviceTokenRepository.findByFcmToken(fcmToken);

        UserDeviceToken tokenEntity;
        if (existingTokenOpt.isPresent()) {
            tokenEntity = existingTokenOpt.get();
            tokenEntity.setUserId(authenticatedUserId);
            tokenEntity.setDeviceType(request.getDeviceType());
            tokenEntity.setLastActive(LocalDateTime.now());
        } else {
            tokenEntity = UserDeviceToken.builder()
                    .userId(authenticatedUserId)
                    .fcmToken(fcmToken)
                    .deviceType(request.getDeviceType())
                    .lastActive(LocalDateTime.now())
                    .build();
        }

        UserDeviceToken saved = userDeviceTokenRepository.save(tokenEntity);
        log.info("Registered FCM device token for User ID: {}", authenticatedUserId);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/devices")
    @Transactional
    public ResponseEntity<?> unregisterDeviceToken(@Valid @RequestBody DeviceTokenRequest request,
                                                   HttpServletRequest httpRequest) {
        Long authenticatedUserId = (Long) httpRequest.getAttribute("authenticatedUserId");
        if (authenticatedUserId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized access"));
        }

        String fcmToken = request.getFcmToken().trim();
        Optional<UserDeviceToken> existingTokenOpt = userDeviceTokenRepository.findByFcmToken(fcmToken);

        if (existingTokenOpt.isPresent()) {
            UserDeviceToken tokenEntity = existingTokenOpt.get();
            if (tokenEntity.getUserId().equals(authenticatedUserId)) {
                userDeviceTokenRepository.delete(tokenEntity);
                log.info("Unregistered FCM device token for User ID: {}", authenticatedUserId);
                return ResponseEntity.ok(Map.of("status", "success", "message", "Device token unregistered successfully"));
            } else {
                return ResponseEntity.status(403).body(Map.of("error", "Cannot unregister token owned by another user"));
            }
        }

        return ResponseEntity.ok(Map.of("status", "success", "message", "Token was not registered"));
    }
}
