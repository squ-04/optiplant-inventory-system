import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiClient, ApiError } from '../src/lib/api/client.ts';
import { INVENTORY_POLL_MS, freshness } from '../src/lib/inventory/freshness.ts';

const branch = { externalId: '0e0f6ad5-6c55-4c55-9c52-3f2b8a4e1a10', code: 'NORTE', name: 'Sucursal Norte', active: true };
const inventory = {
  branch, readAt: '2026-10-26T15:00:02.000Z',
  items: [{ productExternalId: '4c0d6c6e-2a8e-4d0a-8f61-1c2b3d4e5f60', sku: 'SKU-1', name: 'Sustrato', baseUnitName: 'kg',
    physicalQuantity: '7.250', availableQuantity: '7.250', inTransitQuantity: '3.500' }],
};

function harness(responses: Response[]) {
  const urls: string[] = [];
  const methods: string[] = [];
  const client = new ApiClient('http://localhost:8080', async (input, options) => {
    urls.push(String(input)); methods.push(options?.method ?? 'GET');
    const response = responses.shift();
    assert.ok(response, 'unexpected request');
    return response;
  });
  return { client, urls, methods };
}

// RF-0014: the consulted branch is identified and quantities stay exact strings, transit apart.
test('branchInventory reads one branch with physical, available and transit kept apart', async () => {
  const h = harness([Response.json(inventory)]);
  assert.deepEqual(await h.client.branchInventory(branch.externalId), inventory);
  assert.deepEqual(h.urls, [`http://localhost:8080/api/v1/visibility/branches/${branch.externalId}/inventory`]);
  assert.deepEqual(h.methods, ['GET']);
});

test('branchInventory rejects malformed payloads instead of showing invented stock', async () => {
  for (const bad of [{ ...inventory, readAt: 'yesterday' }, { ...inventory, branch: { ...branch, externalId: 'x' } },
    { ...inventory, items: [{ ...inventory.items[0], inTransitQuantity: 3.5 }] },
    { ...inventory, items: [{ ...inventory.items[0], availableQuantity: '-1' }] }, { ...inventory, items: null }]) {
    await assert.rejects(harness([Response.json(bad)]).client.branchInventory(branch.externalId), /inválida/);
  }
  const missing = harness([Response.json({ code: 'BRANCH_NOT_FOUND', detail: 'No se encontró la sucursal.' }, { status: 404 })]);
  await assert.rejects(missing.client.branchInventory(branch.externalId),
    (error: unknown) => error instanceof ApiError && error.status === 404 && error.problem.code === 'BRANCH_NOT_FOUND');
});

// RF-0015: approved initial polling interval and outdated-data warning after communication loss.
test('freshness keeps the last read and flags it as potentially outdated after a failed refresh', () => {
  assert.equal(INVENTORY_POLL_MS, 2000);
  assert.deepEqual(freshness(undefined, undefined, false), { kind: 'loading' });
  assert.deepEqual(freshness(inventory.readAt, undefined, false), { kind: 'live', readAt: inventory.readAt });
  const failedAt = Date.parse('2026-10-26T15:00:06.000Z');
  assert.deepEqual(freshness(inventory.readAt, failedAt, true),
    { kind: 'stale', readAt: inventory.readAt, failedAt: '2026-10-26T15:00:06.000Z' });
  assert.deepEqual(freshness(undefined, failedAt, true), { kind: 'unavailable', failedAt: '2026-10-26T15:00:06.000Z' });
  // A later successful read clears the warning.
  assert.deepEqual(freshness('2026-10-26T15:00:08.000Z', failedAt, false), { kind: 'live', readAt: '2026-10-26T15:00:08.000Z' });
});

// RF-0015-CA-02: server-side outages (5xx) count as communication loss, keep polling and flag stale data;
// client rejections (401/403/404) stop polling and are reported by the shell.
test('connectionFailure keeps polling for network errors and 5xx, not for client rejections', async () => {
  const { connectionFailure } = await import('../src/lib/inventory/freshness.ts');
  assert.equal(connectionFailure(new TypeError('Failed to fetch')), true);
  for (const status of [500, 502, 503]) assert.equal(connectionFailure(new ApiError(status, {})), true);
  for (const status of [400, 401, 403, 404]) assert.equal(connectionFailure(new ApiError(status, {})), false);
});

// RF-0015-CA-02: a read that never answers is aborted and reported as communication loss instead of hanging.
test('branchInventory forwards an abort signal so a hung read fails as communication loss', async () => {
  const { connectionFailure } = await import('../src/lib/inventory/freshness.ts');
  const client = new ApiClient('http://localhost:8080', (_input, options) => new Promise((_resolve, reject) => {
    options?.signal?.addEventListener('abort', () => reject(options.signal?.reason));
  }));
  // Node does not keep the process alive for AbortSignal.timeout timers; abort with the same reason explicitly.
  const timeout = new AbortController();
  setTimeout(() => timeout.abort(new DOMException('The operation timed out.', 'TimeoutError')), 20);
  const error = await client.branchInventory(branch.externalId, timeout.signal).then(() => null, (reason: unknown) => reason);
  assert.ok(error instanceof DOMException && error.name === 'TimeoutError');
  assert.equal(connectionFailure(error), true);
});
