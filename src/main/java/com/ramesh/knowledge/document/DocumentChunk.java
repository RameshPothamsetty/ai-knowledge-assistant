package com.ramesh.knowledge.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One retrievable piece of a document together with its embedding. The vector
 * is stored as a JSON array of floats so the project works on H2 and Postgres
 * without extensions; a later roadmap task swaps this for pgvector.
 */
@Entity
@Table(name = "document_chunk",
        indexes = @Index(name = "idx_chunk_document", columnList = "document_id"))
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private KnowledgeDocument document;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String embedding;

    @Column(name = "token_count", nullable = false)
    private int tokenCount;

    protected DocumentChunk() {
        // Required by JPA.
    }

    public DocumentChunk(KnowledgeDocument document, int chunkIndex, String content, String embedding, int tokenCount) {
        this.document = document;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.embedding = embedding;
        this.tokenCount = tokenCount;
    }

    public Long getId() {
        return id;
    }

    public KnowledgeDocument getDocument() {
        return document;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public String getEmbedding() {
        return embedding;
    }

    public int getTokenCount() {
        return tokenCount;
    }
}
