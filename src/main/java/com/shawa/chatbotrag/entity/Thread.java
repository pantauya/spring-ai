package com.shawa.chatbotrag.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import java.util.List;


@Entity
@Table(name = "threads")
@Data
public class Thread {
    @Id
    private UUID id;

    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "thread", fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<Message> messages;
}

