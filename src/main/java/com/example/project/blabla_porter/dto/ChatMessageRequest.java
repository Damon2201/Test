package com.example.project.blabla_porter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChatMessageRequest {
    @NotNull(message = "Sender User ID is required")
    private Long senderUserId;

    @NotBlank(message = "Message text is required")
    private String message;
}
