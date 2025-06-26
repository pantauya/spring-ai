package com.shawa.chatbotrag.service;

import org.springframework.stereotype.Service;

import io.micrometer.common.lang.NonNull;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.compression.DocumentCompressor;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class QueryFocusedSummarizationService implements DocumentCompressor {

    private final ChatClient chatClient;

    public QueryFocusedSummarizationService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @NonNull
    @Override
    public List<Document> compress(Query query,List<Document> documents) {
        return documents.stream()
                .map(doc -> new Document(summarize(doc.getContent(), query.text()), doc.getMetadata()))
                .collect(Collectors.toList());
    }

    private String summarize(String content, String query) {
           String prompt = "You are an AI system tasked with summarizing documents in Indonesian. Your goal is to generate a summary that is relevant to the given question.\n"
                + "Question: " + query + "\n"
                + "Document: " + content + "\n"
                + "Instructions:\n"
                + "- Focus the summary based on the user's question.\n"
                + "- You may include relevant insights that are not explicitly stated, as long as they can be reasonably inferred from the document.\n"
                + "- Avoid adding information that clearly falls outside the document's content.\n"
                + "- Write a concise and clear summary in Indonesian.\n";


        return chatClient.prompt(prompt)
                      .call()
                      .content(); // Mengambil hasil ringkasan
    }
}
