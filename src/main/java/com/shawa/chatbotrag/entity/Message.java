package com.shawa.chatbotrag.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonBackReference;

import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    private LocalDateTime createdAt;

    @Column(columnDefinition = "TEXT")
    private String thought; // hanya untuk "assistant", bisa null

    @ManyToOne
    @JoinColumn(name = "thread_id")
    @JsonBackReference
    private Thread thread;
}


