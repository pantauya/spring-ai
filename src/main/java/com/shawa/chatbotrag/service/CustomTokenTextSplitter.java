package com.shawa.chatbotrag.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.transformer.splitter.TokenTextSplitter;

public class CustomTokenTextSplitter extends TokenTextSplitter {
    private final int overlapSize;

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

            // Jika bukan chunk pertama, tambahkan overlap
            if (i > 0) {
                String prevChunk = baseChunks.get(i - 1);
                // Tentukan posisi akhir dari chunk sebelumnya untuk menentukan overlap
                int overlapStart = Math.max(0, prevChunk.length() - overlapSize);
                String overlap = prevChunk.substring(overlapStart);

                // Gabungkan overlap dengan chunk saat ini dan pastikan tidak memotong kata
                chunk = overlap + " " + chunk;
            }

            // Pastikan chunk tidak terpotong di tengah kata
            chunk = avoidCuttingWords(chunk);

            overlappedChunks.add(chunk);
        }

        return overlappedChunks;
    }

    private String avoidCuttingWords(String chunk) {
        // Cari spasi terakhir sebelum chunk terpotong
        int lastSpace = chunk.lastIndexOf(' ', chunk.length() - 1);
        if (lastSpace != -1 && lastSpace < chunk.length()) {
            return chunk.substring(0, lastSpace); // Memastikan chunk tidak terpotong di tengah kata
        } else {
            return chunk; // Jika tidak ada spasi ditemukan, kembalikan chunk asli
        }
    }
}
