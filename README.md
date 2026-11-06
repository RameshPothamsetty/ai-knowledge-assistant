# AI Knowledge Assistant

[![CI](https://github.com/RameshPothamsetty/ai-knowledge-assistant/actions/workflows/ci.yml/badge.svg)](https://github.com/RameshPothamsetty/ai-knowledge-assistant/actions/workflows/ci.yml)

A retrieval augmented generation (RAG) application that answers questions using **only** the documents you upload. Built with Java 21, Spring Boot 3.5 and Spring Data JPA, with a React + Vite UI that compiles into the same jar.

The whole pipeline runs offline by default — no API key, no external model, no network calls — while keeping the model layers behind interfaces so a hosted LLM can be plugged in later.

---

## What it does

- **Ingest** pasted text or `.txt` / `.md` uploads.
- **Chunk** documents sentence-aware with configurable size and overlap.
- **Embed** every chunk and store the vector alongside it.
- **Retrieve** the closest chunks with cosine similarity, filtered by a relevance threshold.
- **Answer** with a grounded, citation-marked response and show the exact passages and scores used.
- **Refuse** honestly when the knowledge base has nothing relevant, instead of inventing an answer.

## Architecture

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
| Database | H2 (dev), PostgreSQL (compose/CI target) | Zero-setup locally, realistic deployment |
| Retrieval | Cosine similarity over stored vectors | Portable, no extensions needed |
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

Open <http://localhost:8080>, add a document, then ask a question about it. The H2 console is available at `/h2-console` (JDBC URL `jdbc:h2:mem:knowledge`, user `sa`, empty password).

The UI is a separate build so the backend stays a plain Maven project. Until `npm run build` has run, the jar serves the API but no page.

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

This starts PostgreSQL and the application on <http://localhost:8080>.

## API

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/documents` | Create a document from raw text |
| `POST` | `/api/documents/upload` | Upload a `.txt` / `.md` file (multipart) |
| `GET` | `/api/documents` | List documents, newest first |
| `GET` | `/api/documents/{id}` | Fetch one document |
| `GET` | `/api/documents/{id}/chunks` | Stored chunks in order, used by the preview drawer |
| `DELETE` | `/api/documents/{id}` | Delete a document and its chunks |
| `POST` | `/api/ask` | Ask a question over the knowledge base |
| `GET` | `/api/meta` | Active embedding model, chunking and top-k limits |
| `GET` | `/actuator/health` | Health probe |

Ingesting a document whose title **and** content already exist returns `409 Conflict`, so the same text cannot flood retrieval with duplicate chunks. A different title or different content is accepted.

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
{ "id": 1, "title": "Spring Boot Guide", "sourceName": null, "chunkCount": 1, "createdAt": "2026-11-05T10:30:00Z" }
```

```bash
curl -X POST http://localhost:8080/api/ask \
  -H 'Content-Type: application/json' \
  -d '{"question": "How can I check the health of the application?"}'
```

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

Secrets are never committed: see [`.env.example`](.env.example) for the variables Docker reads. `.env` is git-ignored.

## How answer generation works

`RagService` embeds the question with the **same** embedder used for the chunks, retrieves the top-k chunks above `min-score`, renders a prompt with numbered passages, and passes it to the active `LlmClient`.

The default `ExtractiveLlmClient` is deliberately honest: it selects the retrieved sentences with the highest term overlap with the question and appends citation markers. It cannot hallucinate, because it only re-uses retrieved text. Swapping in a hosted model is a matter of providing another `LlmClient` bean — the prompt already contains grounding rules, citation requirements and a "say you don't know" instruction. That is tracked on the roadmap.

### Current limitations

- The local embedder is **lexical**, not semantic: it matches shared words and stemming, so paraphrased questions can miss relevant passages. Hosted embeddings are a roadmap item.
- Similarity is computed in memory over all chunks. Fine for thousands of chunks, not for millions (pgvector is on the roadmap).
- Only plain text and Markdown are accepted; PDF and DOCX need parsers.
- One request at a time per answer: no streaming or conversation memory yet.
- Chat history lives in the browser's `localStorage`, not in a database.

## Tests

```bash
mvn test
```

47 tests, no network or API keys required:

| Suite | What it covers |
| --- | --- |
| `ChunkingServiceTest` | blank input, size limits, overlap, oversized sentences, normalisation |
| `HashingEmbeddingServiceTest` | determinism, unit norm, related-vs-unrelated ranking, stop words |
| `EmbeddingCodecTest` | JSON round-trip and malformed payloads |
| `VectorSearchServiceTest` | ranking, threshold filtering, top-k, cosine edge cases |
| `DocumentServiceTest` | embed-and-persist, validation, duplicate rejection, cascade delete, listing, chunk order |
| `RagServiceTest` | grounded answer, source metadata, no-context path, top-k clamping |
| `ExtractiveLlmClientTest` | citation selection and deterministic output |
| `KnowledgeApiIntegrationTest` | full MockMvc flow: ingest → ask → list → delete → 404, duplicate 409, chunk preview, upload paths, `/api/meta` |

## Project layout

```
src/main/java/com/ramesh/knowledge
├── chat/         RagService, LlmClient, extractive model, prompt builder, /api/ask
├── config/       typed RAG properties
├── document/     entities, repositories, chunking, ingestion, /api/documents
├── embedding/    embedding abstraction, local hashing model, vector codec
├── search/       cosine similarity retrieval
└── web/          home controller, JSON error handling
src/main/resources
└── application.yml
frontend/            React + Vite SPA, built into src/main/resources/static
├── src/components   sidebar, chat thread, answer card, source inspector
├── src/api.ts       typed API client
└── vite.config.ts   build target and dev proxy
```

## Documentation

- [ROADMAP.md](ROADMAP.md) — 76 small tasks, easiest to hardest, with progress checkboxes
- [DEVLOG.md](DEVLOG.md) — one entry per working session
