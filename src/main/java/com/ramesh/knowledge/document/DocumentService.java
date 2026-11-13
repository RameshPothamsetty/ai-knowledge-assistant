package com.ramesh.knowledge.document;

import com.ramesh.knowledge.config.RagProperties;
import com.ramesh.knowledge.document.dto.ChunkView;
import com.ramesh.knowledge.document.dto.DocumentPage;
import com.ramesh.knowledge.document.dto.DocumentView;
import com.ramesh.knowledge.document.dto.KnowledgeBaseSummary;
import com.ramesh.knowledge.document.dto.ReindexResult;
import com.ramesh.knowledge.embedding.EmbeddingCodec;
import com.ramesh.knowledge.embedding.EmbeddingService;
import com.ramesh.knowledge.search.VectorCache;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

/**
 * Ingestion pipeline: validate the text, split it into chunks, embed every
 * chunk and persist the result. Documents that produce no chunks are rejected
 * so the knowledge base never contains unusable rows.
 */
@Service
public class DocumentService {

    private static final Pattern WORD = Pattern.compile("\\S+");

    /** Upper bound for a single page of the document list. */
    private static final int MAX_PAGE_SIZE = 100;

    private final KnowledgeDocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final RagProperties properties;
    private final VectorCache vectorCache;

    public DocumentService(KnowledgeDocumentRepository documentRepository,
                           DocumentChunkRepository chunkRepository,
                           ChunkingService chunkingService,
                           EmbeddingService embeddingService,
                           RagProperties properties,
                           VectorCache vectorCache) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
        this.properties = properties;
        this.vectorCache = vectorCache;
    }

    public DocumentView ingest(String title, String content) {
        return ingest(title, content, DocumentSource.pasted(content == null ? "" : content));
    }

    @Transactional
    public DocumentView ingest(String title, String content, DocumentSource source) {
        String cleanTitle = requireText(title, "title");
        String cleanContent = requireText(content, "content");

        int limit = properties.maxDocumentCharacters();
        if (cleanContent.length() > limit) {
            throw new DocumentTooLargeException(cleanContent.length(), limit);
        }

        if (documentRepository.existsByTitleAndContent(cleanTitle, cleanContent)) {
            throw new DuplicateDocumentException(cleanTitle);
        }

        List<String> chunks = chunkingService.chunk(cleanContent);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("content does not contain usable text");
        }

        KnowledgeDocument document = documentRepository.save(new KnowledgeDocument(
                cleanTitle,
                cleanContent,
                source == null ? null : blankToNull(source.filename()),
                source == null ? null : blankToNull(source.contentType()),
                source == null ? 0 : source.sizeBytes(),
                Instant.now()));

        List<DocumentChunk> entities = buildChunks(document, chunks);
        chunkRepository.saveAll(entities);

        document.setChunkCount(entities.size());
        documentRepository.save(document);
        return DocumentView.from(document);
    }

    @Transactional(readOnly = true)
    public List<DocumentView> list() {
        return documentRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(DocumentView::from)
                .toList();
    }

    /**
     * Newest-first page of documents. The unpaginated list stays for the current
     * UI; this endpoint is what a large knowledge base should use.
     */
    @Transactional(readOnly = true)
    public DocumentPage page(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        Page<KnowledgeDocument> result = documentRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new DocumentPage(
                result.getContent().stream().map(DocumentView::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    /**
     * Rebuilds every chunk from the stored document text, which is what a
     * change to the embedding model or chunking settings requires. Documents keep
     * their ids and metadata; only their chunks are replaced.
     */
    @Transactional
    public ReindexResult reindex() {
        long startedAt = System.nanoTime();
        List<KnowledgeDocument> documents = documentRepository.findAll();
        long chunks = 0;
        for (KnowledgeDocument document : documents) {
            chunkRepository.deleteByDocumentId(document.getId());
            List<DocumentChunk> entities = buildChunks(document, chunkingService.chunk(document.getContent()));
            chunkRepository.saveAll(entities);
            document.setChunkCount(entities.size());
            documentRepository.save(document);
            chunks += entities.size();
        }
        vectorCache.clear();
        return new ReindexResult(documents.size(), chunks, (System.nanoTime() - startedAt) / 1_000_000);
    }

    /**
     * Totals for the whole knowledge base, cheap enough to show in a header.
     * "Tokens" counts the words stored across chunks, matching what the chunk
     * table records per row.
     */
    @Transactional(readOnly = true)
    public KnowledgeBaseSummary summary() {
        return new KnowledgeBaseSummary(
                documentRepository.count(),
                chunkRepository.count(),
                chunkRepository.sumTokenCount());
    }

    /**
     * Chunks in their original order, used by the UI to show how a document was
     * split before it is retrieved.
     */
    @Transactional(readOnly = true)
    public List<ChunkView> chunks(Long id) {
        if (!documentRepository.existsById(id)) {
            throw new NoSuchElementException("document " + id + " not found");
        }
        return chunkRepository.findByDocumentIdOrderByChunkIndexAsc(id).stream()
                .map(ChunkView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentView get(Long id) {
        return documentRepository.findById(id)
                .map(DocumentView::from)
                .orElseThrow(() -> new NoSuchElementException("document " + id + " not found"));
    }

    @Transactional
    public void delete(Long id) {
        if (!documentRepository.existsById(id)) {
            throw new NoSuchElementException("document " + id + " not found");
        }
        chunkRepository.deleteByDocumentId(id);
        documentRepository.deleteById(id);
        vectorCache.evictDocument(id);
    }

    private List<DocumentChunk> buildChunks(KnowledgeDocument document, List<String> chunks) {
        List<DocumentChunk> entities = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            String chunk = chunks.get(index);
            entities.add(new DocumentChunk(
                    document,
                    index,
                    chunk,
                    EmbeddingCodec.encode(embeddingService.embed(chunk)),
                    wordCount(chunk)
            ));
        }
        return entities;
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int wordCount(String chunk) {
        int count = 0;
        var matcher = WORD.matcher(chunk);
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
