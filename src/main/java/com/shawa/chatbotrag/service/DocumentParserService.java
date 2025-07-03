package com.shawa.chatbotrag.service;

import com.shawa.chatbotrag.entity.DocumentMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DocumentParserService {

    private static final Logger log = LoggerFactory.getLogger(DocumentParserService.class);

    /**
     * Metode parse final.
     * Memisahkan Preamble, lalu mendeteksi tipe Batang Tubuh (Pasal vs Generik).
     */
    public List<Document> parse(String rawText, DocumentMetadata baseMetadata) {
        List<Document> allChunks = new ArrayList<>();
        String content = rawText.split("\\bLAMPIRAN\\b")[0];
        if (content.trim().isEmpty()) {
            return allChunks;
        }

        // Cari pembatas antara bagian pembuka dan isi utama
        int bodyStartIndex = findBodyStartIndex(content);
        
        String preambleText = content.substring(0, bodyStartIndex).trim();
        String bodyText = content.substring(bodyStartIndex).trim();

        // Proses Preamble menjadi chunk-chunk utuh
        allChunks.addAll(processPreamble(preambleText, baseMetadata));
        
        // Proses Batang Tubuh
        if (!bodyText.isEmpty()) {
            // Cek apakah ini dokumen berbasis Pasal
            if (bodyText.matches("(?is).*\\bPasal.*")) {
                allChunks.addAll(processPasalBasedBody(bodyText, baseMetadata));
            } else {
                // Jika bukan, ini adalah Diktum/Instruksi. Gunakan splitter token standar.
                allChunks.addAll(processGenericBody(bodyText, baseMetadata));
            }
        }
        
        return allChunks;
    }

    /**
     * Menemukan posisi awal dari Batang Tubuh.
     */
    private int findBodyStartIndex(String content) {
        // Cari posisi MEMUTUSKAN sebagai prioritas utama
        int memutuskanIndex = content.indexOf("MEMUTUSKAN");
        if (memutuskanIndex != -1) {
            return memutuskanIndex;
        }
        
        // Jika tidak ada MEMUTUSKAN, cari penanda body pertama (Pasal 1 atau KESATU)
        Pattern pattern = Pattern.compile("\\b(Pasal\\s+1|BAB\\s+I|KESATU)\\b");
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.start() : content.length(); // Jika tidak ada, seluruhnya adalah preamble
    }
    
    /**
     * Memproses Preamble (Menimbang & Mengingat) menjadi blok utuh per seksi.
     */
    private List<Document> processPreamble(String preambleText, DocumentMetadata baseMetadata) {
        List<Document> chunks = new ArrayList<>();
        if (preambleText.isEmpty()) return chunks;

        int mengingatIndex = preambleText.indexOf("Mengingat");
        
        String menimbangText = (mengingatIndex != -1) ? preambleText.substring(0, mengingatIndex).trim() : preambleText.trim();
        
        if (!menimbangText.isEmpty()) {
            Map<String, Object> metadata = createBaseMetadata(baseMetadata);
            metadata.put("section", "menimbang");
            chunks.add(new Document(menimbangText, metadata));
        }

        if (mengingatIndex != -1) {
            String mengingatText = preambleText.substring(mengingatIndex).trim();
            Map<String, Object> metadata = createBaseMetadata(baseMetadata);
            metadata.put("section", "mengingat");
            chunks.add(new Document(mengingatText, metadata));
        }
        return chunks;
    }

    /**
     * Memproses Batang Tubuh berbasis Pasal: 1 Pasal = 1 Chunk.
     */
   
     // =========================================================================
// === KODE BARU (Mengimplementasikan Logika Hirarkis Anda) ===
// =========================================================================

/**
 * Menerapkan logika hierarkis: "Per Bab, jika terlalu besar baru pecah lagi
 * menjadi chunk yang berisi beberapa Pasal".
 */
private List<Document> processPasalBasedBody(String batangTubuhText, DocumentMetadata baseMetadata) {
    List<Document> finalChunks = new ArrayList<>();
    final int MAX_CHARS_PER_CHUNK = 6000; // Estimasi ~1500 token

    // Langkah 1: Selalu pecah seluruh batang tubuh menjadi blok-blok per BAB.
    String[] babChunks = batangTubuhText.split("(?=\\n*\\bBAB\\s+[IVXLCDM]+\\b)");

    for (String babText : babChunks) {
        String trimmedBabText = babText.trim().replaceFirst("MEMUTUSKAN\\s*:", "").trim();
        if (trimmedBabText.isEmpty()) continue;

        // Langkah 2: Cek ukuran setiap Bab
        if (trimmedBabText.length() <= MAX_CHARS_PER_CHUNK) {
            // KASUS A: UKURAN BAB AMAN -> Seluruh Bab ini menjadi SATU CHUNK
            Map<String, Object> metadata = createBaseMetadata(baseMetadata);
            enrichPasalMetadata(trimmedBabText, metadata); // Ambil info Bab & Pasal pertama
            finalChunks.add(new Document(trimmedBabText, metadata));

        } else {
            // KASUS B: UKURAN BAB TERLALU BESAR -> Pecah Bab ini lebih lanjut
            log.warn("File: {}, Bab '{}' terlalu besar ({} char). Memecah menjadi chunk multi-pasal.",
                     baseMetadata.getFilename(), getBabNumber(trimmedBabText), trimmedBabText.length());
            
            // Pecah Bab besar ini menjadi unit-unit per Pasal
            String[] pasalChunks = trimmedBabText.split("(?=\\n*\\bPasal\\s+\\d+)");
            StringBuilder chunkBuilder = new StringBuilder();
            
            for (String pasalText : pasalChunks) {
                String trimmedPasal = pasalText.trim();
                if (trimmedPasal.isEmpty()) continue;

                // Cek apakah penambahan Pasal baru akan membuat chunk terlalu besar
                if (chunkBuilder.length() > 0 && chunkBuilder.length() + trimmedPasal.length() > MAX_CHARS_PER_CHUNK) {
                    // Jika iya, simpan chunk yang sudah ada
                    Map<String, Object> metadata = createBaseMetadata(baseMetadata);
                    enrichPasalMetadata(chunkBuilder.toString(), metadata);
                    finalChunks.add(new Document(chunkBuilder.toString(), metadata));
                    chunkBuilder = new StringBuilder(); // Mulai builder baru
                }
                
                // Gabungkan Pasal ini ke builder
                if (chunkBuilder.length() > 0) chunkBuilder.append("\n\n");
                chunkBuilder.append(trimmedPasal);
            }

            // Simpan sisa chunk terakhir dari builder
            if (chunkBuilder.length() > 0) {
                Map<String, Object> metadata = createBaseMetadata(baseMetadata);
                enrichPasalMetadata(chunkBuilder.toString(), metadata);
                finalChunks.add(new Document(chunkBuilder.toString(), metadata));
            }
        }
    }
    return finalChunks;
}

// Anda juga butuh helper kecil ini jika belum ada
private String getBabNumber(String babChunkText) {
    Matcher babMatcher = Pattern.compile("^BAB\\s+([IVXLCDM]+)", Pattern.CASE_INSENSITIVE).matcher(babChunkText);
    return babMatcher.find() ? babMatcher.group(1).trim() : "Unknown";
}

    /**
     * Fallback untuk dokumen non-Pasal (Diktum, Instruksi, dll).
     * Memecah teks berdasarkan ukuran token, BUKAN per baris atau per diktum.
     */
    private List<Document> processGenericBody(String bodyText, DocumentMetadata baseMetadata) {
        log.info("Dokumen {} tidak berbasis Pasal, menggunakan TokenTextSplitter standar.", baseMetadata.getFilename());
        
        // Menggunakan splitter bawaan Spring AI yang andal dan berbasis token
        TokenTextSplitter tokenSplitter = new TokenTextSplitter(1000, 50, 5, 10000, true);
        
        String cleanedBodyText = bodyText.replaceFirst("MEMUTUSKAN\\s*:", "").trim();
        Document tempDoc = new Document(cleanedBodyText, createBaseMetadata(baseMetadata));
        List<Document> chunks = tokenSplitter.split(tempDoc);

        // Tambahkan section metadata setelah di-split
        chunks.forEach(chunk -> chunk.getMetadata().put("section", "isi"));
        return chunks;
    }

    // --- METODE-METODE HELPER ---

    private Map<String, Object> createBaseMetadata(DocumentMetadata baseMetadata) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("file_name", baseMetadata.getFilename());
        metadata.put("jenis_peraturan", baseMetadata.getJenis());
        metadata.put("nomor_peraturan", baseMetadata.getNomor());
        metadata.put("tahun_peraturan", baseMetadata.getTahun());
        return metadata;
    }

    private void enrichPasalMetadata(String chunkText, Map<String, Object> metadata) {
        metadata.put("section", "batang_tubuh");
        Matcher babMatcher = Pattern.compile("BAB\\s+([IVXLCDM]+)").matcher(chunkText);
        if (babMatcher.find()) metadata.put("bab", babMatcher.group(1).trim());

        Matcher pasalMatcher = Pattern.compile("Pasal\\s+(\\d+)").matcher(chunkText);
        if (pasalMatcher.find()) metadata.put("pasal", pasalMatcher.group(1).trim());
    }
}