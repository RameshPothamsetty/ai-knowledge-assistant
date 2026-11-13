package com.ramesh.knowledge.document;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Turns an uploaded file into plain text. Format handling lives here rather
 * than in the controller so ingestion stays the same for every source and new
 * parsers have exactly one place to go.
 */
@Component
public class DocumentTextExtractor {

    public static final Set<String> SUPPORTED_EXTENSIONS = Set.of("txt", "md", "markdown", "pdf", "docx");

    private static final Set<String> PLAIN_TEXT_EXTENSIONS = Set.of("txt", "md", "markdown");

    public String extract(String filename, byte[] bytes) {
        String extension = extensionOf(filename);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new UnsupportedDocumentTypeException(
                    "only .txt, .md, .pdf and .docx files are supported, got "
                            + (extension.isEmpty() ? "a file without an extension" : "." + extension));
        }

        String text;
        try {
            if (PLAIN_TEXT_EXTENSIONS.contains(extension)) {
                text = new String(bytes, StandardCharsets.UTF_8);
            } else if ("pdf".equals(extension)) {
                text = fromPdf(bytes);
            } else {
                text = fromDocx(bytes);
            }
        } catch (IOException | RuntimeException e) {
            throw new DocumentExtractionException("could not read text from " + filename + ": " + e.getMessage(), e);
        }

        String normalised = text.replace("\u0000", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\f", "\n")
                .trim();
        if (normalised.isBlank()) {
            throw new DocumentExtractionException(
                    "pdf".equals(extension)
                            ? filename + " has no text layer; scanned PDFs need OCR, which is not supported"
                            : filename + " contains no extractable text");
        }
        return normalised;
    }

    private String fromPdf(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String fromDocx(byte[] bytes) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
