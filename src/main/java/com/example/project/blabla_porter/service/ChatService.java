package com.example.project.blabla_porter.service;

import com.example.project.blabla_porter.dto.ChatMessageRequest;
import com.example.project.blabla_porter.model.ChatMessage;
import com.example.project.blabla_porter.model.ParcelRequest;
import com.example.project.blabla_porter.model.Trip;
import com.example.project.blabla_porter.repository.ChatMessageRepository;
import com.example.project.blabla_porter.repository.ParcelRequestRepository;
import com.example.project.blabla_porter.repository.TripRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ChatService {

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ParcelRequestRepository parcelRequestRepository;

    @Autowired
    private TripRepository tripRepository;

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

        return chatMessageRepository.save(message);
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
