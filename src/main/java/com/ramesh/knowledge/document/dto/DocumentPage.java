package com.ramesh.knowledge.document.dto;

import java.util.List;

/**
 * One page of documents, newest first. The page numbers are zero based, which
 * matches Spring Data's {@code Pageable} and keeps the API free of off-by-one
 * conversions.
 */
public record DocumentPage(
        List<DocumentView> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public DocumentPage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
