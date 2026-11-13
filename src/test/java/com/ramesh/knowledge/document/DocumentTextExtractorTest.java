package com.ramesh.knowledge.document;

import com.ramesh.knowledge.support.DocumentFixtures;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentTextExtractorTest {

    private final DocumentTextExtractor extractor = new DocumentTextExtractor();

    @Test
    void readsPlainTextAndMarkdownAsUtf8() {
        byte[] bytes = "Über Spring Boot".getBytes(StandardCharsets.UTF_8);

        assertThat(extractor.extract("notes.txt", bytes)).isEqualTo("Über Spring Boot");
        assertThat(extractor.extract("notes.md", bytes)).isEqualTo("Über Spring Boot");
        assertThat(extractor.extract("notes.markdown", bytes)).isEqualTo("Über Spring Boot");
    }

    @Test
    void normalisesLineEndingsAndTrims() {
        assertThat(extractor.extract("notes.txt", "  a\r\nb\rc  ".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("a\nb\nc");
    }

    @Test
    void extractsTextFromPdf() {
        byte[] pdf = DocumentFixtures.pdf("Retrieval augmented generation grounds answers.");

        assertThat(extractor.extract("paper.pdf", pdf))
                .contains("Retrieval augmented generation grounds answers.");
    }

    @Test
    void extractsTextFromDocx() {
        byte[] docx = DocumentFixtures.docx("Chunking keeps sentences together.");

        assertThat(extractor.extract("report.docx", docx)).contains("Chunking keeps sentences together.");
    }

    @Test
    void rejectsUnsupportedExtensions() {
        assertThatThrownBy(() -> extractor.extract("legacy.rtf", new byte[]{1, 2, 3}))
                .isInstanceOf(UnsupportedDocumentTypeException.class)
                .hasMessageContaining(".rtf");

        assertThatThrownBy(() -> extractor.extract("no-extension", new byte[]{1, 2, 3}))
                .isInstanceOf(UnsupportedDocumentTypeException.class)
                .hasMessageContaining("without an extension");
    }

    @Test
    void rejectsCorruptPdfAndDocx() {
        assertThatThrownBy(() -> extractor.extract("broken.pdf", new byte[]{1, 2, 3}))
                .isInstanceOf(DocumentExtractionException.class)
                .hasMessageContaining("broken.pdf");

        assertThatThrownBy(() -> extractor.extract("broken.docx", new byte[]{1, 2, 3}))
                .isInstanceOf(DocumentExtractionException.class)
                .hasMessageContaining("broken.docx");
    }

    @Test
    void explainsWhenAPdfHasNoTextLayer() {
        assertThatThrownBy(() -> extractor.extract("scan.pdf", DocumentFixtures.pdfWithoutText()))
                .isInstanceOf(DocumentExtractionException.class)
                .hasMessageContaining("no text layer");
    }

    @Test
    void rejectsEmptyPlainText() {
        assertThatThrownBy(() -> extractor.extract("empty.txt", "   \n  ".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(DocumentExtractionException.class)
                .hasMessageContaining("no extractable text");
    }
}
