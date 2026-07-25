package com.example.project.blabla_porter.controller;

import com.example.project.blabla_porter.dto.ChatMessageRequest;
import com.example.project.blabla_porter.dto.TrackingDto.LocationPingRequest;
import com.example.project.blabla_porter.model.ChatMessage;
import com.example.project.blabla_porter.model.LocationPing;
import com.example.project.blabla_porter.service.ChatService;
import com.example.project.blabla_porter.service.TrackingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
public class WebSocketBroadcastController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WebSocketBroadcastController.class);

    @Autowired
    private ChatService chatService;

    @Autowired
    private TrackingService trackingService;

    /**
     * Inbound WebSocket STOMP endpoint to send chat messages.
     * Maps to client destination: /app/chat/send/{parcelRequestId}
     */
    @MessageMapping("/chat/send/{parcelRequestId}")
    public void handleInboundChat(@DestinationVariable Long parcelRequestId,
                                  ChatMessageRequest request,
                                  SimpMessageHeaderAccessor headerAccessor) {
        Long authenticatedUserId = (Long) headerAccessor.getSessionAttributes().get("userId");
        if (authenticatedUserId == null) {
            throw new IllegalArgumentException("Unauthorized WebSocket message");
        }
        
        // Enforce matching senderUserId with the authenticated session userId
        request.setSenderUserId(authenticatedUserId);
        
        chatService.sendMessage(parcelRequestId, request);
    }

    /**
     * Inbound WebSocket STOMP endpoint to send GPS location telemetry updates.
     * Maps to client destination: /app/ping
     */
    @MessageMapping("/ping")
    public void handleInboundLocationPing(LocationPingRequest request,
                                          SimpMessageHeaderAccessor headerAccessor) {
        Long authenticatedUserId = (Long) headerAccessor.getSessionAttributes().get("userId");
        if (authenticatedUserId == null) {
            throw new IllegalArgumentException("Unauthorized WebSocket message");
        }
        
        // Enforce matching travelerId with the authenticated session userId
        request.setTravelerId(authenticatedUserId);
        
        trackingService.recordLocationPing(request, authenticatedUserId);
    }
}
