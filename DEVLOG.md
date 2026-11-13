# Devlog

## 2026-11-13

- Picked up Phase 3 at task 29: `POST /api/ask` now takes an optional `documentId` that narrows retrieval to one document, fetched with its own eager query so a scoped question never scans the corpus; validated as positive and covered in unit and MockMvc tests, 63 tests passing.
- Reworked ranking as a blend of cosine similarity and Okapi BM25 (`app.rag.hybrid-weight`, default 0.75). Cosine stays the relevance gate, so `min-score` keeps its meaning, while BM25's inverse document frequency lifts chunks that match rare identifiers. Tokenisation moved into a shared `Terms` helper so the embedder and the keyword scorer agree on stems and stop words; 81 tests passing.
- Cached decoded chunk vectors in a bounded map, invalidated per document on delete and wholesale on reindex, so repeated questions stop re-parsing the stored JSON.
- Added `POST /api/documents/reindex`, which re-chunks and re-embeds every stored document while keeping ids and metadata.
- Dropped repeated boilerplate chunks at chunking time, ignoring case and punctuation, so page footers and legal notices cannot occupy several retrieval slots.
- Added `GET /api/documents/page` (`page`, `size`, total counts) for knowledge bases that should not be listed in one response.
- Made answer style requestable: `length` (`short`/`medium`/`long`) and `tone` (`neutral`/`friendly`/`technical`) become prompt instructions, and the offline extractive model honours the sentence budget so the setting is observable without a hosted model.
- Added in-memory conversation memory keyed by `conversationId`: the last three turns are rendered into the prompt, six are retained per conversation, and the store caps itself at 500 conversations.
- Guarded against prompt injection from uploaded documents by stripping instruction-like lines before rendering passages, plus a system-prompt rule that passages are data; case-insensitive enum binding and a `malformed request body` handler keep bad input in the usual error shape.
- Added `POST /api/ask/stream` (SSE: sources, token, done) on a virtual-thread executor, and `POST /api/feedback` with `GET /api/feedback/summary` keyed by the new `answerId`; 89 tests passing.
- Published the API with springdoc: generated document at `/v3/api-docs`, Swagger UI at `/swagger-ui.html`, and a small `OpenApiConfig` for title, version and description.
- Gave every request a correlation id: an accepted `X-Request-Id` is reused, otherwise a UUID is generated, echoed in the header, logged through the pattern and included in error bodies.
- Instrumented the answer pipeline with Micrometer timers (`rag.retrieval`, `rag.generation`) and an outcome counter (`rag.answers`), exposed under `/actuator/metrics`.
- Limited `/api/ask` and `/api/ask/stream` to 60 requests per client address per minute with a sliding window, returning the standard JSON error body plus `Retry-After`; retrieval and document endpoints are unaffected. 94 tests passing.
- Added `scripts/load-demo-data.sh` and two more sample documents, so a fresh instance can be filled and questioned in one command.
- Indexed `knowledge_document.created_at`, which both the newest-first listing and pagination order by (chunks were already indexed by document).
- Wrote `docs/architecture.md` with a component diagram and request flow, and `docs/blog-post.md` on the offline-first design decisions.
- Added an unreachable-API banner to the SPA with a retry action, so a stopped backend reads as a message rather than an empty library; rebuilt the bundle into `src/main/resources/static`.

- Finished Phase 2: uploads now accept `.pdf` (Apache PDFBox) and `.docx` (Apache POI) alongside text and Markdown, with extraction kept in `DocumentTextExtractor` so ingestion stays format-agnostic.
- Enforced one document size limit for both pasted text and extracted files, returning `413` with the actual character count; unsupported formats return `415` and unreadable or scanned files return `422` with a message that explains what to fix.
- Recorded source filename, MIME type and byte size with every document, and added `GET /api/documents/summary` for document, chunk and token totals; 59 tests passing.

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
