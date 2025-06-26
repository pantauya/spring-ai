package com.shawa.chatbotrag.service;

import com.opencsv.CSVReader;
import com.shawa.chatbotrag.repository.DocumentMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class MetadataLoaderService {

    private final DocumentMetadataRepository metadataRepository;

    @Value("${app.status-metadata-path}")
    private String metadataFilePath;

    public List<String> loadStatusMetadata() {
    List<String> resultLog = new ArrayList<>();

    try (CSVReader reader = new CSVReader(new FileReader(metadataFilePath))) {
        String[] line;
        boolean isHeader = true;

        while ((line = reader.readNext()) != null) {
            if (isHeader) {
                isHeader = false;
                continue;
            }

            String filename = line[0];
            String status = line[1];

            metadataRepository.findById(filename).ifPresentOrElse(existing -> {
                existing.setStatusPeraturan(status);
                metadataRepository.save(existing);
                resultLog.add("Updated: " + filename + " -> " + status);
            }, () -> {
                resultLog.add("NOT FOUND in DB: " + filename);
            });
        }

        resultLog.add("Status metadata loaded successfully!");
    } catch (Exception e) {
        resultLog.add("Failed to load status metadata: " + e.getMessage());
    }

    return resultLog;
}
}
