import type { ChatMessage } from '../types';

const THREAD_KEY = 'aka.thread.v1';
const THEME_KEY = 'aka.theme.v1';
const MAX_MESSAGES = 40;

export type Theme = 'light' | 'dark';

export function loadThread(): ChatMessage[] {
  try {
    const raw = localStorage.getItem(THREAD_KEY);
    if (!raw) {
      return [];
    }
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed
      .filter((item): item is ChatMessage => {
        if (!item || typeof item !== 'object') {
          return false;
        }
        const message = item as Partial<ChatMessage>;
        return typeof message.id === 'string' && typeof message.question === 'string' && message.status !== 'pending';
      })
      .slice(-MAX_MESSAGES);
  } catch {
    return [];
  }
}

export function saveThread(messages: ChatMessage[]): void {
  try {
    const settled = messages.filter((message) => message.status !== 'pending').slice(-MAX_MESSAGES);
    localStorage.setItem(THREAD_KEY, JSON.stringify(settled));
  } catch {
    // Storage can be unavailable (private mode); the chat still works in memory.
  }
}

export function loadTheme(): Theme {
  try {
    const stored = localStorage.getItem(THEME_KEY);
    if (stored === 'light' || stored === 'dark') {
      return stored;
    }
  } catch {
    // ignore
  }
  return window.matchMedia?.('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
}

export function saveTheme(theme: Theme): void {
  try {
    localStorage.setItem(THEME_KEY, theme);
  } catch {
    // ignore
  }
}
