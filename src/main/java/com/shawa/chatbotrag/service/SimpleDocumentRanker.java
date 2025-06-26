package com.shawa.chatbotrag.service;

import org.springframework.ai.rag.Query;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.postretrieval.ranking.DocumentRanker;

import io.micrometer.common.lang.NonNull;

import org.springframework.ai.ollama.OllamaEmbeddingModel;

import java.util.List;
import java.util.stream.Collectors;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleDocumentRanker implements DocumentRanker {

    private static final int SNIPPET_MAX_LENGTH = 512; // batasi panjang teks untuk embedding
    private final OllamaEmbeddingModel embeddingModel;
    private final Map<String, float[]> embeddingCache = new ConcurrentHashMap<>();

    public SimpleDocumentRanker(OllamaEmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    private float[] getEmbedding(String text) {
        return this.embeddingModel.embed(text);
    }

    private float[] getEmbeddingWithCache(String text) {
        return embeddingCache.computeIfAbsent(text, this::getEmbedding);
    }

    private String getSnippet(String content, int maxLength) {
        return content.length() > maxLength ? content.substring(0, maxLength) : content;
    }

    private double cosineSimilarity(float[] vec1, float[] vec2) {
        double dotProduct = 0.0, magnitude1 = 0.0, magnitude2 = 0.0;

        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            magnitude1 += Math.pow(vec1[i], 2);
            magnitude2 += Math.pow(vec2[i], 2);
        }

        magnitude1 = Math.sqrt(magnitude1);
        magnitude2 = Math.sqrt(magnitude2);

        return (magnitude1 == 0.0 || magnitude2 == 0.0) ? 0.0 : dotProduct / (magnitude1 * magnitude2);
    }

    @NonNull
    @Override
    public List<Document> rank(Query query, List<Document> documents) {
        float[] queryEmbedding = getEmbeddingWithCache(query.text());

        return documents.parallelStream()
                .map(doc -> {
                    String snippet = getSnippet(doc.getContent(), SNIPPET_MAX_LENGTH);
                    float[] docEmbedding = getEmbeddingWithCache(snippet);
                    double similarity = cosineSimilarity(queryEmbedding, docEmbedding);
                    return new ScoredDocument(doc, similarity);
                })
                .sorted(Comparator.comparingDouble(ScoredDocument::score).reversed())
                .map(ScoredDocument::document)
                .collect(Collectors.toList());
    }

    private record ScoredDocument(Document document, double score) {}
}
