package com.example.project.blabla_porter.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long parcelRequestId;
    private Long senderUserId;
    private Long recipientUserId;

    @Column(length = 1000)
    private String message;

    private LocalDateTime sentAt;
}
