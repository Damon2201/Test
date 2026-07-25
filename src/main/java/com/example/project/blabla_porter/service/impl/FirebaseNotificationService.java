package com.example.project.blabla_porter.service.impl;

import com.example.project.blabla_porter.model.UserDeviceToken;
import com.example.project.blabla_porter.repository.UserDeviceTokenRepository;
import com.example.project.blabla_porter.service.NotificationService;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class FirebaseNotificationService implements NotificationService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FirebaseNotificationService.class);

    @Autowired
    private UserDeviceTokenRepository userDeviceTokenRepository;

    @Value("${blabla.firebase.config-path:}")
    private String firebaseConfigPath;

    private boolean firebaseInitialized = false;

    @PostConstruct
    public void initializeFirebase() {
        try {
            InputStream serviceAccount = null;

            if (firebaseConfigPath != null && !firebaseConfigPath.isBlank()) {
                serviceAccount = new FileInputStream(firebaseConfigPath);
            } else {
                // Try classpath fallback
                serviceAccount = getClass().getClassLoader().getResourceAsStream("firebase-service-account.json");
            }

            if (serviceAccount != null) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();

                if (FirebaseApp.getApps().isEmpty()) {
                    FirebaseApp.initializeApp(options);
                }
                firebaseInitialized = true;
                log.info("Firebase Admin SDK successfully initialized for Push Notifications!");
            } else {
                log.warn("Firebase config file not found. Falling back to MOCK push notification mode.");
            }
        } catch (Exception e) {
            log.warn("Failed to initialize Firebase Admin SDK ({}). Falling back to MOCK push notification mode.", e.getMessage());
        }
    }

    @Override
    public void sendPushToUser(Long userId, String title, String body, Map<String, String> data) {
        List<UserDeviceToken> tokens = userDeviceTokenRepository.findByUserId(userId);
        if (tokens.isEmpty()) {
            log.info("[MOCK PUSH] User {} has no registered device tokens. Log alert only. Title: \"{}\", Body: \"{}\", Data: {}",
                    userId, title, body, data);
            return;
        }

        log.info("Sending push notification to User {} ({} registered devices)", userId, tokens.size());
        for (UserDeviceToken token : tokens) {
            sendPushToToken(token.getFcmToken(), title, body, data);
            // Update token last active time on access
            token.setLastActive(LocalDateTime.now());
            userDeviceTokenRepository.save(token);
        }
    }

    @Override
    public void sendPushToToken(String token, String title, String body, Map<String, String> data) {
        if (firebaseInitialized) {
            try {
                Message message = Message.builder()
                        .setToken(token)
                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build())
                        .putAllData(data != null ? data : Map.of())
                        .build();

                String response = FirebaseMessaging.getInstance().send(message);
                log.debug("Successfully sent FCM message: {}", response);
            } catch (Exception e) {
                log.error("Failed to send Firebase push notification to token: {}. Error: {}", token, e.getMessage());
                // If token is invalid or inactive, prune it from db
                userDeviceTokenRepository.findByFcmToken(token).ifPresent(t -> {
                    userDeviceTokenRepository.delete(t);
                    log.info("Pruned invalid/expired FCM device token: {}", token);
                });
            }
        } else {
            log.info("[MOCK PUSH] to Token {}: \"{}\" - \"{}\" | Data: {}", token, title, body, data);
        }
    }
}
