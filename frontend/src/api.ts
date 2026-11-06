import type { AnswerResponse, ChunkView, DocumentView, Meta } from './types';

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

export class ApiError extends Error {
  readonly status: number;
  readonly details: string[];

  constructor(message: string, status: number, details: string[] = []) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.details = details;
  }
}

function parseJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function messageFrom(data: unknown, status: number): { message: string; details: string[] } {
  if (data && typeof data === 'object') {
    const body = data as { message?: unknown; details?: unknown; error?: unknown };
    const details = Array.isArray(body.details)
      ? body.details.filter((detail): detail is string => typeof detail === 'string')
      : [];
    const message = typeof body.message === 'string' && body.message ? body.message : null;
    if (message) {
      return { message, details };
    }
    if (details.length > 0) {
      return { message: details.join(', '), details };
    }
    if (typeof body.error === 'string' && body.error) {
      return { message: body.error, details };
    }
  }
  return { message: `Request failed with status ${status}`, details: [] };
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${BASE_URL}${path}`, init);
  } catch {
    throw new ApiError('Cannot reach the server. Is the application running?', 0);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  const data = parseJson(text);

  if (!response.ok) {
    const { message, details } = messageFrom(data, response.status);
    throw new ApiError(message, response.status, details);
  }

  return data as T;
}

export const api = {
  meta: () => request<Meta>('/api/meta'),

  listDocuments: () => request<DocumentView[]>('/api/documents'),

  createDocument: (input: { title: string; content: string; sourceName?: string }) =>
    request<DocumentView>('/api/documents', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    }),

  uploadDocument: (file: File, title?: string) => {
    const body = new FormData();
    body.append('file', file);
    if (title?.trim()) {
      body.append('title', title.trim());
    }
    return request<DocumentView>('/api/documents/upload', { method: 'POST', body });
  },

  chunks: (id: number) => request<ChunkView[]>(`/api/documents/${id}/chunks`),

  deleteDocument: (id: number) => request<void>(`/api/documents/${id}`, { method: 'DELETE' }),

  ask: (question: string, topK?: number) =>
    request<AnswerResponse>('/api/ask', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(topK ? { question, topK } : { question }),
    }),
};
