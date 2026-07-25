package com.example.project.blabla_porter.service;

import com.example.project.blabla_porter.dto.ChatMessageRequest;
import com.example.project.blabla_porter.model.ChatMessage;
import com.example.project.blabla_porter.model.ParcelRequest;
import com.example.project.blabla_porter.model.Trip;
import com.example.project.blabla_porter.model.User;
import com.example.project.blabla_porter.repository.ChatMessageRepository;
import com.example.project.blabla_porter.repository.ParcelRequestRepository;
import com.example.project.blabla_porter.repository.TripRepository;
import com.example.project.blabla_porter.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ChatService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChatService.class);

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ParcelRequestRepository parcelRequestRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private NotificationService notificationService;

    @Transactional
    public ChatMessage sendMessage(Long parcelRequestId, ChatMessageRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("Request body cannot be null");
        }
        if (req.getSenderUserId() == null) {
            throw new IllegalArgumentException("Sender User ID is required");
        }
        if (req.getMessage() == null || req.getMessage().trim().isEmpty()) {
            throw new IllegalArgumentException("Message content cannot be empty");
        }

        ParcelRequest pr = parcelRequestRepository.findById(parcelRequestId)
                .orElseThrow(() -> new RuntimeException("Parcel request not found with id: " + parcelRequestId));

        if (pr.getTripId() == null) {
            throw new IllegalStateException("Cannot send chat messages on an unassigned parcel request!");
        }

        if (pr.getStatus() == ParcelRequest.ParcelStatus.CREATED) {
            throw new IllegalStateException("Cannot send chat messages before parcel request acceptance!");
        }

        Trip trip = tripRepository.findById(pr.getTripId())
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        Long recipientUserId;
        Long senderUserId = req.getSenderUserId();
        if (senderUserId.equals(pr.getSenderId())) {
            recipientUserId = trip.getTravelerId();
        } else if (senderUserId.equals(trip.getTravelerId())) {
            recipientUserId = pr.getSenderId();
        } else {
            throw new IllegalArgumentException("User is not authorized to participate in this chat!");
        }

        ChatMessage message = ChatMessage.builder()
                .parcelRequestId(parcelRequestId)
                .senderUserId(senderUserId)
                .recipientUserId(recipientUserId)
                .message(req.getMessage())
                .sentAt(LocalDateTime.now())
                .build();

        ChatMessage savedMessage = chatMessageRepository.save(message);

        // 1. Broadcast the chat message in real time to the WebSocket topic
        try {
            messagingTemplate.convertAndSend("/topic/chat/" + parcelRequestId, savedMessage);
            log.info("Successfully broadcasted chat message to WebSocket topic /topic/chat/{}", parcelRequestId);
        } catch (Exception e) {
            log.warn("Failed to broadcast WebSocket chat message: {}", e.getMessage());
        }

        // 2. Dispatch a background push notification via FCM
        try {
            User sender = userRepository.findById(senderUserId).orElse(null);
            String senderName = (sender != null) ? sender.getFullName() : "A user";
            
            notificationService.sendPushToUser(
                    recipientUserId,
                    "New Chat Message",
                    senderName + ": " + req.getMessage(),
                    Map.of(
                            "type", "CHAT",
                            "parcelRequestId", String.valueOf(parcelRequestId),
                            "senderId", String.valueOf(senderUserId)
                    )
            );
        } catch (Exception e) {
            log.warn("Failed to trigger FCM push notification for chat message: {}", e.getMessage());
        }

        return savedMessage;
    }

    public List<ChatMessage> getChatHistory(Long parcelRequestId) {
        ParcelRequest pr = parcelRequestRepository.findById(parcelRequestId)
                .orElseThrow(() -> new RuntimeException("Parcel request not found with id: " + parcelRequestId));
        return chatMessageRepository.findByParcelRequestIdOrderBySentAtAsc(parcelRequestId);
    }

    public List<ChatMessage> getMessages(Long parcelRequestId, Long userId) {
        ParcelRequest pr = parcelRequestRepository.findById(parcelRequestId)
                .orElseThrow(() -> new RuntimeException("Parcel request not found with id: " + parcelRequestId));

        if (pr.getTripId() == null) {
            throw new IllegalStateException("Cannot retrieve chat messages on an unassigned parcel request!");
        }

        Trip trip = tripRepository.findById(pr.getTripId())
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        if (!userId.equals(pr.getSenderId()) && !userId.equals(trip.getTravelerId())) {
            throw new IllegalArgumentException("User is not authorized to view this chat!");
        }

        return chatMessageRepository.findByParcelRequestIdOrderBySentAtAsc(parcelRequestId);
    }
}
