package com.shawa.chatbotrag.service;

import org.springframework.ai.document.Document;
import com.shawa.chatbotrag.repository.DocumentMetadataRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MetadataEnrichmentService {

    private final DocumentMetadataRepository metadataRepo;

    public MetadataEnrichmentService(DocumentMetadataRepository metadataRepo) {
        this.metadataRepo = metadataRepo;
    }

    public void enrichStatus(List<Document> documents) {
        for (Document doc : documents) {
            String fileName = (String) doc.getMetadata().get("file_name");
            if (fileName != null) {
                String status = metadataRepo.findStatusByFilename(fileName).orElse("Tidak diketahui");
                if (status != null) {
                    doc.getMetadata().put("status_peraturan", status);
                }
            }
        }
    }
}
