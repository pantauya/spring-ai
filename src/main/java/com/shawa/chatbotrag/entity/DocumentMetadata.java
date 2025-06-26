package com.shawa.chatbotrag.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "document_metadata")
public class DocumentMetadata {

    @Id
    @Column(unique = true)
    private String filename;
    private String jenis;
    private String nomor;
    private String tahun;
    private String statusPeraturan; // Diisi manual
}
