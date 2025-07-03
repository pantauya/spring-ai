package com.shawa.chatbotrag.service;

import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

import java.util.ArrayList;
import java.util.List;

public class SafeTokenTextSplitter extends TokenTextSplitter {

    private final int overlapTokenCount;
    private final TokenCountEstimator estimator;
    private final int chunkSize; // simpan chunk size secara manual

    public SafeTokenTextSplitter(int chunkSize,
                                 int minChunkSizeChars,
                                 int minChunkLengthToEmbed,
                                 int maxNumChunks,
                                 boolean keepSeparator,
                                 int overlapTokenCount) {

        super(chunkSize, minChunkSizeChars, minChunkLengthToEmbed, maxNumChunks, keepSeparator);
        this.chunkSize = chunkSize; // inisialisasi manual
        this.overlapTokenCount = overlapTokenCount;
        this.estimator = new JTokkitTokenCountEstimator(); // default CL100K
    }

    @Override
    protected List<String> splitText(String text) {
        List<String> baseChunks = super.splitText(text);
        List<String> adjustedChunks = new ArrayList<>();

        for (int i = 0; i < baseChunks.size(); i++) {
            String currentChunk = baseChunks.get(i);

            // Tambahkan overlap token dari chunk sebelumnya jika ada
            if (i > 0) {
                String prevChunk = baseChunks.get(i - 1);
                String overlapText = trimToLastTokens(prevChunk, overlapTokenCount);
                currentChunk = overlapText + " " + currentChunk;
            }

            // Pangkas jika token-nya melebihi chunk size
            if (estimator.estimate(currentChunk) > chunkSize) {
                currentChunk = trimToFirstTokens(currentChunk, chunkSize);
            }

            adjustedChunks.add(currentChunk);
        }

        return adjustedChunks;
    }

    private String trimToLastTokens(String text, int maxTokens) {
        String[] tokens = text.split("\\s+");
        int start = Math.max(0, tokens.length - maxTokens);
        return String.join(" ", List.of(tokens).subList(start, tokens.length));
    }

    private String trimToFirstTokens(String text, int maxTokens) {
        String[] tokens = text.split("\\s+");
        int end = Math.min(maxTokens, tokens.length);
        return String.join(" ", List.of(tokens).subList(0, end));
    }
}
