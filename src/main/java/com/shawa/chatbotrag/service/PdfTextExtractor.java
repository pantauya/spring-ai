package com.shawa.chatbotrag.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.InputStream;


@Service
public class PdfTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    private final OCRService ocrService;

    public PdfTextExtractor(OCRService ocrService) {
        this.ocrService = ocrService;
    }

    public String hybridExtractTextByPage(Resource file) {
        StringBuilder finalText = new StringBuilder();

        try (InputStream inputStream = file.getInputStream(); PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper textStripper = new PDFTextStripper();
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            int totalPages = document.getNumberOfPages();

            for (int page = 0; page < totalPages; page++) {
                textStripper.setStartPage(page + 1);
                textStripper.setEndPage(page + 1);
                String pageText = textStripper.getText(document).trim();

                if (pageText.isEmpty()) {
                    // Halaman kosong → gunakan OCR
                    log.info("Page {} is empty. Using OCR...", page + 1);
                    BufferedImage image = pdfRenderer.renderImageWithDPI(page, 600);
                    String ocrText = ocrService.extractText(image);

                    if (!ocrText.isEmpty()) {
                        finalText.append(ocrText).append("\n");
                    } else {
                        log.warn("OCR result is empty on page {}", page + 1);
                    }

                } else {
                    log.info("Page {} has extractable text.", page + 1);
                    finalText.append(pageText).append("\n");
                }
            }

        } catch (Exception e) {
            log.error("Hybrid text extraction failed for file {}: {}", file.getFilename(), e.getMessage(), e);
        }
        return finalText.toString();
    }
}

