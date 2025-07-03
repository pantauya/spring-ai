package com.shawa.chatbotrag.service;

import org.springframework.ai.rag.Query;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.postretrieval.ranking.DocumentRanker;

import io.micrometer.common.lang.NonNull;

import org.springframework.ai.ollama.OllamaEmbeddingModel;

import java.util.List;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleDocumentRanker implements DocumentRanker {

    private static final int SNIPPET_MAX_LENGTH = 512;
    private final OllamaEmbeddingModel embeddingModel;
    private final Map<String, float[]> embeddingCache = new ConcurrentHashMap<>();
    private static final Map<String, Integer> STATUS_PRIORITY = Map.of(
    "Baru", 1,
    "Berlaku", 2,
    "Mengubah", 3,
    "diubah", 4,
    "Mencabut", 5,
    "Dicabut", 6
);
    public SimpleDocumentRanker(OllamaEmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    private float[] getEmbedding(String text) {
        return this.embeddingModel.embed(text);
    }

    private float[] getEmbeddingWithCache(String text) {
        return embeddingCache.computeIfAbsent(text, this::getEmbedding);
    }

    private double cosineSimilarity(float[] vec1, float[] vec2) {
        double dotProduct = 0.0, magnitude1 = 0.0, magnitude2 = 0.0;
        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            magnitude1 += vec1[i] * vec1[i];
            magnitude2 += vec2[i] * vec2[i];
        }
        return (magnitude1 == 0 || magnitude2 == 0) ? 0.0 : dotProduct / (Math.sqrt(magnitude1) * Math.sqrt(magnitude2));
    }

    private List<String> splitBySlidingWindow(String content, int windowSize, int stepSize) {
        List<String> chunks = new ArrayList<>();
        int length = content.length();
        for (int i = 0; i < length; i += stepSize) {
            int end = Math.min(i + windowSize, length);
            chunks.add(content.substring(i, end));
            if (end == length) break;
        }
        return chunks;
    }

    @NonNull
    @Override
    public List<Document> rank(Query query, List<Document> documents) {
        float[] queryEmbedding = getEmbeddingWithCache(query.text());

        return documents.parallelStream()
            .map(doc -> {
                List<String> chunks = splitBySlidingWindow(doc.getContent(), SNIPPET_MAX_LENGTH, SNIPPET_MAX_LENGTH / 2);
                double maxSimilarity = chunks.stream()
                        .map(this::getEmbeddingWithCache)
                        .mapToDouble(chunkEmbedding -> cosineSimilarity(queryEmbedding, chunkEmbedding))
                        .max()
                        .orElse(0.0);

                doc.getMetadata().put("score", maxSimilarity);

                // Ambil status dan konversi ke prioritas
                String status = ((String) doc.getMetadata().getOrDefault("status_peraturan", "Berlaku")).toLowerCase();
                int statusPriority = STATUS_PRIORITY.getOrDefault(status, 0); // makin tinggi makin tidak diprioritaskan

                return new ScoredDocument(doc, maxSimilarity, statusPriority);
            })
            .sorted(Comparator
                .comparingInt(ScoredDocument::statusPriority)
                .thenComparingDouble(ScoredDocument::score).reversed()) // skor tertinggi lebih dulu
            .map(ScoredDocument::document)
            .collect(Collectors.toList());

    }

    private record ScoredDocument(Document document, double score, int statusPriority) {}

}
