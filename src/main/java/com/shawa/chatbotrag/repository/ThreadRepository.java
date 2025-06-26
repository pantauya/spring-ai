package com.shawa.chatbotrag.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.shawa.chatbotrag.entity.Thread;

import io.micrometer.common.lang.NonNull;

import java.util.UUID;
import java.util.List;

public interface ThreadRepository extends JpaRepository<Thread, UUID> {
    @Override
    @NonNull
    @EntityGraph(attributePaths = "messages")
    List<Thread> findAll();

    List<Thread> findAllByOrderByUpdatedAtDesc();

}

