package com.ramesh.knowledge.document;

import com.ramesh.knowledge.document.dto.ChunkView;
import com.ramesh.knowledge.document.dto.CreateDocumentRequest;
import com.ramesh.knowledge.document.dto.DocumentView;
import com.ramesh.knowledge.document.dto.KnowledgeBaseSummary;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

/**
 * REST API for managing the knowledge base.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentTextExtractor textExtractor;

    public DocumentController(DocumentService documentService, DocumentTextExtractor textExtractor) {
        this.documentService = documentService;
        this.textExtractor = textExtractor;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentView create(@Valid @RequestBody CreateDocumentRequest request) {
        return documentService.ingest(request.title(), request.content());
    }

    /**
     * Accepts a text, Markdown, PDF or DOCX file. Text is extracted first, so
     * the rest of the pipeline never sees a binary format.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentView upload(@RequestParam("file") MultipartFile file,
                               @RequestParam(value = "title", required = false) String title) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is empty");
        }
        String filename = file.getOriginalFilename() == null ? "upload.txt" : file.getOriginalFilename();

        String content;
        try {
            content = textExtractor.extract(filename, file.getBytes());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "could not read uploaded file", e);
        }

        String documentTitle = title == null || title.isBlank() ? baseName(filename) : title;
        String contentType = file.getContentType() == null || file.getContentType().isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : file.getContentType();
        return documentService.ingest(documentTitle, content,
                new DocumentSource(filename, contentType, file.getSize()));
    }

    @GetMapping
    public List<DocumentView> list() {
        return documentService.list();
    }

    @GetMapping("/summary")
    public KnowledgeBaseSummary summary() {
        return documentService.summary();
    }

    @GetMapping("/{id}")
    public DocumentView get(@PathVariable Long id) {
        return documentService.get(id);
    }

    @GetMapping("/{id}/chunks")
    public List<ChunkView> chunks(@PathVariable Long id) {
        return documentService.chunks(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        documentService.delete(id);
    }

    private String baseName(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot <= 0 ? filename : filename.substring(0, dot);
    }
}
