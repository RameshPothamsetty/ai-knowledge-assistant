import { useEffect, useRef, useState, type FormEvent } from 'react';
import { FilePlus2, X } from 'lucide-react';
import { Button, Spinner } from './ui';

interface PasteDialogProps {
  open: boolean;
  busy: boolean;
  onClose: () => void;
  onSubmit: (title: string, content: string) => void;
}

export function PasteDialog({ open, busy, onClose, onSubmit }: PasteDialogProps) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    const element = dialog.current;
    if (!element) {
      return;
    }
    if (open && !element.open) {
      setTitle('');
      setContent('');
      setError('');
      element.showModal();
    }
    if (!open && element.open) {
      element.close();
    }
  }, [open]);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!title.trim() || !content.trim()) {
      setError('A title and some text are both required.');
      return;
    }
    setError('');
    onSubmit(title.trim(), content.trim());
  }

  return (
    <dialog
      ref={dialog}
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      onClose={onClose}
      onClick={(event) => {
        if (event.target === dialog.current) {
          onClose();
        }
      }}
      className="m-auto w-[min(38rem,calc(100vw-2rem))] rounded-2xl border border-line bg-surface p-0 text-ink shadow-2xl backdrop:bg-slate-950/60 backdrop:backdrop-blur-sm"
      aria-labelledby="paste-title"
    >
      <form onSubmit={submit} className="p-5">
        <div className="flex items-start gap-3">
          <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-brand-soft text-brand">
            <FilePlus2 className="size-4" />
          </span>
          <div className="min-w-0 flex-1">
            <h2 id="paste-title" className="text-sm font-semibold">
              Add text to the knowledge base
            </h2>
            <p className="mt-0.5 text-xs text-muted">
              The text is chunked, embedded and stored locally. It never leaves this machine.
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="grid size-8 shrink-0 place-items-center rounded-lg text-muted hover:bg-surface-2 hover:text-ink"
            aria-label="Close dialog"
          >
            <X className="size-4" />
          </button>
        </div>

        <label htmlFor="paste-doc-title" className="mt-4 block text-xs font-medium text-muted">
          Title
        </label>
        <input
          id="paste-doc-title"
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          maxLength={200}
          placeholder="Refund policy"
          className="mt-1 w-full rounded-lg border border-line bg-surface-2 px-3 py-2 text-sm placeholder:text-muted focus:border-brand"
        />

        <label htmlFor="paste-doc-content" className="mt-3 block text-xs font-medium text-muted">
          Text
        </label>
        <textarea
          id="paste-doc-content"
          value={content}
          onChange={(event) => setContent(event.target.value)}
          rows={8}
          maxLength={200_000}
          placeholder="Paste the text you want to make searchable…"
          className="mt-1 max-h-[40vh] w-full resize-y rounded-lg border border-line bg-surface-2 px-3 py-2 text-sm leading-relaxed placeholder:text-muted focus:border-brand"
        />
        <p className="mt-1 text-right text-[11px] text-muted">
          {content.length.toLocaleString()} / 200,000 characters
        </p>

        {error && (
          <p role="alert" className="mt-2 text-xs text-danger">
            {error}
          </p>
        )}

        <div className="mt-4 flex items-center justify-end gap-2">
          <Button variant="ghost" onClick={onClose} disabled={busy}>
            Cancel
          </Button>
          <Button type="submit" disabled={busy}>
            {busy && <Spinner className="size-3.5" />}
            Save and embed
          </Button>
        </div>
      </form>
    </dialog>
  );
}
