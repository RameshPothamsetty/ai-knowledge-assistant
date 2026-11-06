import { useRef, useState, type KeyboardEvent } from 'react';
import { ArrowUp, Loader2 } from 'lucide-react';

interface ComposerProps {
  onAsk: (question: string) => void;
  asking: boolean;
  topK: number;
  maxTopK: number;
  onTopKChange: (value: number) => void;
  documentCount: number;
}

export function Composer({ onAsk, asking, topK, maxTopK, onTopKChange, documentCount }: ComposerProps) {
  const [value, setValue] = useState('');
  const textarea = useRef<HTMLTextAreaElement>(null);

  function resize() {
    const element = textarea.current;
    if (!element) {
      return;
    }
    element.style.height = 'auto';
    element.style.height = `${Math.min(element.scrollHeight, 160)}px`;
  }

  function send() {
    const question = value.trim();
    if (!question || asking) {
      return;
    }
    onAsk(question);
    setValue('');
    if (textarea.current) {
      textarea.current.style.height = 'auto';
    }
  }

  function onKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      send();
    }
  }

  return (
    <div className="border-t border-line bg-canvas/85 px-4 pb-3 pt-3 backdrop-blur sm:px-6">
      <div className="mx-auto w-full max-w-3xl">
        <form
          onSubmit={(event) => {
            event.preventDefault();
            send();
          }}
          className="flex items-end gap-2 rounded-2xl border border-line bg-surface p-2 shadow-sm transition focus-within:border-brand/60"
        >
          <label htmlFor="question" className="sr-only">
            Question
          </label>
          <textarea
            id="question"
            ref={textarea}
            value={value}
            onChange={(event) => {
              setValue(event.target.value);
              resize();
            }}
            onKeyDown={onKeyDown}
            rows={1}
            maxLength={1000}
            placeholder={
              documentCount > 0
                ? 'Ask a question about your documents…'
                : 'Add a document first, then ask a question…'
            }
            className="max-h-40 min-h-10 flex-1 resize-none bg-transparent px-2 py-2 text-sm leading-relaxed placeholder:text-muted"
          />
          <button
            type="submit"
            disabled={!value.trim() || asking}
            aria-label="Send question"
            className="grid size-9 shrink-0 place-items-center rounded-xl bg-brand text-brand-ink transition hover:bg-brand-hover disabled:opacity-40"
          >
            {asking ? <Loader2 className="size-4 animate-spin" /> : <ArrowUp className="size-4" />}
          </button>
        </form>

        <div className="mt-1.5 flex items-center justify-between gap-3 px-1">
          <p className="hidden text-[11px] text-muted sm:block">
            Enter to send · Shift + Enter for a new line
          </p>
          <div className="flex items-center gap-3 text-[11px] text-muted">
            <span className="hidden sm:inline">
              {documentCount} document{documentCount === 1 ? '' : 's'} indexed
            </span>
            <label className="flex items-center gap-1.5">
              Passages
              <select
                value={topK}
                onChange={(event) => onTopKChange(Number(event.target.value))}
                className="rounded-md border border-line bg-surface px-1.5 py-0.5 text-[11px] text-ink focus:border-brand"
                aria-label="Number of passages to retrieve"
              >
                {Array.from({ length: Math.max(1, maxTopK) }, (_, index) => index + 1).map((count) => (
                  <option key={count} value={count}>
                    {count}
                  </option>
                ))}
              </select>
            </label>
          </div>
        </div>
      </div>
    </div>
  );
}
