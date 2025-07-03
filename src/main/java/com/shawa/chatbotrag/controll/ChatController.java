package com.shawa.chatbotrag.controll;

import lombok.extern.slf4j.Slf4j;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.postretrieval.ranking.DocumentRanker;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.rag.retrieval.join.ConcatenationDocumentJoiner;
import org.springframework.ai.rag.retrieval.join.DocumentJoiner;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.rag.Query;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import com.shawa.chatbotrag.entity.LLMRequest;
import com.shawa.chatbotrag.entity.LLMResponse;
import com.shawa.chatbotrag.entity.Message;
import com.shawa.chatbotrag.entity.PipelineLog;
import com.shawa.chatbotrag.repository.MessageRepository;
import com.shawa.chatbotrag.repository.ThreadRepository;
import com.shawa.chatbotrag.service.SimpleDocumentRanker;
import com.shawa.chatbotrag.service.MetadataEnrichmentService;
import com.shawa.chatbotrag.service.QueryFocusedSummarizationService;
import com.shawa.chatbotrag.entity.Thread;
import com.shawa.chatbotrag.entity.Role;
//@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/llm")
@Slf4j
public class ChatController {
    private final ChatClient chatClient;
    private final QueryExpander queryExpander;
    private final DocumentJoiner documentJoiner;
    private final VectorStoreDocumentRetriever documentRetriever;
    private final DocumentRanker documentRanker;
    private final QueryFocusedSummarizationService documentCompressor;
    private final ContextualQueryAugmenter queryAugmenter;
    private final MessageRepository messageRepository;
    private final ThreadRepository threadRepository;
    private final MetadataEnrichmentService metadataEnrichmentService;


