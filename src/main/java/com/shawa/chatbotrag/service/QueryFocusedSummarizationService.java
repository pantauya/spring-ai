package com.shawa.chatbotrag.service;

import org.springframework.stereotype.Service;
import io.micrometer.common.lang.NonNull;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.compression.DocumentCompressor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class QueryFocusedSummarizationService implements DocumentCompressor {

    private final ChatClient chatClient;

    public QueryFocusedSummarizationService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @NonNull
@Override
public List<Document> compress(Query query, List<Document> documents) {
    return documents.stream()
        .map(doc -> {
            String summary = summarize(doc.getContent(), query.text()).trim();

            if (summary.isEmpty()) {
                return null; // Skip dokumen yang tidak relevan
            }

            // Ambil metadata
            Map<String, Object> originalMetadata = doc.getMetadata();
            String fileName = originalMetadata.getOrDefault("file_name", "Tidak diketahui").toString();
            String statusPeraturan = originalMetadata.getOrDefault("status_peraturan", "Tidak diketahui").toString();

            // Gabungkan ringkasan + metadata ke dalam content dokumen
            String combinedContent = String.format(
              "Summary:\n%s\nSource: %s\nStatus: %s",
                summary,
                fileName,
                statusPeraturan
            );


            // Metadata tetap disalin jika ingin digunakan juga
            Map<String, Object> metadataCopy = new HashMap<>();
            metadataCopy.put("file_name", fileName);
            metadataCopy.put("status_peraturan", statusPeraturan);

            return new Document(combinedContent, metadataCopy);
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
}


    private String summarize(String content, String query) {
    String prompt = String.format(
        "You are a legal assistant that extracts only the most relevant parts from official regulatory documents.\n\n" +
        "=== Question ===\n%s\n\n" +
        "=== Document ===\n%s\n\n" +
        "=== Instructions ===\n" +
        "- Extract only information that directly answers the question using Indonesian.\n" +
        "- Be precise. Do not add explanations, generalizations,questions, assumptions, or personal opinions.\n" +
        "Key points:",
        query, content
    );


    return chatClient.prompt(prompt).call().content();
}

}