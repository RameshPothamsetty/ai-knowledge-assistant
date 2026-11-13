package com.ramesh.knowledge.support;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Builds the binary formats in tests, so extraction is exercised against real
 * files instead of mocked parser output. Text is ASCII so the standard fonts
 * can encode it.
 */
public final class DocumentFixtures {

    private DocumentFixtures() {
    }

    public static byte[] pdf(String text) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 700);
                stream.showText(text);
                stream.endText();
            }
            return toBytes(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A PDF with a page but no text layer, like a scan without OCR.
     */
    public static byte[] pdfWithoutText() {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            return toBytes(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] docx(String text) {
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText(text);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] toBytes(PDDocument document) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
