package com.shawa.chatbotrag.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.postretrieval.selection.DocumentSelector;
import org.springframework.ai.rag.Query;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import java.util.List;
import java.util.stream.Collectors;

public class RelevanceBasedDocumentSelector implements DocumentSelector {

    private final double relevanceThreshold;
    private final OllamaEmbeddingModel embeddingModel;  
    public RelevanceBasedDocumentSelector(double relevanceThreshold, OllamaEmbeddingModel embeddingModel) {
        this.relevanceThreshold = relevanceThreshold;
        this.embeddingModel = embeddingModel;
    }
    @Override
    public List<Document> select(Query query, List<Document> documents) {
        return documents.stream()
                .filter(doc -> getRelevance(query, doc) >= relevanceThreshold) // Filter berdasarkan threshold relevansi
                .collect(Collectors.toList());
    }
    private double getRelevance(Query query, Document document) {
        return calculateCosineSimilarity(query.text(), document.getContent());
    }
    private double calculateCosineSimilarity(String queryText, String documentText) {
        float[] queryEmbedding = embeddingModel.embed(queryText);
        float[] docEmbedding = embeddingModel.embed(documentText); 
        return cosineSimilarity(queryEmbedding, docEmbedding);
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
    
    @Override
    public List<Document> apply(Query query, List<Document> documents) {
        return select(query, documents);
    }
}