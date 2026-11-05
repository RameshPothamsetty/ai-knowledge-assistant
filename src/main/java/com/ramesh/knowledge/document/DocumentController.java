package com.ramesh.knowledge.document;

import com.ramesh.knowledge.document.dto.CreateDocumentRequest;
import com.ramesh.knowledge.document.dto.DocumentView;
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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * REST API for managing the knowledge base.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("txt", "md", "markdown");

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentView create(@Valid @RequestBody CreateDocumentRequest request) {
        return documentService.ingest(request.title(), request.content(), request.sourceName());
    }

    /**
     * Accepts a plain text or Markdown file. Other formats need a parser and are
     * tracked on the roadmap.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentView upload(@RequestParam("file") MultipartFile file,
                               @RequestParam(value = "title", required = false) String title) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is empty");
        }
        String filename = file.getOriginalFilename() == null ? "upload.txt" : file.getOriginalFilename();
        String extension = extensionOf(filename);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "only .txt and .md files are supported");
        }

        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "could not read uploaded file", e);
        }

        String documentTitle = title == null || title.isBlank() ? baseName(filename) : title;
        return documentService.ingest(documentTitle, content, filename);
    }

    @GetMapping
    public List<DocumentView> list() {
        return documentService.list();
    }

    @GetMapping("/{id}")
    public DocumentView get(@PathVariable Long id) {
        return documentService.get(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        documentService.delete(id);
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String baseName(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot <= 0 ? filename : filename.substring(0, dot);
    }
}
