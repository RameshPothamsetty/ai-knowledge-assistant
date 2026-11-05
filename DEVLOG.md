# Devlog

## 2026-11-05

- Bootstrapped the Spring Boot 3.5 / Java 21 project with H2 and Postgres profiles, Docker and GitHub Actions CI.
- Implemented document ingestion: sentence-aware chunking with overlap, local hashing embeddings and a JSON vector codec.
- Added cosine similarity retrieval with configurable top-k, score threshold and source snippets.
- Built the RAG answer pipeline behind `LlmClient` (offline extractive fallback) with a citation-aware prompt template.
- Exposed `/api/documents` and `/api/ask`, a Thymeleaf UI, JSON error handling, and wrote 41 passing tests.
