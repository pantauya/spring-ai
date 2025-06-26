package com.shawa.chatbotrag.controll;

import com.shawa.chatbotrag.entity.Thread;
import com.shawa.chatbotrag.repository.ThreadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/threads")
@RequiredArgsConstructor
public class ThreadController {

    private final ThreadRepository threadRepository;

    @PostMapping
    public Thread createThread(@RequestBody Thread request) {
        request.setId(UUID.randomUUID());
        request.setCreatedAt(LocalDateTime.now());
        request.setUpdatedAt(LocalDateTime.now());
        return threadRepository.save(request);
    }

    @GetMapping
    public List<Thread> getAllThreads() {
        return threadRepository.findAllByOrderByUpdatedAtDesc(); // ini sudah pasti urut descending
    }
}


