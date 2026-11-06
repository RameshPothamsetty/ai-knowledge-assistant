import { useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeSanitize from 'rehype-sanitize';
import { Check, CircleAlert, Copy, RotateCcw, Sparkles } from 'lucide-react';
import type { ChatMessage, SourceSnippet } from '../types';
import { scorePercent } from '../lib/utils';
import { Badge, Spinner } from './ui';

interface AnswerCardProps {
  message: ChatMessage;
  onRetry: (message: ChatMessage) => void;
  onOpenSource: (source: SourceSnippet) => void;
}

export function AnswerCard({ message, onRetry, onOpenSource }: AnswerCardProps) {
  const [copied, setCopied] = useState(false);
  const noContext = message.status === 'answered' && (message.sources?.length ?? 0) === 0;

  async function copyAnswer() {
    if (!message.answer) {
      return;
    }
    try {
      await navigator.clipboard.writeText(message.answer);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1500);
    } catch {
      // Clipboard access can be denied; the answer is still selectable.
    }
  }

  return (
    <div className="rounded-2xl border border-line bg-surface p-4 shadow-sm sm:p-5">
      <div className="flex flex-wrap items-center gap-2">
        <span className="grid size-7 place-items-center rounded-full bg-gradient-to-br from-brand to-accent text-brand-ink">
          <Sparkles className="size-3.5" />
        </span>
        <span className="text-[11px] font-semibold uppercase tracking-wider text-muted">Answer</span>
        {message.model && <Badge>{message.model}</Badge>}
        {typeof message.latencyMs === 'number' && <Badge>{message.latencyMs} ms</Badge>}
      </div>

      {message.status === 'pending' && (
        <div className="mt-4 flex items-center gap-2 text-sm text-muted" role="status">
          <Spinner className="size-4 text-brand" />
          Searching your documents…
        </div>
      )}

      {message.status === 'failed' && (
        <div role="alert" className="mt-4 rounded-xl border border-danger/30 bg-danger-soft p-3">
          <p className="flex items-start gap-2 text-sm text-danger">
            <CircleAlert className="mt-0.5 size-4 shrink-0" />
            {message.error ?? 'The request failed.'}
          </p>
          <button
            type="button"
            onClick={() => onRetry(message)}
            className="mt-2 inline-flex items-center gap-1.5 rounded-md px-1.5 py-1 text-xs font-semibold text-danger hover:bg-danger/10"
          >
            <RotateCcw className="size-3" />
            Try again
          </button>
        </div>
      )}

      {message.status === 'answered' && (
        <>
          {noContext && (
            <div className="mt-4 flex items-start gap-2 rounded-xl border border-warn/30 bg-warn-soft p-3 text-xs leading-relaxed text-warn">
              <CircleAlert className="mt-0.5 size-4 shrink-0" />
              <span>
                Nothing in the knowledge base passed the relevance threshold, so no model was called.
                Add a document about this topic or rephrase the question.
              </span>
            </div>
          )}

          <div className="md mt-3 text-[0.95rem]">
            <ReactMarkdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeSanitize]}>
              {message.answer ?? ''}
            </ReactMarkdown>
          </div>

          {(message.sources?.length ?? 0) > 0 && (
            <div className="mt-4 border-t border-line pt-3">
              <p className="text-[10px] font-semibold uppercase tracking-wider text-muted">
                Sources · click to inspect
              </p>
              <div className="mt-2 flex flex-wrap gap-1.5">
                {message.sources?.map((source, index) => (
                  <button
                    key={`${source.documentId}-${source.chunkIndex}-${index}`}
                    type="button"
                    onClick={() => onOpenSource(source)}
                    className="group inline-flex max-w-full items-center gap-1.5 rounded-full border border-line bg-surface-2 px-2.5 py-1 text-xs transition hover:border-brand hover:text-brand"
                    title={source.snippet}
                  >
                    <span className="font-mono text-[10px] text-muted group-hover:text-brand">
                      [{index + 1}]
                    </span>
                    <span className="max-w-[14rem] truncate">{source.documentTitle}</span>
                    <span className="shrink-0 text-[10px] text-muted">
                      chunk {source.chunkIndex + 1} · {scorePercent(source.score)}%
                    </span>
                  </button>
                ))}
              </div>
            </div>
          )}

          <div className="mt-3 flex items-center justify-end gap-1">
            <button
              type="button"
              onClick={copyAnswer}
              className="inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-[11px] text-muted transition hover:bg-surface-2 hover:text-ink"
            >
              {copied ? <Check className="size-3" /> : <Copy className="size-3" />}
              {copied ? 'Copied' : 'Copy answer'}
            </button>
            <button
              type="button"
              onClick={() => onRetry(message)}
              className="inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-[11px] text-muted transition hover:bg-surface-2 hover:text-ink"
            >
              <RotateCcw className="size-3" />
              Ask again
            </button>
          </div>
        </>
      )}
    </div>
  );
}
