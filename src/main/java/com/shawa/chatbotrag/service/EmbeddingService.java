package com.shawa.chatbotrag.service;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class EmbeddingService {
    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }
    public double[] getEmbedding(String text) {
        var embeddingResponse = this.embeddingModel.embedForResponse(List.of(text));       
        float[] floatEmbedding = embeddingResponse.getResults().get(0).getOutput(); 
        double[] doubleEmbedding = new double[floatEmbedding.length];
        for (int i = 0; i < floatEmbedding.length; i++) {
            doubleEmbedding[i] = floatEmbedding[i]; 
        }
        return doubleEmbedding; 
    }
}

