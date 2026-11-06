import { useEffect, useRef } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Layers, X } from 'lucide-react';
import { api } from '../api';
import type { DocumentView } from '../types';
import { chunkLabel } from '../lib/utils';
import { Spinner } from './ui';

interface ChunkDrawerProps {
  doc: DocumentView;
  onClose: () => void;
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Could not load the chunks.';
}

export function ChunkDrawer({ doc, onClose }: ChunkDrawerProps) {
  const closeButton = useRef<HTMLButtonElement>(null);
  const chunksQuery = useQuery({
    queryKey: ['chunks', doc.id],
    queryFn: () => api.chunks(doc.id),
    staleTime: Infinity,
  });

  useEffect(() => {
    const previouslyFocused = window.document.activeElement as HTMLElement | null;
    closeButton.current?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
      }
    };
    window.addEventListener('keydown', onKey);
    const previousOverflow = window.document.body.style.overflow;
    window.document.body.style.overflow = 'hidden';
    return () => {
      window.removeEventListener('keydown', onKey);
      window.document.body.style.overflow = previousOverflow;
      previouslyFocused?.focus();
    };
    // onClose is stable enough for this sheet; re-running would steal focus.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const chunks = chunksQuery.data ?? [];

  return (
    <div className="fixed inset-0 z-40">
      <div className="absolute inset-0 bg-slate-950/50 backdrop-blur-sm" onClick={onClose} aria-hidden="true" />
      <aside
        role="dialog"
        aria-modal="true"
        aria-labelledby="chunk-heading"
        className="absolute inset-y-0 right-0 flex w-full max-w-lg animate-slide-in flex-col border-l border-line bg-surface shadow-2xl"
      >
        <div className="flex items-start gap-3 border-b border-line p-4">
          <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-brand-soft text-brand">
            <Layers className="size-4" />
          </span>
          <div className="min-w-0 flex-1">
            <p className="text-[10px] font-semibold uppercase tracking-wider text-muted">Indexed chunks</p>
            <h2 id="chunk-heading" className="mt-0.5 truncate text-sm font-semibold" title={doc.title}>
              {doc.title}
            </h2>
            <p className="mt-0.5 text-[11px] text-muted">
              {doc.chunkCount} chunk{doc.chunkCount === 1 ? '' : 's'} · each one is embedded separately
            </p>
          </div>
          <button
            ref={closeButton}
            type="button"
            onClick={onClose}
            className="grid size-8 shrink-0 place-items-center rounded-lg text-muted hover:bg-surface-2 hover:text-ink"
            aria-label="Close chunk preview"
          >
            <X className="size-4" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto scroll-thin p-4">
          {chunksQuery.isLoading && (
            <p role="status" className="flex items-center gap-2 text-xs text-muted">
              <Spinner className="size-3.5 text-brand" />
              Loading chunks…
            </p>
          )}

          {chunksQuery.isError && (
            <p role="alert" className="rounded-xl border border-danger/30 bg-danger-soft p-3 text-xs text-danger">
              {errorMessage(chunksQuery.error)}
            </p>
          )}

          {!chunksQuery.isLoading && !chunksQuery.isError && chunks.length === 0 && (
            <p className="text-xs text-muted">This document has no stored chunks.</p>
          )}

          <ol className="space-y-2">
            {chunks.map((chunk) => (
              <li key={chunk.id} className="rounded-xl border border-line bg-surface-2 p-3">
                <div className="flex items-center justify-between gap-3 text-[11px] text-muted">
                  <span className="font-semibold uppercase tracking-wider">{chunkLabel(chunk.chunkIndex)}</span>
                  <span>≈ {chunk.tokenCount} words</span>
                </div>
                <p className="mt-1.5 whitespace-pre-wrap text-xs leading-relaxed text-ink">{chunk.content}</p>
              </li>
            ))}
          </ol>
        </div>

        <p className="border-t border-line px-4 py-3 text-[11px] leading-relaxed text-muted">
          Retrieval ranks these passages with cosine similarity and only sends the best ones to the answerer.
        </p>
      </aside>
    </div>
  );
}
