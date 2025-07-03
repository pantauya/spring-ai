package com.shawa.chatbotrag.service;

import org.springframework.ai.transformer.splitter.TokenTextSplitter;

import java.util.ArrayList;
import java.util.List;

public class CustomTokenTextSplitter extends TokenTextSplitter {
    private final int overlapSize;

    /**
     * @param chunkSize               Jumlah token maksimum per chunk
     * @param minChunkSizeChars      Panjang karakter minimal setiap chunk
     * @param minChunkLengthToEmbed  Panjang minimal chunk agar di-embed
     * @param maxNumChunks           Batas maksimal jumlah chunk
     * @param keepSeparator          Apakah mempertahankan pemisah?
     * @param overlapSize            Jumlah karakter yang dijadikan overlap dari chunk sebelumnya
     */
    public CustomTokenTextSplitter(int chunkSize, int minChunkSizeChars, int minChunkLengthToEmbed, int maxNumChunks, boolean keepSeparator, int overlapSize) {
        super(chunkSize, minChunkSizeChars, minChunkLengthToEmbed, maxNumChunks, keepSeparator);
        this.overlapSize = overlapSize;
    }

    @Override
    protected List<String> splitText(String text) {
        List<String> baseChunks = super.splitText(text);
        List<String> overlappedChunks = new ArrayList<>();

        for (int i = 0; i < baseChunks.size(); i++) {
            String chunk = baseChunks.get(i);

            if (i > 0) {
                String prevChunk = baseChunks.get(i - 1);
                int overlapStart = Math.max(0, prevChunk.length() - overlapSize);
                String overlap = prevChunk.substring(overlapStart);
                chunk = overlap + " " + chunk;
            }

            overlappedChunks.add(chunk);
        }

        return overlappedChunks;
    }
}
