package com.shawa.chatbotrag.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.awt.image.BufferedImage;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.ai.document.Document;

import com.shawa.chatbotrag.entity.DocumentMetadata;
import com.shawa.chatbotrag.repository.DocumentMetadataRepository;



@Component
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    private final VectorStore vectorStore;
    private final OCRService ocrService;
    private final DocumentMetadataRepository metadataRepository;
    
    @Value("${app.document-folder-path}")
    private String documentFolderPath;

    public IngestionService(VectorStore vectorStore, OCRService ocrService, DocumentMetadataRepository metadataRepository) {
        this.vectorStore = vectorStore;
        this.ocrService = ocrService; 
        this.metadataRepository = metadataRepository;  
    }

    public void ingestDocuments() {
        File folder = new File(documentFolderPath);
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf"));

        if(files == null || files.length == 0) {
            log.error("File not found", documentFolderPath);
            return;
        }
        IngestionStatsCollector stats = new IngestionStatsCollector();
        stats.startTimer(); // ⬅️ Tambahkan ini SEBELUM for-loop file


        for (File file : files) {
            try {
                String cleanFileName = file.getName().replaceFirst("(?i)\\.pdf$", "").trim();
                log.info("Starting ingestion process for file: {}", cleanFileName);
                if (metadataRepository.existsByFilename(cleanFileName)) {
                    log.info("File {} sudah ada di document_metadata, skip indexing.", cleanFileName);
                    continue;
                }
                DocumentMetadata metadata = parseMetadataFromFilename(cleanFileName);
                metadataRepository.save(metadata);
                log.info("File {} ditambahkan ke tabel document_metadata.", cleanFileName);
                 // Load file as Spring Resource
                //  String extractedText = hybridExtractTextPerPage(file, ocrService);
                 var result = hybridExtractTextPerPageWithStats(file, ocrService);
                String extractedText = result.fullText();

                // Resource resource = new FileSystemResource(file);
                // String extractedText = tryExtractTextFromPdf(resource);

                // if (extractedText == null || extractedText.trim().isEmpty()) {
                //     // PDF kemungkinan hasil scan → pakai OCR
                //     log.info("Text extraction failed or empty. Falling back to OCR for file: {}", cleanFileName);
                //     extractedText = ocrService.extractText(resource);
                // }

                if (extractedText == null || extractedText.trim().isEmpty()) {
                    log.warn("Extracted text is still empty after OCR for file: {}", cleanFileName);
                    continue;
                }
                log.info("Text extraction completed for file: {}, extracted text length: {}", cleanFileName, extractedText.length());
          
                List<Document> documents = convertTextToDocuments(extractedText, cleanFileName);

                TextSplitter textSplitter = new CustomTokenTextSplitter(1000, 350,5,1200,false,150);              
                var chunks = textSplitter.apply(documents);

                 int totalTokens = chunks.stream().mapToInt(c -> c.getContent().split("\\s+").length).sum();

                stats.record(cleanFileName, result.totalPages(), chunks.size(), totalTokens, result.ocrPages(), result.ocrSamples());

    
                vectorStore.accept(chunks);
                log.info("VectorStore loaded with {} chunks of data from file: {}", chunks.size(), cleanFileName);

            } catch (Exception e) {
                log.error("Error during document ingestion for file: {}", file.getName(), e);
            }
        }
        stats.printSummary();  // tetap cetak ke console
        String outputPath = "statistik_ingestion.csv";
        stats.writeToFile(outputPath);

        
    }

        private List<Document> convertTextToDocuments(String extractedText, String cleanFileName) {
        List<Document> documents = new ArrayList<>();
        boolean isLampiran = false;

        String[] lines = extractedText.split("\\r?\\n");
        StringBuilder buffer = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();

        if (!isLampiran && trimmed.matches(".*\\bLAMPIRAN\\b.*")) {
            isLampiran = true;
            continue;
        }

            if (isLampiran) {
                continue; // Skip seluruh lampiran
            }

            buffer.append(line).append("\n");
        }

        if (buffer.length() > 0) {
            Document doc = new Document(buffer.toString());
            doc.getMetadata().put("section", "batang_tubuh");
            doc.getMetadata().put("file_name", cleanFileName); // <–– Tambahan ini
            documents.add(doc);
        }

        return documents;
    }

    private DocumentMetadata parseMetadataFromFilename(String filename) {

    // Contoh: "Keputusan Kepala BPS Nomor 87 Tahun 2024 Tentang ..."
     Pattern pattern = Pattern.compile("^(.*?)\\s+Nomor\\s+([a-zA-Z0-9_]+)\\s+Tahun\\s+(\\d{4})", Pattern.CASE_INSENSITIVE);
    Matcher matcher = pattern.matcher(filename);

    String jenis = null;
    String nomor = null;
    String tahun = null;

    if (matcher.find()) {
        jenis = matcher.group(1).trim();   // Keputusan Kepala BPS
        nomor = matcher.group(2).trim().replace('_', '/');  // Ganti "_" dengan "/" agar sesuai aslinya
        tahun = matcher.group(3).trim();   // 2024
    } else {
        log.warn("Gagal parsing metadata dari nama file: {}", filename);
    }

    return new DocumentMetadata(
        filename, // filename
        jenis,    // jenis
        nomor,    // nomor
        tahun,    // tahun
        null      // statusPeraturan (diisi manual)
    );
    }

    public String hybridExtractTextPerPage(File file, OCRService ocrService) {
    try (PDDocument document = PDDocument.load(file)) {
        PDFTextStripper textStripper = new PDFTextStripper();
        PDFRenderer renderer = new PDFRenderer(document);
        StringBuilder result = new StringBuilder();

        int totalPages = document.getNumberOfPages();

        for (int i = 0; i < totalPages; i++) {
            textStripper.setStartPage(i + 1);
            textStripper.setEndPage(i + 1);
            String pageText = textStripper.getText(document).trim();

            if (pageText.length() < 20) {
                // fallback ke OCR
                log.info("Page {} dari file {} kosong/minim teks, lakukan OCR.", i + 1, file.getName());
                BufferedImage image = renderer.renderImageWithDPI(i, 300);
                String ocrText = ocrService.extractText(image);
                result.append(ocrText).append("\n");
            } else {
                result.append(pageText).append("\n");
            }
        }

        return result.toString();
    } catch (IOException e) {
        log.error("Gagal load file PDF: {}", file.getName(), e);
        return null;
    }
    }
    
    public record ExtractionResult(
        String fullText,
        int totalPages,
        int ocrPages,
        Map<Integer, String> ocrSamples
    ) {}

    public ExtractionResult hybridExtractTextPerPageWithStats(File file, OCRService ocrService) {
    try (PDDocument document = PDDocument.load(file)) {
        PDFTextStripper textStripper = new PDFTextStripper();
        PDFRenderer renderer = new PDFRenderer(document);
        StringBuilder resultText = new StringBuilder();

        int totalPages = document.getNumberOfPages();
        int ocrPages = 0;
        Map<Integer, String> samplePerPage = new HashMap<>();

        for (int i = 0; i < totalPages; i++) {
            textStripper.setStartPage(i + 1);
            textStripper.setEndPage(i + 1);
            String pageText = textStripper.getText(document).trim();

            if (pageText.length() < 20) {
                BufferedImage image = renderer.renderImageWithDPI(i, 300);
                String ocrText = ocrService.extractText(image);
                resultText.append(ocrText).append("\n");
                ocrPages++;
                samplePerPage.put(i, ocrText);
            } else {
                resultText.append(pageText).append("\n");
            }
        }

        return new ExtractionResult(resultText.toString(), totalPages, ocrPages, samplePerPage);
    } catch (IOException e) {
        throw new RuntimeException("Gagal memproses file PDF: " + file.getName(), e);
    }
}

}