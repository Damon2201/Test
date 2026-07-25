package com.example.project.blabla_porter.service;

import java.util.Map;

public interface NotificationService {
    /**
     * Sends a push notification to a specific user on all their registered devices.
     */
    void sendPushToUser(Long userId, String title, String body, Map<String, String> data);

    /**
     * Sends a push notification to a specific device token.
     */
    void sendPushToToken(String token, String title, String body, Map<String, String> data);
}
