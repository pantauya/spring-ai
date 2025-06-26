package com.shawa.chatbotrag.controll;

import com.shawa.chatbotrag.entity.Message;
import com.shawa.chatbotrag.entity.Thread;
import com.shawa.chatbotrag.repository.MessageRepository;
import com.shawa.chatbotrag.repository.ThreadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageRepository messageRepository;
    private final ThreadRepository threadRepository;

    @PostMapping
    public Message createMessage(@RequestBody Message request) {
        request.setId(UUID.randomUUID());
        request.setCreatedAt(LocalDateTime.now());

        // findById sekarang langsung pakai UUID, tanpa toString()
        Thread thread = threadRepository.findById(request.getThread().getId())
            .orElseThrow(() -> new RuntimeException("Thread not found"));

        thread.setUpdatedAt(LocalDateTime.now());
        threadRepository.save(thread);

        return messageRepository.save(request);
    }

        @GetMapping
        public List<Message> getMessagesForThread(@RequestParam UUID threadId) {
            return messageRepository.findByThreadIdOrderByCreatedAtAsc(threadId);
        }

}
