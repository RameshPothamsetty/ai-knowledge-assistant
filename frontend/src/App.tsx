import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Cpu, PanelLeftOpen, Trash2 } from 'lucide-react';
import { api } from './api';
import type { ChatMessage, SourceSnippet } from './types';
import { newId } from './lib/utils';
import { loadThread, saveThread } from './lib/storage';
import { Sidebar } from './components/Sidebar';
import { ChatThread } from './components/ChatThread';
import { Composer } from './components/Composer';
import { PasteDialog } from './components/PasteDialog';
import { SourceInspector } from './components/SourceInspector';
import { ThemeToggle } from './components/ThemeToggle';

interface Toast {
  id: number;
  text: string;
  tone: 'ok' | 'error';
  action?: { label: string; run: () => void };
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Something went wrong.';
}

const MAX_UPLOAD_BYTES = 2 * 1024 * 1024;

export default function App() {
  const queryClient = useQueryClient();
  const [messages, setMessages] = useState<ChatMessage[]>(() => loadThread());
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [pasteOpen, setPasteOpen] = useState(false);
  const [inspector, setInspector] = useState<SourceSnippet | null>(null);
  const [topK, setTopK] = useState<number | null>(null);
  const [toast, setToast] = useState<Toast | null>(null);

  const documentsQuery = useQuery({ queryKey: ['documents'], queryFn: api.listDocuments });
  const metaQuery = useQuery({ queryKey: ['meta'], queryFn: api.meta, staleTime: Infinity });

  const documents = documentsQuery.data ?? [];
  const effectiveTopK = topK ?? metaQuery.data?.defaultTopK ?? 4;
  const maxTopK = metaQuery.data?.maxTopK ?? 20;

  function showToast(text: string, tone: Toast['tone'] = 'ok', action?: Toast['action']) {
    setToast({ id: Date.now(), text, tone, action });
  }

  useEffect(() => {
    if (!toast) {
      return;
    }
    const timer = window.setTimeout(() => setToast(null), 4500);
    return () => window.clearTimeout(timer);
  }, [toast]);

  useEffect(() => {
    saveThread(messages);
  }, [messages]);

  const askMutation = useMutation({
    mutationFn: (variables: { id: string; question: string; topK: number }) =>
      api.ask(variables.question, variables.topK),
    onSuccess: (answer, variables) => {
      setMessages((previous) =>
        previous.map((message) =>
          message.id === variables.id
            ? {
                ...message,
                status: 'answered',
                answer: answer.answer,
                model: answer.model,
                latencyMs: answer.latencyMs,
                sources: answer.sources,
                error: undefined,
              }
            : message,
        ),
      );
    },
    onError: (error, variables) => {
      setMessages((previous) =>
        previous.map((message) =>
          message.id === variables.id
            ? { ...message, status: 'failed', error: errorMessage(error) }
            : message,
        ),
      );
    },
  });

  const uploadMutation = useMutation({
    mutationFn: (file: File) => api.uploadDocument(file),
    onSuccess: (document) => {
      queryClient.invalidateQueries({ queryKey: ['documents'] });
      showToast(`“${document.title}” added · ${document.chunkCount} chunk${document.chunkCount === 1 ? '' : 's'}`);
    },
    onError: (error) => showToast(errorMessage(error), 'error'),
  });

  const createMutation = useMutation({
    mutationFn: (input: { title: string; content: string }) => api.createDocument(input),
    onSuccess: (document) => {
      queryClient.invalidateQueries({ queryKey: ['documents'] });
      setPasteOpen(false);
      showToast(`“${document.title}” added · ${document.chunkCount} chunk${document.chunkCount === 1 ? '' : 's'}`);
    },
    onError: (error) => showToast(errorMessage(error), 'error'),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => api.deleteDocument(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['documents'] });
      showToast('Document deleted');
    },
    onError: (error) => showToast(errorMessage(error), 'error'),
  });

  const busy = uploadMutation.isPending || createMutation.isPending || deleteMutation.isPending;

  function askQuestion(question: string) {
    const text = question.trim();
    if (!text || askMutation.isPending) {
      return;
    }
    const message: ChatMessage = {
      id: newId(),
      question: text,
      createdAt: new Date().toISOString(),
      status: 'pending',
    };
    setMessages((previous) => [...previous, message]);
    askMutation.mutate({ id: message.id, question: text, topK: effectiveTopK });
  }

  function retryMessage(message: ChatMessage) {
    if (askMutation.isPending) {
      return;
    }
    setMessages((previous) =>
      previous.map((item) =>
        item.id === message.id
          ? { ...item, status: 'pending', error: undefined, answer: undefined, sources: undefined }
          : item,
      ),
    );
    askMutation.mutate({ id: message.id, question: message.question, topK: effectiveTopK });
  }

  function handleUpload(file: File) {
    const name = file.name.toLowerCase();
    if (!/\.(txt|md|markdown)$/.test(name)) {
      showToast('Only .txt and .md files are supported.', 'error');
      return;
    }
    if (file.size > MAX_UPLOAD_BYTES) {
      showToast('Files must be 2 MB or smaller.', 'error');
      return;
    }
    uploadMutation.mutate(file);
  }

  function clearThread() {
    const previous = messages;
    setMessages([]);
    showToast('Conversation cleared', 'ok', { label: 'Undo', run: () => setMessages(previous) });
  }

  const documentCountLabel = useMemo(
    () => `${documents.length} document${documents.length === 1 ? '' : 's'} indexed`,
    [documents.length],
  );

  return (
    <div className="flex h-dvh overflow-hidden bg-canvas">
      <Sidebar
        documents={documents}
        isLoading={documentsQuery.isLoading}
        error={documentsQuery.isError ? errorMessage(documentsQuery.error) : null}
        busy={busy}
        embeddingModel={metaQuery.data?.embeddingModel}
        open={sidebarOpen}
        onClose={() => setSidebarOpen(false)}
        onOpenPaste={() => {
          setPasteOpen(true);
          setSidebarOpen(false);
        }}
        onUpload={handleUpload}
        onDelete={(id) => deleteMutation.mutate(id)}
      />

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center gap-2 border-b border-line bg-surface/60 px-3 py-2.5 backdrop-blur sm:gap-3 sm:px-6 sm:py-3">
          <button
            type="button"
            onClick={() => setSidebarOpen(true)}
            className="grid size-9 shrink-0 place-items-center rounded-lg text-muted hover:bg-surface-2 hover:text-ink lg:hidden"
            aria-label="Open document library"
          >
            <PanelLeftOpen className="size-4" />
          </button>

          <div className="min-w-0 flex-1">
            <h1 className="truncate text-sm font-semibold sm:text-base">Ask your knowledge base</h1>
            <p className="hidden truncate text-xs text-muted sm:block">
              Grounded answers with the passages they came from.
            </p>
          </div>

          {metaQuery.data && (
            <span
              className="hidden items-center gap-1.5 rounded-full border border-line bg-surface px-2.5 py-1 text-[11px] text-muted md:inline-flex"
              title={`${metaQuery.data.embeddingDimensions}-dimension local embeddings`}
            >
              <Cpu className="size-3" />
              {metaQuery.data.embeddingModel}
            </span>
          )}

          {messages.length > 0 && (
            <button
              type="button"
              onClick={clearThread}
              className="hidden items-center gap-1.5 rounded-lg px-2 py-1.5 text-xs text-muted transition hover:bg-surface-2 hover:text-ink sm:inline-flex"
            >
              <Trash2 className="size-3.5" />
              Clear
            </button>
          )}

          <ThemeToggle />
        </header>

        <ChatThread
          messages={messages}
          documents={documents}
          onAsk={askQuestion}
          onRetry={retryMessage}
          onOpenSource={setInspector}
          onCreateDocument={() => setPasteOpen(true)}
        />

        <Composer
          onAsk={askQuestion}
          asking={askMutation.isPending}
          topK={effectiveTopK}
          maxTopK={maxTopK}
          onTopKChange={setTopK}
          documentCount={documents.length}
        />
      </div>

      <PasteDialog
        open={pasteOpen}
        busy={createMutation.isPending}
        onClose={() => setPasteOpen(false)}
        onSubmit={(title, content) => createMutation.mutate({ title, content })}
      />

      {inspector && <SourceInspector source={inspector} onClose={() => setInspector(null)} />}

      {toast && (
        <div
          role="status"
          aria-live="polite"
          className="fixed bottom-4 left-1/2 z-50 flex max-w-[calc(100vw-2rem)] -translate-x-1/2 items-center gap-3 rounded-xl border border-line bg-surface px-3.5 py-2.5 text-xs shadow-xl"
        >
          <span className={toast.tone === 'error' ? 'text-danger' : 'text-ink'}>{toast.text}</span>
          {toast.action && (
            <button
              type="button"
              onClick={() => {
                toast.action?.run();
                setToast(null);
              }}
              className="shrink-0 font-semibold text-brand hover:underline"
            >
              {toast.action.label}
            </button>
          )}
          <span className="sr-only">{documentCountLabel}</span>
        </div>
      )}
    </div>
  );
}
