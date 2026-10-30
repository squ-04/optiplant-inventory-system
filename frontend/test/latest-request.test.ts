import assert from 'node:assert/strict';
import test from 'node:test';
import { discardPending, loadLatest, requestGate } from '../src/lib/latestRequest.ts';

/** A promise resolved or rejected by the test, to decide which response arrives first. */
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((ok, fail) => { resolve = ok; reject = fail; });
  return { promise, resolve, reject };
}

function recorder() {
  const events: string[] = [];
  return { events, handlers: {
    start: () => events.push('start'),
    value: (value: string) => events.push(`value:${value}`),
    error: (reason: unknown) => events.push(`error:${String(reason)}`),
    done: () => events.push('done'),
  } };
}

test('a slower earlier response never replaces the latest one', async () => {
  const gate = requestGate();
  const { events, handlers } = recorder();
  const october = deferred<string>(), november = deferred<string>();
  const first = loadLatest(gate, () => october.promise, handlers);
  const second = loadLatest(gate, () => november.promise, handlers);
  november.resolve('noviembre');
  await second;
  october.resolve('octubre');
  await first;
  assert.deepEqual(events, ['start', 'start', 'value:noviembre', 'done']);
});

test('the error and the end of loading of a stale request are discarded too', async () => {
  const gate = requestGate();
  const { events, handlers } = recorder();
  const stale = deferred<string>(), latest = deferred<string>();
  const first = loadLatest(gate, () => stale.promise, handlers);
  const second = loadLatest(gate, () => latest.promise, handlers);
  stale.reject('sin conexión');
  await first;
  // Still loading: the stale failure neither shows an error nor ends the loading state of the latest request.
  assert.deepEqual(events, ['start', 'start']);
  latest.resolve('noviembre');
  await second;
  assert.deepEqual(events, ['start', 'start', 'value:noviembre', 'done']);
});

test('in order, every response applies; the latest failure is shown', async () => {
  const gate = requestGate();
  const { events, handlers } = recorder();
  await loadLatest(gate, async () => 'octubre', handlers);
  await loadLatest(gate, () => Promise.reject('sin conexión'), handlers);
  assert.deepEqual(events, ['start', 'value:octubre', 'done', 'start', 'error:sin conexión', 'done']);
});

test('separate views keep separate gates', async () => {
  const reports = requestGate(), other = requestGate();
  const { events, handlers } = recorder();
  const slow = deferred<string>();
  const first = loadLatest(reports, () => slow.promise, handlers);
  await loadLatest(other, async () => 'otra vista', handlers);
  slow.resolve('reporte');
  await first;
  assert.deepEqual(events, ['start', 'start', 'value:otra vista', 'done', 'value:reporte', 'done']);
});

test('a request discarded without a replacement never updates the view', async () => {
  const gate = requestGate();
  const { events, handlers } = recorder();
  const previous = deferred<string>();
  const pending = loadLatest(gate, () => previous.promise, handlers);
  // The administrator went back to «Seleccione una sucursal»: nothing new is requested.
  discardPending(gate);
  previous.resolve('Medellín');
  await pending;
  assert.deepEqual(events, ['start']);
});
