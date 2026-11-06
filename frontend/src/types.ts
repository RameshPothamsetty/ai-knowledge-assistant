/** Types mirror the Spring DTO records one-for-one so the contract is visible in the UI code. */

export interface DocumentView {
  id: number;
  title: string;
  sourceName: string | null;
  chunkCount: number;
  createdAt: string;
}

export interface SourceSnippet {
  documentId: number;
  documentTitle: string;
  chunkIndex: number;
  score: number;
  snippet: string;
}

export interface AnswerResponse {
  answer: string;
  model: string;
  sources: SourceSnippet[];
  latencyMs: number;
}

export interface Meta {
  embeddingModel: string;
  embeddingDimensions: number;
  chunkSize: number;
  chunkOverlap: number;
  defaultTopK: number;
  maxTopK: number;
  minScore: number;
}

export type MessageStatus = 'pending' | 'answered' | 'failed';

export interface ChatMessage {
  id: string;
  question: string;
  createdAt: string;
  status: MessageStatus;
  answer?: string;
  model?: string;
  latencyMs?: number;
  sources?: SourceSnippet[];
  error?: string;
}
