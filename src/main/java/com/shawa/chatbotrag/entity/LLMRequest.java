package com.shawa.chatbotrag.entity;

import java.util.UUID;

import lombok.Data;

@Data
public class LLMRequest {
    private String query;
    private UUID threadId;
}
