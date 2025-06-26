package com.shawa.chatbotrag.service;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class IngestionStatsCollector {

    private int totalFiles = 0;
    private int totalPages = 0;
    private int totalChunks = 0;
    private int totalTokens = 0;
    private int totalFilesWithOcr = 0;
    private int totalOcrPages = 0;
    private int totalTextPages = 0;
    private final List<OcrSample> ocrSamples = new ArrayList<>();

    public record OcrSample(String filename, int pageNumber, String sampleText) {}

    private long startTime;
    public void startTimer() {
        this.startTime = System.currentTimeMillis();
    }

    public void record(String filename, int pages, int chunks, int tokens, int ocrPages, Map<Integer, String> samplePerPage) {
        totalFiles++;
        totalPages += pages;
        totalChunks += chunks;
        totalTokens += tokens;
        totalTextPages += (pages - ocrPages);
        if (ocrPages > 0) {
            totalFilesWithOcr++;
            totalOcrPages += ocrPages;
            for (Map.Entry<Integer, String> entry : samplePerPage.entrySet()) {
                if (entry.getValue().strip().length() > 30) { // hanya ambil sampel yang agak panjang
                    ocrSamples.add(new OcrSample(filename, entry.getKey() + 1, entry.getValue().strip()));
                }
            }
        }
    }

    public void printSummary() {
        System.out.println("===== INGESTION SUMMARY =====");
        System.out.println("Total Files: " + totalFiles);
        System.out.println("Total Pages: " + totalPages);
        System.out.println("Total Chunks: " + totalChunks);
        System.out.println("Total Tokens: " + totalTokens);
        System.out.println("Total Text Pages," + totalTextPages);
        System.out.println("Avg Tokens per File: " + (totalFiles == 0 ? 0 : totalTokens / totalFiles));
        System.out.println("Avg Tokens per Chunk: " + (totalChunks == 0 ? 0 : totalTokens / totalChunks));
        System.out.println("Files with OCR: " + totalFilesWithOcr);
        System.out.println("Total OCR Pages: " + totalOcrPages);
        System.out.println("Avg OCR Pages per OCR file: " +
            (totalFilesWithOcr == 0 ? 0 : (double) totalOcrPages / totalFilesWithOcr));

        System.out.println("\n===== OCR TEXT SAMPLES =====");
        ocrSamples.stream().limit(5).forEach(sample -> {
            System.out.println("📄 " + sample.filename + " - Halaman " + sample.pageNumber);
            System.out.println(sample.sampleText.substring(0, Math.min(300, sample.sampleText.length())));
            System.out.println("---");
        });
    }
    public void writeToFile(String outputPath) {
    try (PrintWriter writer = new PrintWriter(outputPath, StandardCharsets.UTF_8)) {
        writer.println("Total Files," + totalFiles);
        writer.println("Total Pages," + totalPages);
        writer.println("Total Chunks," + totalChunks);
        writer.println("Total Tokens," + totalTokens);
        writer.println("Avg Tokens per File," + (totalFiles == 0 ? 0 : totalTokens / totalFiles));
        writer.println("Avg Tokens per Chunk," + (totalChunks == 0 ? 0 : totalTokens / totalChunks));
        writer.println("Files with OCR," + totalFilesWithOcr);
        writer.println("Total OCR Pages," + totalOcrPages);
        writer.println("Avg OCR Pages per OCR File," +
            (totalFilesWithOcr == 0 ? 0 : (double) totalOcrPages / totalFilesWithOcr));

        writer.println();
        writer.println("Sample OCR Results:");
        writer.println("Filename,PageNumber,SampleText");
        for (OcrSample sample : ocrSamples) {
            String cleanText = sample.sampleText().replaceAll("[\\r\\n]+", " ").replace(",", " ");
            writer.printf("%s,%d,%s%n", sample.filename(), sample.pageNumber(), cleanText);
        }
        long durationMs = System.currentTimeMillis() - startTime;
        writer.println("Total Processing Time (s)," + (durationMs / 1000.0));


        System.out.println("📁 Statistik berhasil ditulis ke file: " + outputPath);
    } catch (IOException e) {
        System.err.println("❌ Gagal menulis statistik ke file: " + e.getMessage());
    }
}

}


