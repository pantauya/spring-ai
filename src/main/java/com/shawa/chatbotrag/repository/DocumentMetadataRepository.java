package com.shawa.chatbotrag.repository;

import com.shawa.chatbotrag.entity.DocumentMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
@Repository
public interface DocumentMetadataRepository extends JpaRepository<DocumentMetadata, String> {
    boolean existsByFilename(String filename);
}
