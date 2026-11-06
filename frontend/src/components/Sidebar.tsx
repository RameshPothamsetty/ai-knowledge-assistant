import { useEffect, useMemo, useRef, useState } from 'react';
import { BookOpen, Check, FilePlus2, FileText, Layers, Search, Trash2, UploadCloud, X } from 'lucide-react';
import type { DocumentView } from '../types';
import { cn, formatDate } from '../lib/utils';
import { useMediaQuery } from '../lib/useMediaQuery';
import { Button } from './ui';

interface SidebarProps {
  documents: DocumentView[] | undefined;
  isLoading: boolean;
  error: string | null;
  busy: boolean;
  embeddingModel?: string;
  open: boolean;
  onClose: () => void;
  onOpenPaste: () => void;
  onUpload: (file: File) => void;
  onDelete: (id: number) => void;
  onPreview: (document: DocumentView) => void;
}

export function Sidebar({
  documents,
  isLoading,
  error,
  busy,
  embeddingModel,
  open,
  onClose,
  onOpenPaste,
  onUpload,
  onDelete,
  onPreview,
}: SidebarProps) {
  const [query, setQuery] = useState('');
  const [dragging, setDragging] = useState(false);
  const [confirmId, setConfirmId] = useState<number | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);
  const closeButton = useRef<HTMLButtonElement>(null);

  // The drawer is fixed and off-canvas below lg. While it is hidden there it
  // must leave the tab order and the accessibility tree; on lg it is always on
  // screen, so the same state means nothing.
  const isDesktop = useMediaQuery('(min-width: 1024px)');
  const hidden = !isDesktop && !open;

  const onCloseRef = useRef(onClose);
  useEffect(() => {
    onCloseRef.current = onClose;
  }, [onClose]);

  useEffect(() => {
    if (!open || isDesktop) {
      return;
    }
    closeButton.current?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onCloseRef.current();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, isDesktop]);

  const filtered = useMemo(() => {
    const needle = query.trim().toLowerCase();
    const list = documents ?? [];
    if (!needle) {
      return list;
    }
    return list.filter((document) =>
      `${document.title} ${document.sourceName ?? ''}`.toLowerCase().includes(needle),
    );
  }, [documents, query]);

  const total = documents?.length ?? 0;

  return (
    <>
      {open && (
        <div
          className="fixed inset-0 z-30 bg-slate-950/55 backdrop-blur-sm lg:hidden"
          onClick={onClose}
          aria-hidden="true"
        />
      )}

      <aside
        id="document-library"
        className={cn(
          'fixed inset-y-0 left-0 z-30 flex w-80 max-w-[86vw] shrink-0 flex-col border-r border-line bg-surface transition-transform duration-300 lg:static lg:z-auto lg:translate-x-0',
          open ? 'translate-x-0' : '-translate-x-full',
        )}
        aria-label="Document library"
        aria-hidden={hidden || undefined}
        inert={hidden || undefined}
      >
        <div className="flex items-center gap-2.5 px-4 pb-4 pt-5">
          <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-brand text-brand-ink">
            <BookOpen className="size-4.5" />
          </span>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-semibold">Knowledge Assistant</p>
            <p className="truncate text-xs text-muted">Answers from your documents</p>
          </div>
          <button
            ref={closeButton}
            type="button"
            onClick={onClose}
            className="grid size-8 place-items-center rounded-lg text-muted hover:bg-surface-2 hover:text-ink lg:hidden"
            aria-label="Close document library"
          >
            <X className="size-4" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto scroll-thin px-4 pb-4">
          <label className="relative block">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted" />
            <input
              type="search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search documents"
              aria-label="Search documents"
              className="w-full rounded-lg border border-line bg-surface-2 py-2 pl-9 pr-3 text-sm placeholder:text-muted focus:border-brand"
            />
          </label>

          <div
            onDragOver={(event) => {
              event.preventDefault();
              setDragging(true);
            }}
            onDragLeave={() => setDragging(false)}
            onDrop={(event) => {
              event.preventDefault();
              setDragging(false);
              const file = event.dataTransfer.files?.[0];
              if (file) {
                onUpload(file);
              }
            }}
            className={cn(
              'mt-3 rounded-xl border border-dashed px-4 py-4 text-center transition',
              dragging ? 'border-brand bg-brand-soft' : 'border-line-strong bg-surface-2/60',
            )}
          >
            <UploadCloud className={cn('mx-auto mb-1.5 size-5', dragging ? 'text-brand' : 'text-muted')} />
            <p className="text-xs text-muted">
              Drop a <span className="font-medium text-ink">.txt</span> or{' '}
              <span className="font-medium text-ink">.md</span> file
            </p>
            <button
              type="button"
              onClick={() => fileInput.current?.click()}
              className="mt-1 text-xs font-medium text-brand hover:underline"
            >
              or browse files
            </button>
            <input
              ref={fileInput}
              type="file"
              accept=".txt,.md,.markdown"
              className="hidden"
              onChange={(event) => {
                const file = event.target.files?.[0];
                if (file) {
                  onUpload(file);
                }
                event.target.value = '';
              }}
            />
          </div>

          <Button variant="subtle" className="mt-2 w-full" onClick={onOpenPaste} disabled={busy}>
            <FilePlus2 className="size-4" />
            Paste text
          </Button>

          <div className="mt-5 flex items-center justify-between px-1">
            <h2 id="documents-heading" className="text-[11px] font-semibold uppercase tracking-wider text-muted">
              Documents
            </h2>
            <span className="text-[11px] text-muted">{total}</span>
          </div>

          {error && <p className="mt-3 rounded-lg bg-danger-soft px-3 py-2 text-xs text-danger">{error}</p>}

          {isLoading && (
            <div className="mt-3 space-y-2" aria-hidden="true">
              {[0, 1, 2].map((row) => (
                <div key={row} className="animate-pulse rounded-xl bg-surface-2 px-3 py-3">
                  <div className="h-3 w-2/3 rounded bg-line" />
                  <div className="mt-2 h-2 w-1/2 rounded bg-line" />
                </div>
              ))}
            </div>
          )}

          {!isLoading && filtered.length === 0 && (
            <p className="mt-4 px-1 text-xs leading-relaxed text-muted">
              {total === 0
                ? 'No documents yet. Upload a file or paste text to get started.'
                : 'No document matches that search.'}
            </p>
          )}

          <ul className="mt-2 space-y-1" aria-labelledby="documents-heading" aria-busy={isLoading}>
            {filtered.map((document) => (
              <li
                key={document.id}
                className="group rounded-xl border border-transparent px-2.5 py-2.5 transition hover:border-line hover:bg-surface-2"
              >
                <div className="flex items-start gap-2">
                  <FileText className="mt-0.5 size-4 shrink-0 text-muted" />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium" title={document.title}>
                      {document.title}
                    </p>
                    <p className="mt-0.5 truncate text-[11px] text-muted">
                      {document.chunkCount} chunk{document.chunkCount === 1 ? '' : 's'}
                      {document.sourceName ? ` · ${document.sourceName}` : ''}
                    </p>
                    <p className="text-[11px] text-muted">{formatDate(document.createdAt)}</p>
                  </div>

                  <div className="flex shrink-0 items-center gap-0.5">
                    <button
                      type="button"
                      onClick={() => onPreview(document)}
                      className="grid size-7 place-items-center rounded-md text-muted transition hover:bg-surface hover:text-ink"
                      aria-label={`Preview chunks of ${document.title}`}
                      title="Preview chunks"
                    >
                      <Layers className="size-3.5" />
                    </button>

                    {confirmId === document.id ? (
                      <>
                        <button
                          type="button"
                          onClick={() => {
                            onDelete(document.id);
                            setConfirmId(null);
                          }}
                          className="grid size-7 place-items-center rounded-md text-danger hover:bg-danger-soft"
                          aria-label={`Confirm deleting ${document.title}`}
                        >
                          <Check className="size-3.5" />
                        </button>
                        <button
                          type="button"
                          onClick={() => setConfirmId(null)}
                          className="grid size-7 place-items-center rounded-md text-muted hover:bg-surface hover:text-ink"
                          aria-label="Cancel delete"
                        >
                          <X className="size-3.5" />
                        </button>
                      </>
                    ) : (
                      <button
                        type="button"
                        onClick={() => setConfirmId(document.id)}
                        className="grid size-7 shrink-0 place-items-center rounded-md text-muted opacity-100 transition hover:bg-danger-soft hover:text-danger lg:opacity-0 lg:group-hover:opacity-100 lg:focus-visible:opacity-100"
                        aria-label={`Delete ${document.title}`}
                      >
                        <Trash2 className="size-3.5" />
                      </button>
                    )}
                  </div>
                </div>
              </li>
            ))}
          </ul>
        </div>

        <div className="border-t border-line px-4 py-3">
          <p className="flex items-center gap-1.5 truncate text-[11px] text-muted" title={embeddingModel}>
            <span className="size-1.5 shrink-0 rounded-full bg-ok" />
            Local pipeline
            {embeddingModel ? <span className="truncate">· {embeddingModel}</span> : null}
          </p>
        </div>
      </aside>
    </>
  );
}
