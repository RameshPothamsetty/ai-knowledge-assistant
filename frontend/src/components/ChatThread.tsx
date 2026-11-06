import { useEffect, useMemo, useRef } from 'react';
import { FilePlus2, LibraryBig, MessageSquareText } from 'lucide-react';
import type { ChatMessage, DocumentView, SourceSnippet } from '../types';
import { cn, formatTime } from '../lib/utils';
import { AnswerCard } from './AnswerCard';
import { Button } from './ui';

interface ChatThreadProps {
  messages: ChatMessage[];
  documents: DocumentView[];
  onAsk: (question: string) => void;
  onRetry: (message: ChatMessage) => void;
  onOpenSource: (source: SourceSnippet) => void;
  onCreateDocument: () => void;
}

export function ChatThread({
  messages,
  documents,
  onAsk,
  onRetry,
  onOpenSource,
  onCreateDocument,
}: ChatThreadProps) {
  const scroller = useRef<HTMLDivElement>(null);
  const stickToBottom = useRef(true);

  const lastStatus = messages[messages.length - 1]?.status;

  useEffect(() => {
    const element = scroller.current;
    if (element && stickToBottom.current) {
      element.scrollTop = element.scrollHeight;
    }
  }, [messages.length, lastStatus]);

  const suggestions = useMemo(() => {
    const first = documents[0];
    const second = documents[1];
    const items: string[] = [];
    if (first) {
      items.push(`Summarise “${first.title}”`);
      items.push(`What are the key points in “${first.title}”?`);
    }
    if (second) {
      items.push(`What does “${second.title}” cover?`);
    }
    return items.slice(0, 3);
  }, [documents]);

  return (
    <div
      ref={scroller}
      onScroll={(event) => {
        const element = event.currentTarget;
        stickToBottom.current = element.scrollHeight - element.scrollTop - element.clientHeight < 120;
      }}
      className="flex-1 overflow-y-auto scroll-thin"
    >
      <div className="mx-auto w-full max-w-3xl px-4 py-6 sm:px-6 sm:py-8">
        {messages.length === 0 && documents.length === 0 && (
          <div className="mx-auto max-w-md animate-rise rounded-2xl border border-dashed border-line-strong bg-surface/70 p-7 text-center">
            <span className="mx-auto grid size-12 place-items-center rounded-2xl bg-brand-soft text-brand">
              <LibraryBig className="size-5" />
            </span>
            <h2 className="mt-3 text-base font-semibold">Start with a document</h2>
            <p className="mt-1 text-sm leading-relaxed text-muted">
              Upload a <span className="font-medium text-ink">.txt</span> or{' '}
              <span className="font-medium text-ink">.md</span> file from the sidebar, or paste text straight in.
            </p>
            <Button className="mt-4" onClick={onCreateDocument}>
              <FilePlus2 className="size-4" />
              Paste text
            </Button>
            <ol className="mt-6 space-y-2 text-left text-xs leading-relaxed text-muted">
              <li className="flex gap-2">
                <span className="font-mono text-brand">1</span>
                Text is split into overlapping chunks.
              </li>
              <li className="flex gap-2">
                <span className="font-mono text-brand">2</span>
                Every chunk is embedded on this machine.
              </li>
              <li className="flex gap-2">
                <span className="font-mono text-brand">3</span>
                Answers cite the exact passages they came from.
              </li>
            </ol>
          </div>
        )}

        {messages.length === 0 && documents.length > 0 && (
          <div className="mx-auto max-w-lg animate-rise text-center">
            <span className="mx-auto grid size-12 place-items-center rounded-2xl bg-brand-soft text-brand">
              <MessageSquareText className="size-5" />
            </span>
            <h2 className="mt-3 text-base font-semibold">Ready when you are</h2>
            <p className="mt-1 text-sm text-muted">
              {documents.length} document{documents.length === 1 ? '' : 's'} indexed. Ask something that is
              answered inside them.
            </p>
            <div className="mt-4 flex flex-col items-stretch gap-2">
              {suggestions.map((suggestion) => (
                <button
                  key={suggestion}
                  type="button"
                  onClick={() => onAsk(suggestion)}
                  className="rounded-xl border border-line bg-surface px-4 py-2.5 text-left text-sm text-muted transition hover:border-brand/60 hover:text-ink"
                >
                  {suggestion}
                </button>
              ))}
            </div>
          </div>
        )}

        <div className={cn('space-y-8', messages.length === 0 && 'hidden')}>
          {messages.map((message) => (
            <article key={message.id} className="animate-rise">
              <div className="flex justify-end">
                <div className="max-w-[88%] rounded-2xl rounded-br-md bg-brand px-4 py-2.5 text-sm text-brand-ink shadow-sm">
                  <p className="whitespace-pre-wrap">{message.question}</p>
                  <p className="mt-1 text-right text-[10px] opacity-70">{formatTime(message.createdAt)}</p>
                </div>
              </div>
              <div className="mt-3">
                <AnswerCard message={message} onRetry={onRetry} onOpenSource={onOpenSource} />
              </div>
            </article>
          ))}
        </div>
      </div>
    </div>
  );
}
