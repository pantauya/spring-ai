package com.shawa.chatbotrag.controll;

import com.shawa.chatbotrag.service.MetadataLoaderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/metadata")
@RequiredArgsConstructor
public class MetadataController {

    private final MetadataLoaderService metadataLoaderService;

    @PostMapping("/load-status")
    public ResponseEntity<List<String>> loadStatusMetadata() {
         List<String> result = metadataLoaderService.loadStatusMetadata();
        return ResponseEntity.ok(result);
    }
}
