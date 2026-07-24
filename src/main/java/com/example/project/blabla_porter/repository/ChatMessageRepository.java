package com.example.project.blabla_porter.repository;

import com.example.project.blabla_porter.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByParcelRequestIdOrderBySentAtAsc(Long parcelRequestId);
}
