package com.shawa.chatbotrag.controll;

import com.shawa.chatbotrag.service.IngestionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ingest")
public class IngestionController {

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping
    public String triggerIngestion() {
        ingestionService.ingestDocuments();
        return "Ingestion process triggered.";
    }
}

