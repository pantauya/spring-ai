package com.shawa.chatbotrag.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegulationChunker {

    private static final Pattern PATTERN = Pattern.compile(
        "(?<=^|\\n)(JUDUL|Menimbang|Mengingat|Memutuskan|BAB\\s+[IVXLCDM]+|Pasal\\s+\\d+|\\(\\d+\\)|[a-z]\\.)",
        Pattern.CASE_INSENSITIVE
    );

    public List<String> splitByStructure(String text) {
        List<String> chunks = new ArrayList<>();
        Matcher matcher = PATTERN.matcher(text);
    
        List<Integer> positions = new ArrayList<>();
        positions.add(0);  // Tambahkan posisi awal sebagai chunk pertama (judul biasanya di sini)
    
        while (matcher.find()) {
            if (matcher.start() != 0) {  // Supaya ga dobel start di posisi 0
                positions.add(matcher.start());
            }
        }
    
        for (int i = 0; i < positions.size(); i++) {
            int start = positions.get(i);
            int end = (i + 1 < positions.size()) ? positions.get(i + 1) : text.length();
            String chunk = text.substring(start, end).trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
        }
    
        return chunks;
    }
    
}
