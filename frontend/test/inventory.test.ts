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
