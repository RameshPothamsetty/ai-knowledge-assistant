package com.ramesh.knowledge.document;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    /**
     * Loads every chunk with its parent document in a single query, which keeps
     * retrieval free of lazy-loading surprises when documents are mapped to API
     * responses.
     */
    @EntityGraph(attributePaths = "document")
    @Query("select c from DocumentChunk c")
    List<DocumentChunk> findAllWithDocument();

    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(Long documentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from DocumentChunk c where c.document.id = :documentId")
    void deleteByDocumentId(@Param("documentId") Long documentId);
}
