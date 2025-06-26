package com.shawa.chatbotrag.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shawa.chatbotrag.entity.Message;

import java.util.List;
import java.util.UUID;
public interface MessageRepository extends JpaRepository<Message, UUID> {
    List<Message> findByThreadIdOrderByCreatedAtAsc(UUID threadId);
}