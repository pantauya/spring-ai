package com.shawa.chatbotrag.controll;



import lombok.extern.slf4j.Slf4j;

import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
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
import com.shawa.chatbotrag.entity.DocumentMetadata;
import com.shawa.chatbotrag.entity.LLMRequest;
import com.shawa.chatbotrag.entity.LLMResponse;
import com.shawa.chatbotrag.entity.Message;
import com.shawa.chatbotrag.entity.PipelineLog;
import com.shawa.chatbotrag.repository.MessageRepository;
import com.shawa.chatbotrag.repository.ThreadRepository;
import com.shawa.chatbotrag.service.SimpleDocumentRanker;
import com.shawa.chatbotrag.service.QueryFocusedSummarizationService;
import com.shawa.chatbotrag.entity.Thread;
import com.shawa.chatbotrag.entity.Role;
import com.shawa.chatbotrag.repository.DocumentMetadataRepository;

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
    private final DocumentMetadataRepository metadataRepository;

    public ChatController(ChatClient.Builder builder, VectorStore vectorStore, OllamaEmbeddingModel embeddingModel, QueryFocusedSummarizationService documentCompressor, ChatModel chatModel, MessageRepository messageRepository, ThreadRepository threadRepository, DocumentMetadataRepository metadataRepository) { 

        this.queryExpander = MultiQueryExpander.builder()
            .chatClientBuilder(builder)  
            .numberOfQueries(2) 
            .build();

        this.documentRetriever = VectorStoreDocumentRetriever.builder()
                .similarityThreshold(0.3)
                .vectorStore(vectorStore)
                .topK(2)  
                .build();
        this.documentJoiner = new ConcatenationDocumentJoiner();
        this.documentRanker = new SimpleDocumentRanker(embeddingModel);
        this.documentCompressor = documentCompressor;
        PromptTemplate customPrompt = new PromptTemplate(
            "Here is the retrieved context from the document(s):\n\n" +
            "{context}\n\n" +
            "User Query:\n{query}\n\n" +
            "Instruction:\n" +
            "1. If the context is relevant to the query, answer in **clear and concise Indonesian**:\n" +
            "- Give a brief explanation based on the context.\n" +
            "- Mention the document source (file name).\n" +
            "- Mention the regulation status.\n" +
            "2. If the context does **not explicitly answer** the question but contains **relevant clues or related information**, provide the best possible answer you can reasonably infer, and still mention the source and regulation status.\\n" + 
            "3. If the context is **not relevant or unrelated** to the query, dont mention the document source or regulation status and respond with:\n" +
            "\"Maaf, tidak ditemukan dokumen yang relevan dengan pertanyaan Anda.\""
        );

        PromptTemplate emptyContextPrompt = new PromptTemplate(
            "Maaf, tidak ada informasi yang tersedia untuk menjawab pertanyaan Anda."
        );

        this.queryAugmenter = ContextualQueryAugmenter.builder()
            .promptTemplate(customPrompt)
            .emptyContextPromptTemplate(emptyContextPrompt)
            .allowEmptyContext(true)
            .build();
        this.messageRepository = messageRepository;
        this.threadRepository = threadRepository;
        this.metadataRepository = metadataRepository;
 
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
            List<Document> rankedDocuments = documentRanker.rank(userQuery, retrievedDocuments);

            log.info("Total ranked documents: {}", rankedDocuments.size());
            for (int i = 0; i < rankedDocuments.size(); i++) {
                log.info("Rank {}: ID = {}, Content = {}", i + 1, rankedDocuments.get(i).getId(), rankedDocuments.get(i).getContent());
            }

            List<Document> compressedDocuments = documentCompressor.compress(userQuery, rankedDocuments);
            log.info("Compressed documents: {}", compressedDocuments.size());
            for (int i = 0; i < compressedDocuments.size(); i++) {
                log.info("Compressed document {}: {}", i, compressedDocuments.get(i).getContent());
            }

            List<Document> documentsWithMetadata = compressedDocuments.stream()
                    .map(doc -> {
                        String content = doc.getContent();
                        String fileName = (String) doc.getMetadata().get("file_name");

                        // Ambil metadata dari DB
                        Optional<DocumentMetadata> metaOpt = metadataRepository.findById(fileName);
                        String status = metaOpt.map(DocumentMetadata::getStatusPeraturan).orElse("Status tidak tersedia");

                        //Tambahkan ke konten jawaban
                        String sourceMetadata = "Sumber: " + fileName + "\nStatus Peraturan: " + status;
                        String contentWithMetadata = content + "\n" + sourceMetadata;
                        // String sourceMetadata = "Sumber: " + fileName;  
                        // String contentWithMetadata = content + "\n" + sourceMetadata;
                        return new Document(contentWithMetadata, doc.getMetadata());
                    })
            .collect(Collectors.toList());  
            String augmentedQuery = queryAugmenter.augment(userQuery, documentsWithMetadata).toString();
            log.info("Augmented query: {}", augmentedQuery);
            log.info(" Sending query to ChatClient...");

        //    var chatResponse = chatClient.prompt()
        //            .user(augmentedQuery)
        //            .call()
        //            .content();
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