package com.shawa.chatbotrag.service;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

@Service
public class OCRService {

    private static final Logger log = LoggerFactory.getLogger(OCRService.class);

    private final Tesseract tesseract;

    public OCRService() {
        tesseract = new Tesseract();
        tesseract.setDatapath("C://Program Files//Tesseract-OCR//tessdata"); 
        tesseract.setLanguage("ind");
        tesseract.setPageSegMode(6);
    }

    public String extractText(Resource pdfResource) {
        try (InputStream inputStream = pdfResource.getInputStream()) {

            PDDocument document = PDDocument.load(inputStream);
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            StringBuilder extractedText = new StringBuilder();

            int numPages = document.getNumberOfPages();
            for (int pageIndex = 0; pageIndex < numPages; pageIndex++) {
                BufferedImage image = pdfRenderer.renderImageWithDPI(pageIndex, 600); 
                String text = tesseract.doOCR(image); 
                text = cleanExtractedText(text); 
                text = preserveStructure(text);
                extractedText.append(text).append(" "); 
            }

            return extractedText.toString();
        } catch (TesseractException e) {
            log.error("Error during OCR processing: ", e);
            throw new RuntimeException("OCR failed", e);
        } catch (IOException e) {
            log.error("Error reading the PDF file: ", e);
            throw new RuntimeException("File reading failed", e);
        } 
    }

        public String extractText(BufferedImage image) {
        try {
            String text = tesseract.doOCR(image);
            text = cleanExtractedText(text);
            text = preserveStructure(text);
            return text;
        } catch (TesseractException e) {
            log.error("Error during OCR processing for image: ", e);
            return "";
        }
    }

    private String cleanExtractedText(String extractedText) {
    extractedText = extractedText.replaceAll("\\d+\\.\\s*", "");      // hapus "1. "
    extractedText = extractedText.replaceAll("[a-zA-Z]+\\.", "");     // hapus "a.", "b."
    
    // Pertahankan struktur baris
    extractedText = extractedText.replaceAll("\\n{2,}", "\n");        // ubah newline ganda jadi satu
    extractedText = extractedText.replaceAll("(?m)^[ \\t]+", "");     // hapus indentasi di awal baris
    extractedText = extractedText.replaceAll("  +", " ");             // hapus spasi ganda
    extractedText = extractedText.replaceAll("([.,;!?:])", "$1 ");    // spasi setelah tanda baca (opsional)

    return extractedText.trim();
}


    private String preserveStructure(String text) {
        // Pisahkan BAB (misalnya "BAB II")
        text = text.replaceAll("(?m)^\\s*(BAB\\s+[IVXLCDM]+)", "\n\n$1");

        // Pisahkan Bagian (misalnya "Bagian Kesatu")
        text = text.replaceAll("(?m)^\\s*(Bagian\\s+[A-Za-z]+)", "\n\n$1");

        // Pisahkan Pasal
        text = text.replaceAll("(?m)^\\s*(Pasal\\s+\\d+)", "\n\n$1");

        // Pisahkan ayat (1), (2), dst.
        text = text.replaceAll("(?m)(?<=\\S)\\s*\\((\\d+)\\)", "\n($1)");

        // Pisahkan huruf a., b., dst.
        text = text.replaceAll("(?m)(?<=\\n)([a-z])\\.", "\n$1.");

        return text;
    }
}

