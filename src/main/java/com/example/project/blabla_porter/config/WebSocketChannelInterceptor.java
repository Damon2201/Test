package com.example.project.blabla_porter.config;

import com.example.project.blabla_porter.service.JwtService;
import com.example.project.blabla_porter.service.TrackingService;
import com.example.project.blabla_porter.service.ParcelService;
import com.example.project.blabla_porter.model.ParcelRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WebSocketChannelInterceptor implements ChannelInterceptor {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WebSocketChannelInterceptor.class);

    @Autowired
    private JwtService jwtService;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private TrackingService trackingService;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private ParcelService parcelService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && accessor.getCommand() != null) {
            StompCommand command = accessor.getCommand();

            if (StompCommand.CONNECT.equals(command)) {
                handleConnect(accessor);
            } else if (StompCommand.SUBSCRIBE.equals(command)) {
                handleSubscribe(accessor);
            }
        }

        return message;
    }

    private void handleConnect(StompHeaderAccessor accessor) {
        String token = accessor.getFirstNativeHeader("Authorization");
        if (token == null) {
            token = accessor.getFirstNativeHeader("token");
        }
        if (token == null) {
            // Also check query parameter fallback from CONNECT headers if available
            token = accessor.getFirstNativeHeader("passcode"); // some stomp clients map tokens to passcode
        }

        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        if (token == null || token.isBlank()) {
            log.warn("Security Alert: WebSocket connection attempt without JWT token");
            throw new IllegalArgumentException("Authentication token is required for WebSocket connection");
        }

        try {
            Long userId = jwtService.extractUserId(token);
            accessor.getSessionAttributes().put("userId", userId);
            log.info("WebSocket connected successfully for User ID: {}", userId);
        } catch (Exception e) {
            log.warn("Security Alert: WebSocket connection failed due to invalid JWT: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid authentication token: " + e.getMessage());
        }
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null || !sessionAttributes.containsKey("userId")) {
            log.warn("Security Alert: Unauthorized subscription attempt on connection without user session");
            throw new IllegalArgumentException("Access denied: Unauthenticated session");
        }

        Long userId = (Long) sessionAttributes.get("userId");
        String destination = accessor.getDestination();

        if (destination == null || destination.isBlank()) {
            return;
        }

        try {
            if (destination.startsWith("/topic/tracking/")) {
                // Topic format: /topic/tracking/{tripId}
                String tripIdStr = destination.substring("/topic/tracking/".length());
                Long tripId = Long.parseLong(tripIdStr);
                trackingService.assertUserHasAccessToTrip(userId, tripId);
                log.info("User {} successfully subscribed to tracking for trip {}", userId, tripId);

            } else if (destination.startsWith("/topic/chat/")) {
                // Topic format: /topic/chat/{parcelRequestId}
                String parcelRequestIdStr = destination.substring("/topic/chat/".length());
                Long parcelRequestId = Long.parseLong(parcelRequestIdStr);
                
                ParcelRequest parcel = parcelService.getById(parcelRequestId);
                if (parcel == null) {
                    throw new IllegalArgumentException("Parcel request not found with ID: " + parcelRequestId);
                }
                
                Long tripId = parcel.getTripId();
                if (tripId == null) {
                    // Chat is only allowed if parcel is matched/requested on a trip
                    throw new IllegalArgumentException("Parcel request is not associated with any trip");
                }
                
                trackingService.assertUserHasAccessToTrip(userId, tripId);
                log.info("User {} successfully subscribed to chat for parcel {} on trip {}", userId, parcelRequestId, tripId);

            } else if (destination.startsWith("/topic/trip/")) {
                // Topic format: /topic/trip/{tripId}
                String tripIdStr = destination.substring("/topic/trip/".length());
                Long tripId = Long.parseLong(tripIdStr);
                trackingService.assertUserHasAccessToTrip(userId, tripId);
                log.info("User {} successfully subscribed to updates for trip {}", userId, tripId);
            }
        } catch (IllegalArgumentException e) {
            log.warn("Security Alert: User {} subscription to '{}' was denied: {}", userId, destination, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error processing subscription for User {} on destination '{}': {}", userId, destination, e.getMessage());
            throw new IllegalArgumentException("Error verifying subscription: " + e.getMessage());
        }
    }
}
