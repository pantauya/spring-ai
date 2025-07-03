package com.shawa.chatbotrag.service;

import org.springframework.ai.transformer.splitter.TextSplitter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

public class RecursiveCharacterTextSplitter extends TextSplitter {

    private final List<String> separators;
    private final int chunkSize;
    private final boolean regex;
    private final Function<String, Integer> lengthFunction = String::length;

    /**
     * Konstruktor utama.
     * @param chunkSize Ukuran maksimal chunk dalam karakter.
     * @param chunkOverlap (Saat ini belum diimplementasikan di versi simpel ini, tapi bisa ditambahkan kemudian)
     * @param regex Apakah separator menggunakan Regex.
     * @param separators Daftar separator dari prioritas tertinggi ke terendah.
     */
    public RecursiveCharacterTextSplitter(int chunkSize, int chunkOverlap, boolean regex, List<String> separators) {
        // NOTE: chunkOverlap belum digunakan di implementasi simpel ini,
        // karena fokus kita adalah pada splitting struktural yang benar.
        this.chunkSize = chunkSize;
        this.separators = separators;
        this.regex = regex;
    }
    
    /**
     * Metode wajib yang harus diimplementasikan dari abstract class TextSplitter.
     * Di sinilah logika utama pemisahan teks kita berada.
     */
    @Override
    protected List<String> splitText(String text) {
        final List<String> finalChunks = new ArrayList<>();
        splitRecursively(text, this.separators, finalChunks);
        return finalChunks;
    }
    
    private void splitRecursively(String text, List<String> separators, List<String> finalChunks) {
        if (text.trim().isEmpty()) {
            return;
        }

        // Jika teks sudah cukup kecil, jadikan satu chunk dan berhenti.
        if (lengthFunction.apply(text) <= this.chunkSize) {
            finalChunks.add(text);
            return;
        }

        // Cari separator yang paling cocok di level ini
        String currentSeparator = null;
        for (String separator : separators) {
            Pattern pattern = this.regex ? Pattern.compile(separator) : Pattern.compile(Pattern.quote(separator));
            if (pattern.matcher(text).find()) {
                currentSeparator = separator;
                break;
            }
        }

        // Jika tidak ada separator yang cocok, atau teks lebih kecil, potong paksa
        if (currentSeparator == null) {
            for(int i = 0; i < text.length(); i += this.chunkSize) {
                finalChunks.add(text.substring(i, Math.min(text.length(), i + this.chunkSize)));
            }
            return;
        }

        // Lakukan splitting berdasarkan separator yang ditemukan
        String[] splits;
        if (this.regex) {
            // Split dengan lookbehind agar separator tidak hilang
            splits = text.split("(?=" + currentSeparator + ")");
        } else {
            splits = text.split(Pattern.quote(currentSeparator));
        }

        List<String> nextSeparators = separators.subList(separators.indexOf(currentSeparator) + 1, separators.size());
        
        // Panggil rekursif untuk setiap hasil split
        for(String split : splits) {
            splitRecursively(split, nextSeparators, finalChunks);
        }
    }
}