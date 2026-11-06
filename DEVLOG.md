# Devlog

## 2026-11-06

- Rejected duplicate documents at ingestion: a matching title and content now returns `409 Conflict` before any chunk is embedded, with unit and MockMvc coverage; 44 tests passing.
- Completed the UI phase: the library previews a document's stored chunks through the new `GET /api/documents/{id}/chunks`, and an accessibility pass added drawer focus and Escape handling, inert off-canvas sidebar content, higher contrast for small muted text and reduced-motion support; 47 tests passing.

## 2026-11-05

- Bootstrapped the Spring Boot 3.5 / Java 21 project with H2 and Postgres profiles, Docker and GitHub Actions CI.
- Implemented document ingestion: sentence-aware chunking with overlap, local hashing embeddings and a JSON vector codec.
- Added cosine similarity retrieval with configurable top-k, score threshold and source snippets.
- Built the RAG answer pipeline behind `LlmClient` (offline extractive fallback) with a citation-aware prompt template.
- Exposed `/api/documents` and `/api/ask`, a Thymeleaf UI, JSON error handling, and wrote 41 passing tests.
- Replaced the Thymeleaf page with a React 19 + Vite SPA: document library with search and drag-and-drop upload, chat thread with citation chips, source inspector, persisted history, sanitised Markdown answers and a theme toggle.
- Added `GET /api/meta` so the UI reads the real embedding model and top-k limits, removed the unused Thymeleaf dependency, and taught CI and Docker to build the UI into the jar.
- Added issue forms, a pull request template and Dependabot; 42 tests passing.
