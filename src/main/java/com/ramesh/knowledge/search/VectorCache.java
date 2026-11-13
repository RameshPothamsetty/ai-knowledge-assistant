package com.ramesh.knowledge.search;

import com.ramesh.knowledge.config.RagProperties;
import com.ramesh.knowledge.document.DocumentChunk;
import com.ramesh.knowledge.embedding.EmbeddingCodec;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps decoded chunk vectors in memory so repeated questions do not re-parse
 * the stored JSON for every chunk.
 *
 * <p>Stored embeddings never change once written: ingestion creates new rows and
 * reindexing replaces them, so a chunk id identifies a fixed vector. The cache is
 * therefore invalidated explicitly — per document on delete, wholesale on
 * reindex — rather than by tracking content hashes. The map is bounded, and when
 * it is full it is dropped in one go instead of maintaining a recency list.
 */
@Component
public class VectorCache {

    private final int maxEntries;
    private final Map<Long, Entry> entries = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public VectorCache(RagProperties properties) {
        this(properties.vectorCacheSize());
    }

    public VectorCache(int maxEntries) {
        this.maxEntries = Math.max(1, maxEntries);
    }

    public float[] vector(DocumentChunk chunk) {
        Entry entry = entries.get(chunk.getId());
        if (entry != null) {
            return entry.vector();
        }
        float[] decoded = EmbeddingCodec.decode(chunk.getEmbedding());
        if (entries.size() >= maxEntries) {
            entries.clear();
        }
        entries.put(chunk.getId(), new Entry(chunk.getDocument().getId(), decoded));
        return decoded;
    }

    public void evictDocument(Long documentId) {
        if (documentId == null) {
            return;
        }
        entries.values().removeIf(entry -> documentId.equals(entry.documentId()));
    }

    public void clear() {
        entries.clear();
    }

    int size() {
        return entries.size();
    }

    private record Entry(Long documentId, float[] vector) {
    }
}
