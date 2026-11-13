# Building a RAG assistant that runs without an API key

Most retrieval augmented generation demos start with a hosted embedding model and
a hosted chat model, which means the first thing a reader needs is a credit card.
This project takes the opposite constraint: the whole pipeline — ingest, chunk,
embed, retrieve, answer, cite — has to work offline, in CI, on a laptop, with no
network. Then, and only then, do the hosted providers become an option.

Here is what that constraint forced, and what it turned out to be worth.

## The embedder is lexical, and that is fine

`HashingEmbeddingService` turns text into a 256-dimension vector with the hashing
trick: each stemmed, stop-word-filtered token is hashed to a dimension with a
sign, and the vector is L2 normalised. Two chunks that share words land near each
other; cosine similarity does the rest.

It is not semantic. A question that paraphrases "car" as "automobile" misses. But
it is deterministic, dependency free and fast, which makes every later decision
testable: the ranking tests assert exact orderings instead of "something relevant
came back". When a hosted embedding model arrives behind the same `EmbeddingService`
interface, it has to beat a baseline that already works.

The honest limitation is written down in the README rather than hidden, because a
lexical search that pretends to be semantic is worse than one that admits it.

## Cosine alone ranks badly, so retrieval is hybrid

Cosine similarity over term-frequency vectors rewards documents that repeat the
question's words. Ask about a specific identifier and the chunk that repeats five
common words beats the one that mentions the identifier once.

Retrieval therefore blends cosine with Okapi BM25. Cosine stays the relevance gate
— `min-score` keeps its meaning and a chunk with no overlap is still excluded —
while BM25's inverse document frequency decides the order among relevant chunks.
The blend weight is configuration (`app.rag.hybrid-weight`), and a scorer test
pins down the property that matters: a term appearing in one document outranks a
term appearing in several.

## The default answerer cannot hallucinate

`ExtractiveLlmClient` selects the retrieved sentences with the highest overlap
with the question and appends citation markers. It cannot invent a fact because it
only re-uses text that was retrieved. That makes the demo trustworthy in a way a
small hosted model often is not, and it gives the prompt-rendering code an exact
expected output to test against.

The seam matters more than the implementation: `LlmClient` receives the rendered
prompt *and* the structured sources, so a hosted client does not have to parse its
own prompt, and streaming has a default implementation that any client inherits.

## Retrieval has to be explainable

Every answer returns the passages it used, with document title, chunk number,
score and a snippet. When the answer is wrong, the source panel shows whether the
problem was retrieval (wrong passage) or generation (right passage, bad answer).
That single design choice removed most of the guesswork from tuning chunk size,
overlap and the score threshold.

## State that can be rebuilt, is rebuilt

Chunks are derived data. The raw document text is stored, and a reindex endpoint
throws every chunk away and rebuilds it. Changing the chunk size or the embedding
model is therefore a button, not a migration. The same reasoning keeps the vector
cache simple: chunk rows are immutable, so a cache keyed by chunk id only needs
invalidating when a document is deleted or reindexed.

Boilerplate is dropped at chunking time, ignoring case and punctuation. A page
footer that appears on all forty pages of a PDF would otherwise occupy forty
retrieval slots and push every real passage out of the top-k.

## Small surfaces, explicit limits

- Retrieval scans every chunk in memory. Fine for thousands, wrong for millions,
  and `pgvector` is on the roadmap where that is stated rather than implied.
- Conversation memory is a bounded in-process map. Restarting forgets it, and a
  second replica would not share it.
- The rate limit is per client address in memory, which assumes one instance and
  a container that is configured to trust forwarding headers.
- Prompt injection is handled by removing instruction-like lines from passages
  and telling the model that passages are data. It reduces the attack surface; it
  does not eliminate it.

None of these are hidden behind a "production ready" badge. Each one is a
deliberate trade with a note about when it stops being acceptable.

## What the offline constraint bought

Ninety-four tests with no network, no API keys and no flakiness. A demo that
starts with `mvn spring-boot:run`. And a codebase where adding a hosted model is
an implementation of an interface that already has a working, tested alternative —
which is the cheapest way to find out whether the hosted model is actually better.

That is the whole argument: build the boring version first, measure it, and keep
the seams open.
