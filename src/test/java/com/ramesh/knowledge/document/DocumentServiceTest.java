package com.ramesh.knowledge.document;

import com.ramesh.knowledge.config.RagProperties;
import com.ramesh.knowledge.document.dto.ChunkView;
import com.ramesh.knowledge.document.dto.DocumentPage;
import com.ramesh.knowledge.document.dto.DocumentView;
import com.ramesh.knowledge.document.dto.KnowledgeBaseSummary;
import com.ramesh.knowledge.document.dto.ReindexResult;
import com.ramesh.knowledge.embedding.EmbeddingCodec;
import com.ramesh.knowledge.embedding.HashingEmbeddingService;
import com.ramesh.knowledge.search.VectorCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private KnowledgeDocumentRepository documentRepository;

    @Mock
    private DocumentChunkRepository chunkRepository;

    @Mock
    private VectorCache vectorCache;

    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(
                documentRepository,
                chunkRepository,
                new ChunkingService(120, 30),
                new HashingEmbeddingService(64),
                new RagProperties(800, 120, 4, 20, 256, 0.05, 200_000, 0.75, 4096),
                vectorCache);
    }

    @Test
    @SuppressWarnings("unchecked")
    void chunksAndEmbedsEveryChunkDuringIngestion() {
        when(documentRepository.save(any(KnowledgeDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chunkRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentView view = documentService.ingest(
                "Spring notes",
                "Spring Boot provides auto configuration. ".repeat(12),
                new DocumentSource("spring-notes.txt", "text/plain", 480));

        assertThat(view.title()).isEqualTo("Spring notes");
        assertThat(view.sourceName()).isEqualTo("spring-notes.txt");
        assertThat(view.contentType()).isEqualTo("text/plain");
        assertThat(view.sizeBytes()).isEqualTo(480);
        assertThat(view.chunkCount()).isGreaterThan(1);

        ArgumentCaptor<List<DocumentChunk>> chunksCaptor = ArgumentCaptor.forClass(List.class);
        verify(chunkRepository).saveAll(chunksCaptor.capture());
        List<DocumentChunk> chunks = chunksCaptor.getValue();

        assertThat(chunks).hasSize(view.chunkCount());
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.getContent()).isNotBlank();
            assertThat(chunk.getTokenCount()).isPositive();
            assertThat(EmbeddingCodec.decode(chunk.getEmbedding())).hasSize(64);
        });
        assertThat(chunks).extracting(DocumentChunk::getChunkIndex)
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, chunks.size()).boxed().toList());
    }

    @Test
    void rejectsBlankContentWithoutTouchingTheDatabase() {
        assertThatThrownBy(() -> documentService.ingest("Title", "   ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content");

        verifyNoInteractions(documentRepository, chunkRepository);
    }

    @Test
    void rejectsBlankTitleWithoutTouchingTheDatabase() {
        assertThatThrownBy(() -> documentService.ingest("  ", "some content", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");

        verifyNoInteractions(documentRepository, chunkRepository);
    }

    @Test
    void rejectsDuplicateTitleAndContentBeforeChunking() {
        when(documentRepository.existsByTitleAndContent("Spring notes", "same body")).thenReturn(true);

        assertThatThrownBy(() -> documentService.ingest("Spring notes", "same body", null))
                .isInstanceOf(DuplicateDocumentException.class)
                .hasMessageContaining("already exists");

        verifyNoInteractions(chunkRepository);
        verify(documentRepository, never()).save(any(KnowledgeDocument.class));
    }

    @Test
    void rejectsDocumentsAboveTheCharacterLimit() {
        DocumentService limited = new DocumentService(
                documentRepository,
                chunkRepository,
                new ChunkingService(120, 30),
                new HashingEmbeddingService(64),
                new RagProperties(800, 120, 4, 20, 256, 0.05, 50, 0.75, 4096),
                vectorCache);

        assertThatThrownBy(() -> limited.ingest("Big", "x".repeat(51)))
                .isInstanceOf(DocumentTooLargeException.class)
                .hasMessageContaining("limit is 50");

        verifyNoInteractions(documentRepository, chunkRepository);
    }

    @Test
    void summarisesTotalsFromTheDatabase() {
        when(documentRepository.count()).thenReturn(2L);
        when(chunkRepository.count()).thenReturn(5L);
        when(chunkRepository.sumTokenCount()).thenReturn(320L);

        assertThat(documentService.summary()).isEqualTo(new KnowledgeBaseSummary(2, 5, 320));
    }

    @Test
    void deletesChunksBeforeTheDocument() {
        when(documentRepository.existsById(3L)).thenReturn(true);

        documentService.delete(3L);

        verify(chunkRepository).deleteByDocumentId(3L);
        verify(documentRepository).deleteById(3L);
        verify(vectorCache).evictDocument(3L);
    }

    @Test
    void reindexesEveryDocumentFromItsStoredText() {
        KnowledgeDocument document = new KnowledgeDocument(
                "Notes", "Spring Boot provides auto configuration. ".repeat(12), null, "text/plain", 7, Instant.now());
        ReflectionTestUtils.setField(document, "id", 7L);
        when(documentRepository.findAll()).thenReturn(List.of(document));
        when(documentRepository.save(any(KnowledgeDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chunkRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        ReindexResult result = documentService.reindex();

        assertThat(result.documents()).isEqualTo(1);
        assertThat(result.chunks()).isGreaterThan(1);
        assertThat(result.durationMs()).isNotNegative();
        assertThat(document.getChunkCount()).isEqualTo((int) result.chunks());
        verify(chunkRepository).deleteByDocumentId(7L);
        verify(vectorCache).clear();
    }

    @Test
    void pagesDocumentsNewestFirst() {
        KnowledgeDocument document = new KnowledgeDocument("First", "content", null, "text/plain", 7, Instant.now());
        Pageable pageable = PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<KnowledgeDocument> page = new PageImpl<>(List.of(document), pageable, 5);
        when(documentRepository.findAll(pageable)).thenReturn(page);

        DocumentPage result = documentService.page(1, 2);

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(5);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.items()).extracting(DocumentView::title).containsExactly("First");
    }

    @Test
    void rejectsInvalidPageRequestsBeforeQuerying() {
        assertThatThrownBy(() -> documentService.page(-1, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page");
        assertThatThrownBy(() -> documentService.page(0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
        assertThatThrownBy(() -> documentService.page(0, 101))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(documentRepository);
    }

    @Test
    void deletingAnUnknownDocumentFails() {
        when(documentRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> documentService.delete(99L))
                .isInstanceOf(NoSuchElementException.class);

        verify(chunkRepository, never()).deleteByDocumentId(any());
        verify(documentRepository, never()).deleteById(any());
    }

    @Test
    void listsDocumentsNewestFirstAsReportedByTheRepository() {
        KnowledgeDocument first = new KnowledgeDocument("First", "content", null, "text/plain", 7, Instant.now());
        KnowledgeDocument second = new KnowledgeDocument("Second", "content", null, "text/plain", 7, Instant.now());
        when(documentRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(second, first));

        assertThat(documentService.list()).extracting(DocumentView::title)
                .containsExactly("Second", "First");
    }

    @Test
    void returnsChunksInStoredOrder() {
        KnowledgeDocument document = new KnowledgeDocument("Notes", "content", null, "text/plain", 7, Instant.now());
        DocumentChunk first = new DocumentChunk(document, 0, "first", "[]", 1);
        DocumentChunk second = new DocumentChunk(document, 1, "second", "[]", 2);
        when(documentRepository.existsById(7L)).thenReturn(true);
        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(7L)).thenReturn(List.of(first, second));

        assertThat(documentService.chunks(7L))
                .extracting(ChunkView::chunkIndex)
                .containsExactly(0, 1);
    }

    @Test
    void listingChunksOfAnUnknownDocumentFails() {
        when(documentRepository.existsById(42L)).thenReturn(false);

        assertThatThrownBy(() -> documentService.chunks(42L))
                .isInstanceOf(NoSuchElementException.class);

        verifyNoInteractions(chunkRepository);
    }

    @Test
    void getThrowsForUnknownDocument() {
        when(documentRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.get(5L))
                .isInstanceOf(NoSuchElementException.class);
    }
}
