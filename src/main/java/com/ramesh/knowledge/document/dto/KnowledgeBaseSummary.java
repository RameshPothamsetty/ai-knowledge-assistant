package com.ramesh.knowledge.document.dto;

/**
 * Knowledge-base totals. {@code tokens} is the number of word tokens stored
 * across every chunk, which is what the chunk table counts per row.
 */
public record KnowledgeBaseSummary(long documents, long chunks, long tokens) {
}
