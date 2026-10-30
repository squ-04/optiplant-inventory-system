import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../api/client.ts';
import { discardPending, loadLatest, requestGate } from '../latestRequest.ts';

const OUTDATED = 'Los datos mostrados pueden estar desactualizados.';

/** The message of a failed reading; figures still on screen are declared possibly outdated. */
export function failureMessage(reason: unknown, fallback: string, shown: boolean): string {
  if (reason instanceof TypeError) return `Sin comunicación con el servidor. ${OUTDATED}`;
  const message = reason instanceof Error ? reason.message : fallback;
  return shown ? `${message} ${OUTDATED}` : message;
}

/** Turns a failed reading into the message of its own section; an expired session goes to the shell. */
export function failure(onError: (reason: unknown) => void, set: (message: string) => void, fallback: string, shown: () => boolean) {
  return (reason: unknown) => {
    if (reason instanceof ApiError && reason.status === 401) onError(reason);
    else set(failureMessage(reason, fallback, shown()));
  };
}

interface Reading<T> {
  read: () => Promise<T>;
  /** What is being read (branch, month): when it changes, the previous figures and any late answer are discarded. */
  subject: string;
  active: boolean;
  revision: number;
  onError: (reason: unknown) => void;
  fallback: string;
}

/** One section of the dashboard: reads while visible, keeps the figures shown during a refresh and fails on its own. */
export function useReading<T>({ read, subject, active, revision, onError, fallback }: Reading<T>) {
  const [value, setValue] = useState<T | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const gate = useRef(requestGate());
  const shown = useRef(subject);
  const current = useRef(value);
  current.current = value;
  const reload = () => loadLatest(gate.current, read, {
    start: () => { setLoading(true); setError(''); },
    value: setValue,
    error: failure(onError, setError, fallback, () => current.current !== null),
    done: () => setLoading(false),
  });
  useEffect(() => {
    if (shown.current !== subject) {
      shown.current = subject;
      discardPending(gate.current);
      setValue(null); setError(''); setLoading(false);
    }
    if (active) void reload();
  }, [subject, active, revision]);
  return { value, loading, error, reload };
}
