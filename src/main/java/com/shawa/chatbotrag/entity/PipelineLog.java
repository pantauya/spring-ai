package com.shawa.chatbotrag.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.document.Document;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PipelineLog {
    private UUID threadId;
    private String timestamp;
    private String userQuery;
    private List<String> expandedQueries;
    private List<Document> retrievedDocuments;
    private List<Document> rankedDocuments;
    private List<Document> compressedDocuments;
    private String augmentedQuery;
    private String thought;
    private String content;
}
