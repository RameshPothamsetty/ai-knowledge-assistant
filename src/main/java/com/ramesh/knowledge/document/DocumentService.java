package com.ramesh.knowledge.document;

import com.ramesh.knowledge.document.dto.DocumentView;
import com.ramesh.knowledge.embedding.EmbeddingCodec;
import com.ramesh.knowledge.embedding.EmbeddingService;
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

    private final KnowledgeDocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;

    public DocumentService(KnowledgeDocumentRepository documentRepository,
                           DocumentChunkRepository chunkRepository,
                           ChunkingService chunkingService,
                           EmbeddingService embeddingService) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
    }

    @Transactional
    public DocumentView ingest(String title, String content, String sourceName) {
        String cleanTitle = requireText(title, "title");
        String cleanContent = requireText(content, "content");

        if (documentRepository.existsByTitleAndContent(cleanTitle, cleanContent)) {
            throw new DuplicateDocumentException(cleanTitle);
        }

        List<String> chunks = chunkingService.chunk(cleanContent);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("content does not contain usable text");
        }

        KnowledgeDocument document = documentRepository.save(
                new KnowledgeDocument(cleanTitle, cleanContent, blankToNull(sourceName), Instant.now()));

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
