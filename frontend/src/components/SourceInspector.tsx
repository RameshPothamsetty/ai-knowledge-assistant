import { useEffect, useRef, useState } from 'react';
import { Check, Copy, Quote, X } from 'lucide-react';
import type { SourceSnippet } from '../types';
import { chunkLabel, scorePercent } from '../lib/utils';

interface SourceInspectorProps {
  source: SourceSnippet;
  onClose: () => void;
}

export function SourceInspector({ source, onClose }: SourceInspectorProps) {
  const closeButton = useRef<HTMLButtonElement>(null);
  const [copied, setCopied] = useState(false);
  const percent = scorePercent(source.score);

  useEffect(() => {
    const previouslyFocused = document.activeElement as HTMLElement | null;
    closeButton.current?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
      }
    };
    window.addEventListener('keydown', onKey);
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      window.removeEventListener('keydown', onKey);
      document.body.style.overflow = previousOverflow;
      previouslyFocused?.focus();
    };
    // onClose is stable enough for this sheet; re-running would steal focus.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function copyPassage() {
    try {
      await navigator.clipboard.writeText(source.snippet);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1500);
    } catch {
      // ignore
    }
  }

  return (
    <div className="fixed inset-0 z-40">
      <div className="absolute inset-0 bg-slate-950/50 backdrop-blur-sm" onClick={onClose} aria-hidden="true" />
      <aside
        role="dialog"
        aria-modal="true"
        aria-labelledby="source-heading"
        className="absolute inset-y-0 right-0 flex w-full max-w-md animate-slide-in flex-col border-l border-line bg-surface shadow-2xl"
      >
        <div className="flex items-start gap-3 border-b border-line p-4">
          <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-brand-soft text-brand">
            <Quote className="size-4" />
          </span>
          <div className="min-w-0 flex-1">
            <p className="text-[10px] font-semibold uppercase tracking-wider text-muted">Source passage</p>
            <h2 id="source-heading" className="mt-0.5 truncate text-sm font-semibold" title={source.documentTitle}>
              {source.documentTitle}
            </h2>
          </div>
          <button
            ref={closeButton}
            type="button"
            onClick={onClose}
            className="grid size-8 shrink-0 place-items-center rounded-lg text-muted hover:bg-surface-2 hover:text-ink"
            aria-label="Close source"
          >
            <X className="size-4" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto scroll-thin p-4">
          <div className="flex items-center justify-between text-xs text-muted">
            <span>{chunkLabel(source.chunkIndex)}</span>
            <span className="font-medium text-ink">{percent}% match</span>
          </div>
          <div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-surface-2">
            <div
              className="h-full rounded-full bg-gradient-to-r from-brand to-accent"
              style={{ width: `${percent}%` }}
            />
          </div>

          <blockquote className="mt-5 rounded-xl border border-line bg-surface-2 p-4 text-sm leading-relaxed text-ink">
            <p className="whitespace-pre-wrap">{source.snippet}</p>
          </blockquote>

          <p className="mt-3 text-[11px] leading-relaxed text-muted">
            Cosine similarity between the question and this chunk&apos;s local embedding. Passages below the
            configured threshold are never shown.
          </p>
        </div>

        <div className="flex items-center justify-between border-t border-line p-4">
          <button
            type="button"
            onClick={copyPassage}
            className="inline-flex items-center gap-1.5 rounded-lg border border-line px-3 py-1.5 text-xs text-muted transition hover:border-line-strong hover:text-ink"
          >
            {copied ? <Check className="size-3.5" /> : <Copy className="size-3.5" />}
            {copied ? 'Copied' : 'Copy passage'}
          </button>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg px-3 py-1.5 text-xs font-medium text-muted transition hover:bg-surface-2 hover:text-ink"
          >
            Close
          </button>
        </div>
      </aside>
    </div>
  );
}
