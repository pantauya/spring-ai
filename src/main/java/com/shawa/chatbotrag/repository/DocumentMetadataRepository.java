package com.shawa.chatbotrag.repository;

import com.shawa.chatbotrag.entity.DocumentMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
@Repository
public interface DocumentMetadataRepository extends JpaRepository<DocumentMetadata, String> {
    boolean existsByFilename(String filename);
    @Query("SELECT d.statusPeraturan FROM DocumentMetadata d WHERE d.filename = :filename")
    Optional<String> findStatusByFilename(@Param("filename") String filename);
}
