# Roadmap

Small, ordered tasks. One task is meant to be completable in under ten minutes; tick it only when the code, tests and docs are done.

Legend: `[x]` done, `[ ]` next.

## Phase 1 — Foundations

- [x] 1. Maven project with Spring Boot 3.5 on Java 21
- [x] 2. Package structure: document, embedding, search, chat, web
- [x] 3. `.gitignore` covering build output, IDE files and secrets
- [x] 4. `.env.example` documenting every environment variable
- [x] 5. H2 in-memory dev profile plus a Postgres profile
- [x] 6. Actuator health endpoint
- [x] 7. GitHub Actions CI running `mvn verify` on push and pull request
- [x] 8. Multi-stage Dockerfile and docker-compose with Postgres

## Phase 2 — Document ingestion

- [x] 9. `KnowledgeDocument` entity and repository
- [x] 10. `DocumentChunk` entity storing text, order, token count and embedding
- [x] 11. Sentence-aware chunking with configurable size and overlap
- [x] 12. Ingestion service: validate, chunk, embed, persist
- [x] 13. `POST /api/documents` endpoint
- [x] 14. Multipart upload for `.txt` and `.md`
- [x] 15. Bean validation and a consistent JSON error body
- [x] 16. `DELETE /api/documents/{id}` removing chunks first
- [x] 17. Reject duplicate documents (same title and content) with 409
- [ ] 18. Size guard for oversized documents with a 413 test
- [ ] 19. Extract text from PDF uploads with Apache PDFBox
- [ ] 20. Extract text from DOCX uploads
- [ ] 21. Keep original filename and content type in a metadata table
- [ ] 22. Ingestion summary endpoint (documents, chunks, tokens)

## Phase 3 — Retrieval

- [x] 23. `EmbeddingService` abstraction
- [x] 24. Local hashing embeddings (offline, deterministic)
- [x] 25. Cosine similarity search over stored chunks
- [x] 26. Configurable top-k, minimum score and maximum top-k
- [x] 27. Rounded scores and display snippets in the API response
- [x] 28. Unit tests for ranking, threshold and edge cases
- [ ] 29. Optional document filter on `/api/ask`
- [ ] 30. Hybrid scoring: blend cosine with a BM25 keyword score
- [ ] 31. Cache decoded vectors in memory instead of re-parsing JSON
- [ ] 32. Reindex endpoint to re-embed documents after a model change
- [ ] 33. Chunk deduplication for repeated boilerplate text
- [ ] 34. Pagination for large knowledge bases
- [ ] 35. Move similarity search into Postgres with pgvector
- [ ] 36. HNSW index and a benchmark against the in-memory scan

## Phase 4 — Answer generation

- [x] 37. `LlmClient` abstraction taking the prompt and structured sources
- [x] 38. Offline extractive answerer with citation markers
- [x] 39. Prompt template with grounding and citation rules
- [x] 40. Return answer, model name, latency and source snippets
- [x] 41. Skip the model and answer honestly when nothing is retrieved
- [x] 42. Unit tests for prompt rendering and the no-context path
- [ ] 43. Integrate a Spring AI OpenAI chat model behind a profile
- [ ] 44. Hosted embeddings (OpenAI) behind `EmbeddingService`, with re-embedding
- [ ] 45. Ollama profile for fully local generation
- [ ] 46. Gemini provider
- [ ] 47. Stream answers token by token over SSE
- [ ] 48. Multi-turn conversation memory
- [ ] 49. Configurable answer length and tone
- [ ] 50. Guard against prompt injection inside uploaded documents
- [ ] 51. Store user feedback (helpful / not helpful) per answer

## Phase 5 — API and quality

- [x] 52. MockMvc integration test for ingest → ask → delete
- [x] 53. Integration tests for validation, upload and 404 paths
- [ ] 54. OpenAPI documentation with springdoc
- [ ] 55. Request logging with a correlation id
- [ ] 56. Micrometer timers for retrieval and generation latency
- [ ] 57. Rate limiting for `/api/ask`
- [ ] 58. Testcontainers integration test against Postgres
- [ ] 59. k6 load test with results recorded in the README
- [ ] 60. JaCoCo coverage gate in CI
- [ ] 61. UI error page when the API is unreachable

## Phase 6 — Frontend

- [x] 62. Thymeleaf page: add text, upload a file, list documents, ask
- [x] 63. Render answer with sources, scores and latency
- [x] 64. Chunk preview drawer per document
- [x] 65. Question history persisted in localStorage
- [x] 66. Markdown rendering for answers with sanitisation
- [x] 67. Dark/light theme toggle
- [x] 68. Accessibility pass: focus states, aria labels, contrast
- [x] 69. Optional React + Vite frontend on the same API

## Phase 7 — Deployment and polish

- [ ] 70. Deploy to a managed host with managed Postgres
- [ ] 71. CD workflow deploying main after CI passes
- [ ] 72. Architecture diagram image in `docs/`
- [ ] 73. Demo GIF in the README
- [ ] 74. Sample documents and a "load demo data" script
- [ ] 75. Performance pass: index chunk lookups by document
- [ ] 76. Short blog post on the design decisions

---

Progress: 39 / 76 complete.
