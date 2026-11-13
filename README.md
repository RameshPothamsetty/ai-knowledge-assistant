# AI Knowledge Assistant

[![CI](https://github.com/RameshPothamsetty/ai-knowledge-assistant/actions/workflows/ci.yml/badge.svg)](https://github.com/RameshPothamsetty/ai-knowledge-assistant/actions/workflows/ci.yml)

A retrieval augmented generation (RAG) application that answers questions using **only** the documents you upload. Built with Java 21, Spring Boot 3.5 and Spring Data JPA, with a React + Vite UI that compiles into the same jar.

The whole pipeline runs offline by default — no API key, no external model, no network calls — while keeping the model layers behind interfaces so a hosted LLM can be plugged in later.

---

## What it does

- **Ingest** pasted text or `.txt`, `.md`, `.pdf` and `.docx` uploads.
- **Chunk** documents sentence-aware with configurable size and overlap.
- **Embed** every chunk and store the vector alongside it.
- **Retrieve** the closest chunks with cosine similarity, filtered by a relevance threshold.
- **Answer** with a grounded, citation-marked response and show the exact passages and scores used.
- **Refuse** honestly when the knowledge base has nothing relevant, instead of inventing an answer.

## Architecture

![Component diagram](docs/architecture.png)

```
POST /api/documents ──► DocumentService ──► ChunkingService ──► EmbeddingService
                              │                                      │
                              ▼                                      ▼
                       knowledge_document                     document_chunk
                                                                 (text + vector)

POST /api/ask ──► RagService ──► embed question ──► VectorSearchService (cosine, top-k)
                       │                                        │
                       ▼                                        ▼
              RagPromptBuilder ──► LlmClient        retrieved chunks + scores
                       │
                       ▼
          answer + citations + source snippets
```

| Layer | Choice | Why |
| --- | --- | --- |
| Language / runtime | Java 21 | LTS, records, text blocks, pattern matching |
| Backend | Spring Boot 3.5 (Web, Data JPA, Validation, Actuator) | Standard, testable, production-shaped |
| Database | H2 (dev), PostgreSQL + pgvector (compose/CI target) | Zero-setup locally, realistic deployment |
| Retrieval | Cosine + BM25; in-memory scan on H2, HNSW index on Postgres | Portable by default, index-backed at scale |
| Embeddings | Local feature hashing (256 dims, L2 normalised) | Deterministic, offline, free |
| Answering | Extractive answerer behind `LlmClient` | Demonstrates grounding without a key |
| UI | React 19 + Vite + TypeScript + Tailwind CSS | Typed against the API, still one deployable artefact |
| DevOps | Docker (multi-stage), docker-compose, GitHub Actions | Reproducible build and CI |

## Quick start

Requirements: JDK 21+, Maven 3.9+, Node 20+.

```bash
git clone https://github.com/RameshPothamsetty/ai-knowledge-assistant.git
cd ai-knowledge-assistant
npm --prefix frontend ci
npm --prefix frontend run build   # writes the SPA into src/main/resources/static
mvn spring-boot:run
```

Open <http://localhost:8080>, add a document, then ask a question about it. The H2 console is available at `/h2-console` (JDBC URL `jdbc:h2:mem:knowledge`, user `sa`, empty password), the OpenAPI document at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`.

The UI is a separate build so the backend stays a plain Maven project. Until `npm run build` has run, the jar serves the API but no page.

To try it with content, load the sample documents into the running instance:

```bash
./scripts/load-demo-data.sh        # uploads samples/* with their file names as titles
```

### Frontend development

```bash
mvn spring-boot:run                 # API on :8080
npm --prefix frontend run dev       # Vite on :5173, proxies /api to :8080
```

Open <http://localhost:5173> for hot reload. `npm --prefix frontend run typecheck` checks the UI without building it.

### With Docker

```bash
cp .env.example .env      # then change the password
docker compose up --build
```

This starts PostgreSQL (the pgvector image) and the application on <http://localhost:8080>. On startup the `postgres` profile installs the `vector` extension, mirrors embeddings into a `vector(256)` column and builds an HNSW index, so retrieval runs in the database instead of in memory.

## API

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/documents` | Create a document from raw text |
| `POST` | `/api/documents/upload` | Upload a `.txt`, `.md`, `.pdf` or `.docx` file (multipart) |
| `GET` | `/api/documents` | List documents, newest first |
| `GET` | `/api/documents/page` | One page of documents (`page`, `size`), newest first |
| `POST` | `/api/documents/reindex` | Re-chunk and re-embed every stored document |
| `GET` | `/api/documents/summary` | Document, chunk and token totals for the knowledge base |
| `GET` | `/api/documents/{id}` | Fetch one document |
| `GET` | `/api/documents/{id}/chunks` | Stored chunks in order, used by the preview drawer |
| `DELETE` | `/api/documents/{id}` | Delete a document and its chunks |
| `POST` | `/api/ask` | Ask a question over the knowledge base, optionally scoped to one document |
| `POST` | `/api/ask/stream` | Same question, streamed as Server-Sent Events |
| `POST` | `/api/feedback` | Record a helpful / not-helpful vote for an answer |
| `GET` | `/api/feedback/summary` | Vote totals |
| `GET` | `/api/meta` | Active embedding model, chunking and top-k limits |
| `GET` | `/actuator/health` | Health probe |
| `GET` | `/actuator/metrics/rag.retrieval` | Retrieval latency timer (`rag.generation`, `rag.answers` too) |
| `GET` | `/v3/api-docs` | OpenAPI document, rendered at `/swagger-ui.html` |

Ingesting a document whose title **and** content already exist returns `409 Conflict`, so the same text cannot flood retrieval with duplicate chunks. A different title or different content is accepted. Text above `max-document-characters` returns `413 Payload Too Large`, unsupported extensions `415 Unsupported Media Type`, and files whose text cannot be extracted — corrupt files or scans without a text layer — `422 Unprocessable Entity`.

`POST /api/ask` accepts optional fields beside `question`: `topK` (clamped to the configured maximum), `documentId` (restrict retrieval to one document), `length` (`short`, `medium`, `long`), `tone` (`neutral`, `friendly`, `technical`) and `conversationId` (carry the last few turns of a follow-up conversation into the prompt). Enum values are accepted in any case. A `documentId` for a document that no longer exists yields the same honest "nothing relevant" answer as an empty knowledge base rather than an error.

`POST /api/ask/stream` answers the same body as SSE: a `sources` event, one `token` event per word, then `done` with the `answerId`, model and latency. Pass that `answerId` to `POST /api/feedback` with `helpful` and an optional `comment`.

Passages containing instruction-like text — "ignore previous instructions", role prefixes such as `system:`, requests to reveal the prompt — are replaced with `[instruction-like text removed]` before the prompt is rendered, and the system prompt states that passages are data rather than instructions.

`POST /api/documents/reindex` is for changing the embedding model or chunking settings: it rebuilds every chunk from the stored text, keeps document ids and metadata, and returns how many documents, chunks and milliseconds it took. It is safe to run repeatedly, and the retrieval cache is dropped so the next question reads the new vectors.

### Example

```bash
curl -X POST http://localhost:8080/api/documents \
  -H 'Content-Type: application/json' \
  -d '{
        "title": "Spring Boot Guide",
        "content": "Spring Boot provides auto configuration. It starts an embedded Tomcat server by default. The Actuator module exposes health and metrics endpoints."
      }'
```

```json
{
  "id": 1,
  "title": "Spring Boot Guide",
  "sourceName": null,
  "contentType": "text/plain",
  "sizeBytes": 146,
  "chunkCount": 1,
  "createdAt": "2026-11-05T10:30:00Z"
}
```

```bash
curl -X POST http://localhost:8080/api/ask \
  -H 'Content-Type: application/json' \
  -d '{"question": "How can I check the health of the application?"}'
```

Add `"documentId": 1` to the same body to answer only from that document.

```json
{
  "answer": "The Actuator module exposes health and metrics endpoints. [1]",
  "model": "extractive-fallback-v1",
  "sources": [
    {
      "documentId": 1,
      "documentTitle": "Spring Boot Guide",
      "chunkIndex": 0,
      "score": 0.412,
      "snippet": "Spring Boot provides auto configuration. It starts an embedded Tomcat server by default. The Actuator module exposes health and metrics endpoints."
    }
  ],
  "latencyMs": 4
}
```

Every response carries an `X-Request-Id` header: a well-formed value from the caller is reused, otherwise a UUID is generated. The same id appears in the log pattern and in error bodies, and `POST /api/ask` is rate limited per client address, returning `429` with `Retry-After` beyond the configured limit.

Errors are JSON too:

```json
{
  "timestamp": "2026-11-05T10:31:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "validation failed",
  "details": ["title: title is required"]
}
```

## Configuration

| Property | Env var | Default | Meaning |
| --- | --- | --- | --- |
| `server.port` | `SERVER_PORT` | `8080` | HTTP port |
| `spring.profiles.active` | `SPRING_PROFILES_ACTIVE` | `dev` | `dev` (H2) or `postgres` |
| `app.rag.chunk-size` | — | `800` | Max characters per chunk |
| `app.rag.chunk-overlap` | — | `120` | Characters of overlap between chunks |
| `app.rag.default-top-k` | — | `4` | Chunks retrieved by default |
| `app.rag.max-top-k` | — | `20` | Server-side cap for `topK` |
| `app.rag.embedding-dimensions` | — | `256` | Vector size of the local embedder |
| `app.rag.min-score` | — | `0.05` | Minimum cosine similarity to keep a chunk |
| `app.rag.max-document-characters` | — | `200000` | Largest amount of text one document may contain |
| `app.rag.hybrid-weight` | — | `0.75` | Share of the cosine score in the ranking blend; the rest is BM25 |
| `app.rag.vector-cache-size` | — | `4096` | Decoded chunk vectors kept in memory |
| `app.rate-limit.requests-per-minute` | — | `60` | Questions allowed per client address per minute (`429` beyond it) |

Secrets are never committed: see [`.env.example`](.env.example) for the variables Docker reads. `.env` is git-ignored.

## How answer generation works

`RagService` embeds the question with the **same** embedder used for the chunks, retrieves the top-k chunks above `min-score`, renders a prompt with numbered passages, and passes it to the active `LlmClient`.

Retrieval ranks by a blend of cosine similarity and Okapi BM25: cosine remains the relevance gate, while the keyword score weights rare terms more heavily, so a question mentioning a specific identifier is not drowned out by common words. Decoded vectors are cached in memory, and repeated boilerplate is dropped at chunking time so it cannot claim several retrieval slots.

On the `postgres` profile that scan is replaced by pgvector: a startup task installs the extension, mirrors each chunk's JSON embedding into a `vector(256)` column through a trigger, and builds an HNSW cosine index. Postgres selects the candidate pool by distance, and the same BM25 blend re-ranks it in Java, so the scoring behaves the same on both profiles. H2 keeps the portable in-memory path, which needs no extensions. Benchmarked on the test corpus of 4,000 chunks (top-k 5, median of 10 runs), the in-memory scan took **312–430 ms** per question against **3.5–7.3 ms** with the HNSW index across runs.

The default `ExtractiveLlmClient` is deliberately honest: it selects the retrieved sentences with the highest term overlap with the question and appends citation markers. It cannot hallucinate, because it only re-uses retrieved text. Swapping in a hosted model is a matter of providing another `LlmClient` bean — the prompt already contains grounding rules, citation requirements and a "say you don't know" instruction. That is tracked on the roadmap.

### Current limitations

- The local embedder is **lexical**, not semantic: it matches shared words and stemming, so paraphrased questions can miss relevant passages. Hosted embeddings are a roadmap item.
- The index-backed path exists only on Postgres, where the `vector` extension must be installable; H2 keeps the in-memory scan. The pgvector query re-ranks only a candidate pool, so its ordering can differ slightly from a full-corpus scan.
- PDF and DOCX text is extracted, but scans without a text layer need OCR, which is not wired in; images and embedded objects inside DOCX files are ignored.
- Conversation memory is in process memory: a restart forgets it and a second replica would not share it.
- Streaming uses the offline client's word-splitting; a hosted model can stream its own tokens through the same `LlmClient.stream` hook.
- Feedback is stored, but answers themselves are not, so a vote is not linked back to the passage that was used.
- Chat history lives in the browser's `localStorage`, not in a database.

## Tests

```bash
mvn test
```

96 tests, no network or API keys required. The two Postgres suites need Docker (Testcontainers) and are skipped automatically when no daemon is available.

| Suite | What it covers |
| --- | --- |
| `ChunkingServiceTest` | blank input, size limits, overlap, oversized sentences, normalisation, boilerplate dedup |
| `HashingEmbeddingServiceTest` | determinism, unit norm, related-vs-unrelated ranking, stop words |
| `EmbeddingCodecTest` | JSON round-trip and malformed payloads |
| `VectorSearchServiceTest` | ranking, threshold filtering, top-k, document filter, hybrid weights, cache reuse, cosine edge cases |
| `Bm25ScorerTest` | rare-term weighting, length normalisation, empty queries |
| `VectorCacheTest` | cache hits, eviction, capacity bound |
| `DocumentServiceTest` | embed-and-persist, validation, duplicate rejection, size limit, cascade delete, listing, pagination, reindex, chunk order, summary |
| `DocumentTextExtractorTest` | UTF-8 text and Markdown, PDF and DOCX extraction, corrupt files, scans without text, unsupported extensions |
| `RagServiceTest` | grounded answer, source metadata, no-context path, top-k clamping, style instructions, conversation turns, injection sanitising, streaming |
| `ExtractiveLlmClientTest` | citation selection and deterministic output |
| `KnowledgeApiIntegrationTest` | full MockMvc flow: ingest → ask → list → delete → 404, duplicate 409, chunk preview, `413`/`415`/`422` paths, PDF/DOCX uploads, summary, pagination, reindex, document-scoped questions, SSE streaming, feedback, correlation ids, timers, OpenAPI, `/api/meta` |
| `RateLimitIntegrationTest` | per-client limit on `/api/ask`, `429` with `Retry-After`, trailing-slash bypass, other endpoints unaffected |
| `PostgresIntegrationTest` | full flow against real Postgres with pgvector: ingest, vector mirroring, HNSW index, scoped ask, page, reindex, delete |
| `RetrievalBenchmarkTest` | seeds 4,000 chunks and times the in-memory scan against the HNSW index |

`mvn verify` also runs a JaCoCo check that fails the build when bundle instruction coverage drops below 80%.

### Load test

`scripts/load-test.js` drives `/api/ask` with k6. Raise the rate limit first, or the script measures `429`s:

```bash
APP_RATE_LIMIT_REQUESTS_PER_MINUTE=1000000 java -jar target/ai-knowledge-assistant-0.1.0-SNAPSHOT.jar
./scripts/load-demo-data.sh
docker run --rm -i -e BASE_URL=http://host.docker.internal:8080 \
  grafana/k6 run - < scripts/load-test.js
```

Recorded run on a Windows laptop against the H2 profile with three sample documents, 10 virtual users for 20 seconds:

| Metric | Result |
| --- | --- |
| Requests | 22,423 (1,121 req/s) |
| Failed requests | 0.00% |
| p50 / p95 latency | 6.9 ms / 18.5 ms |
| Checks passed | 100% |

`VUS` and `DURATION` are overridable with `-e`; thresholds fail the run above 1% errors or a p95 over 500 ms.

## Project layout

```
src/main/java/com/ramesh/knowledge
├── chat/         RagService, LlmClient, prompt builder, conversations, feedback, /api/ask
├── config/       typed RAG properties
├── document/     entities, repositories, chunking, ingestion, /api/documents
├── embedding/    embedding abstraction, local hashing model, vector codec
├── search/       retrieval: in-memory scan, pgvector + HNSW, BM25, vector cache
└── web/          JSON error handling, correlation id and rate-limit filters
src/main/resources
└── application.yml
frontend/            React + Vite SPA, built into src/main/resources/static
├── src/components   sidebar, chat thread, answer card, source inspector
├── src/api.ts       typed API client
└── vite.config.ts   build target and dev proxy
samples/             three short documents the demo script uploads
scripts/             load-demo-data.sh, load-test.js
docs/                architecture diagram (markdown + PNG) and design write-up
```

## Documentation

- [ROADMAP.md](ROADMAP.md) — 76 small tasks, easiest to hardest, with progress checkboxes
- [DEVLOG.md](DEVLOG.md) — one entry per working session
- [docs/architecture.md](docs/architecture.md) — component diagram (also as `docs/architecture.png`) and request flow
- [docs/blog-post.md](docs/blog-post.md) — why the pipeline is offline-first