    public ChatController(ChatClient.Builder builder, VectorStore vectorStore, OllamaEmbeddingModel embeddingModel, QueryFocusedSummarizationService documentCompressor, ChatModel chatModel, MessageRepository messageRepository, ThreadRepository threadRepository,MetadataEnrichmentService metadataEnrichmentService) {

        this.queryExpander = MultiQueryExpander.builder()
            .chatClientBuilder(builder)  
            .numberOfQueries(2) 
            .build();

        this.documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(3)  
                .build();
        this.documentJoiner = new ConcatenationDocumentJoiner();
        this.documentRanker = new SimpleDocumentRanker(embeddingModel);
        this.documentCompressor = documentCompressor;
        PromptTemplate customPrompt = new PromptTemplate(
    "You are an AI assistant that answers questions based on Indonesian official documents.\n\n" +
    "Instructions:\n" +
    "- Think briefly to confirm what information directly answers the question based on the compressed context.\n" +
    "- For each reasoning step, if applicable, mention the supporting document and its status from metadata.\n" +
    "- Do not add new assumptions, inferences, or summarizations beyond what is provided.\n" +
    "- Avoid re-explaining the context; only focus on producing a clear and accurate answer.\n" +
    "- Then, infer the most accurate answer based only on those relevant parts.\n" +
    "- Always include all relevant bullet points if they directly answer the question.\n" +
    "- If multiple documents provide relevant information, mention all of them as long as the information is complementary and not contradictory.\n" +
    "- Prioritize higher-authority documents when multiple sources provide overlapping information, but do not ignore valid supporting details from others.\n" +
    "- Always verify which document most explicitly answers the question.\n" +
    "- Do not prioritize based on word count or frequency, but based on clarity and directness.\n" +
    "- Ignore generic structural references unless directly tied to the question.\n" +
    "- Use only terminology that appears in the documents.\n" +
    "- Only mention documents that are actually cited in the answer. Do not list all documents.'\n" +
    "- Do not include opinions, suggestions, or extra interpretations outside the documents.\n\n" +
    "Response Format:\n" +
    "1. Think Step-by-Step (in Bahasa Indonesia)\n" +
    "2. Final Answer (concise & clear, still in Bahasa Indonesia)\n" +
    "3. Sources:\n" +
    "   Judul Dokumen (Status: Status dari metadata)\n\n" +
    "=== Context ===\n{context}\n\n" +
    "=== Question ===\n{query}\n\n" +
    "Jawaban:"
);



        PromptTemplate emptyContextPrompt = new PromptTemplate(
            "Maaf, tidak ada informasi yang tersedia untuk menjawab pertanyaan Anda."
        );

        this.queryAugmenter = ContextualQueryAugmenter.builder()
            .promptTemplate(customPrompt)
            .emptyContextPromptTemplate(emptyContextPrompt)
            .allowEmptyContext(false)
            .build();
        this.messageRepository = messageRepository;
        this.threadRepository = threadRepository;
        this.metadataEnrichmentService = metadataEnrichmentService;
 
        System.out.println("Using embedding model: " + embeddingModel.getClass().getName());
       this.chatClient = ChatClient.builder(chatModel)
       .build();

    }
    @PostMapping(value = "/chat")
    public ResponseEntity<LLMResponse> chat(@RequestBody LLMRequest llmRequest) {
        try {

            log.info("User asked: {}", llmRequest.getQuery());
            Query userQuery = new Query(llmRequest.getQuery());
            UUID threadId = llmRequest.getThreadId(); // Sesuaikan dengan bentuk threadId kamu
            Thread thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new RuntimeException("Thread not found"));

            // Simpan pesan user
            Message userMessage = new Message();
            userMessage.setId(UUID.randomUUID());
            userMessage.setThread(thread);
            userMessage.setRole(Role.USER);
            userMessage.setContent(llmRequest.getQuery());
            userMessage.setCreatedAt(LocalDateTime.now());
            messageRepository.save(userMessage);
            log.info("Expanding query...");
            List<Query> expandedQueries = queryExpander.expand(userQuery);
            log.info("Expanded queries: {}", expandedQueries.size());
            for (int i = 0; i < expandedQueries.size(); i++) {
                log.info("Expanded query {}: {}", i, expandedQueries.get(i).text());
            }
            
            Map<Query, List<List<Document>>> documentsForQuery = new HashMap<>();

            for (Query expandedQuery : expandedQueries) {
                List<Document> documents = documentRetriever.retrieve(expandedQuery);
                log.info("Retrieved {} documents for query: {}", documents.size(), expandedQuery.text());
                for (int i = 0; i < documents.size(); i++) {
                    log.info("Document {}: ID = {}, Content = {}", i, documents.get(i).getId(), documents.get(i).getContent());
                }
                documentsForQuery.put(expandedQuery, Collections.singletonList(documents));
            }

                List<Document> retrievedDocuments = documentJoiner.join(documentsForQuery);

            log.info("Total unique retrieved documents/after joining docs: {}", retrievedDocuments.size());
            for (int i = 0; i < retrievedDocuments.size(); i++) {
                log.info("Document {}: ID = {}, Content = {}", i, retrievedDocuments.get(i).getId(), retrievedDocuments.get(i).getContent());
            }
            // Setelah ranking…
             metadataEnrichmentService.enrichStatus(retrievedDocuments);
            List<Document> rankedDocuments = documentRanker.rank(userQuery, retrievedDocuments);

            log.info("Total ranked documents: {}", rankedDocuments.size());
           for (Document doc : rankedDocuments) {
                log.info("Dokumen: {} | Status: {} | Skor: {}",
                    doc.getMetadata().get("file_name"),
                    doc.getMetadata().get("status_peraturan"),
                    doc.getMetadata().get("score")
                );
            }
            int maxDocsToCompress = 4;
                //double minScore = 0.75;
                List<Document> docsToCompress = rankedDocuments.stream()
                    // .filter(doc -> {
                    //     Object scoreObj = doc.getMetadata().get("score");
                    //     if (scoreObj instanceof Number) {
                    //         return ((Number) scoreObj).doubleValue() >= minScore;
                    //     }
                    //     return false; // jika tidak ada skor, skip
                    // })
                    .limit(maxDocsToCompress)
                    .collect(Collectors.toList());

            List<Document> compressedDocuments = documentCompressor.compress(userQuery, docsToCompress);

            log.info("Compressed documents: {}", compressedDocuments.size());
            for (int i = 0; i < compressedDocuments.size(); i++) {
                log.info("Compressed document {}: {}", i, compressedDocuments.get(i).getContent());
            }
            String augmentedQuery = queryAugmenter.augment(userQuery, compressedDocuments).toString();
            log.info("Augmented query: {}", augmentedQuery);
            log.info(" Sending query to ChatClient...");

        String rawResponse = chatClient.prompt()
            .user(augmentedQuery)
            .call()
            .content();

        Map<String, String> extracted = extractThoughtAndContent(rawResponse);
        String thought = extracted.get("thought");
        String content = extracted.get("content");


           log.info("Answer generated: {}", rawResponse);

           // Simpan pesan assistant
            Message assistantMessage = new Message();
            assistantMessage.setId(UUID.randomUUID());
            assistantMessage.setThread(thread);
            assistantMessage.setRole(Role.ASSISTANT);
            assistantMessage.setContent(content);
            assistantMessage.setCreatedAt(LocalDateTime.now());
            messageRepository.save(assistantMessage);

            // Update waktu terakhir thread
            thread.setUpdatedAt(LocalDateTime.now());
            threadRepository.save(thread);
           
           LLMResponse llmResponse = new LLMResponse(rawResponse, "success", threadId);
           PipelineLog pipelineLog = new PipelineLog(
                threadId,
                LocalDateTime.now().toString(),
                llmRequest.getQuery(),
                expandedQueries.stream().map(Query::text).toList(),
                retrievedDocuments,
                rankedDocuments,
                compressedDocuments,
                augmentedQuery,
                thought,
                content
            );
            logToJsonFile(pipelineLog);

           return ResponseEntity.ok(llmResponse);

          
        } catch (Exception e) {
            log.error("Error during chat processing: ", e);
            LLMResponse llmResponse = new LLMResponse("An error occurred while processing your request.", "error", llmRequest.getThreadId());
            return ResponseEntity.status(500).body(llmResponse);
        }
    }

    private Map<String, String> extractThoughtAndContent(String rawResponse) {
        Map<String, String> result = new HashMap<>();
        String thought = "";
        String content = rawResponse;
    
        Pattern pattern = Pattern.compile("<think>([\\s\\S]*?)</think>", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(rawResponse);
        if (matcher.find()) {
            thought = matcher.group(1).trim();
            content = rawResponse.replace(matcher.group(0), "").trim();
        }
    
        result.put("thought", thought);
        result.put("content", content);
        return result;
    }

    private void logToJsonFile(PipelineLog logData) {
    try {
        ObjectMapper objectMapper = new ObjectMapper();
        Files.createDirectories(Paths.get("logs")); // pastikan folder logs ada
        String filename = "logs/chatlog-" + logData.getThreadId() + "-" + System.currentTimeMillis() + ".json";
        objectMapper.writerWithDefaultPrettyPrinter()
            .writeValue(Paths.get(filename).toFile(), logData);
    } catch (Exception e) {
        log.error("Gagal menulis file JSON log", e);
    }
}


}