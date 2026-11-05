package com.ramesh.knowledge.document;

import com.ramesh.knowledge.document.dto.DocumentView;
import com.ramesh.knowledge.embedding.EmbeddingCodec;
import com.ramesh.knowledge.embedding.HashingEmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(
                documentRepository,
                chunkRepository,
                new ChunkingService(120, 30),
                new HashingEmbeddingService(64));
    }

    @Test
    @SuppressWarnings("unchecked")
    void chunksAndEmbedsEveryChunkDuringIngestion() {
        when(documentRepository.save(any(KnowledgeDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chunkRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentView view = documentService.ingest(
                "Spring notes",
                "Spring Boot provides auto configuration. ".repeat(12),
                "spring-notes.txt");

        assertThat(view.title()).isEqualTo("Spring notes");
        assertThat(view.sourceName()).isEqualTo("spring-notes.txt");
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
    void deletesChunksBeforeTheDocument() {
        when(documentRepository.existsById(3L)).thenReturn(true);

        documentService.delete(3L);

        verify(chunkRepository).deleteByDocumentId(3L);
        verify(documentRepository).deleteById(3L);
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
        KnowledgeDocument first = new KnowledgeDocument("First", "content", null, Instant.now());
        KnowledgeDocument second = new KnowledgeDocument("Second", "content", null, Instant.now());
        when(documentRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(second, first));

        assertThat(documentService.list()).extracting(DocumentView::title)
                .containsExactly("Second", "First");
    }

    @Test
    void getThrowsForUnknownDocument() {
        when(documentRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.get(5L))
                .isInstanceOf(NoSuchElementException.class);
    }
}
