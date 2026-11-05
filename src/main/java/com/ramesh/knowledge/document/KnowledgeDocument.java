package com.ramesh.knowledge.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A document a user uploaded to the knowledge base. The raw text is kept so
 * documents can be re-chunked or re-embedded later, and the chunk count lets
 * the API report ingestion results without loading every chunk.
 */
@Entity
@Table(name = "knowledge_document")
public class KnowledgeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "source_name", length = 255)
    private String sourceName;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected KnowledgeDocument() {
        // Required by JPA.
    }

    public KnowledgeDocument(String title, String content, String sourceName, Instant createdAt) {
        this.title = title;
        this.content = content;
        this.sourceName = sourceName;
        this.createdAt = createdAt;
        this.chunkCount = 0;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getSourceName() {
        return sourceName;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }
}
