package com.example.project.blabla_porter.controller;

import com.example.project.blabla_porter.dto.ChatMessageRequest;
import com.example.project.blabla_porter.model.ChatMessage;
import com.example.project.blabla_porter.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @PostMapping("/{parcelRequestId}/send")
    public ChatMessage sendMessage(@PathVariable Long parcelRequestId,
                                  @Valid @RequestBody ChatMessageRequest request) {
        return chatService.sendMessage(parcelRequestId, request);
    }

    @GetMapping("/{parcelRequestId}")
    public List<ChatMessage> getMessages(@PathVariable Long parcelRequestId,
                                         @RequestParam Long userId) {
        return chatService.getMessages(parcelRequestId, userId);
    }
}
