import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiClient, ApiError } from '../src/lib/api/client.ts';
import { alertsNavigationLabel, restockNotice } from '../src/lib/inventory/alerts.ts';

const branch = { externalId: '7c9e6679-7425-40de-944b-e07fc1f90ae7', code: 'NORTH', name: 'Norte', active: true };
const active = { externalId: 'df06cdfe-a745-4bf4-bc81-e10db132724b', status: 'ACTIVE', branch,
  product: { externalId: '3b241101-e2bb-4255-8caf-4136c566a962', sku: 'SKU-1', name: 'Helecho', baseUnitName: 'unidad' },
  openedAt: '2026-10-26T10:00:00Z', resolvedAt: null, minimumAtOpening: '5.000', quantityAtOpening: '5.000',
  currentMinimum: '5.000', currentQuantity: '4.000' };
const resolved = { ...active, externalId: '9b2e6b7a-1c3d-4e5f-8a9b-0c1d2e3f4a5b', status: 'RESOLVED',
  resolvedAt: '2026-10-26T11:00:00Z', currentMinimum: null };
const payload = { readAt: '2026-10-26T12:00:00Z', branch, activeCount: 1, items: [active, resolved] };

function harness(values: unknown[]) {
  const calls: string[] = [];
  const client = new ApiClient('http://localhost:8080', async (url, options) => {
    calls.push(`${options?.method} ${String(url).split('/api/v1')[1]}`);
    const value = values.shift();
    if (value instanceof Response) return value;
    return Response.json(value);
  });
  return { client, calls };
}

test('reorder alerts use the real contract with scope, history and exact strings', async () => {
  const h = harness([payload, { ...payload, branch: null }]);
  assert.deepEqual(await h.client.reorderAlerts({ branch: branch.externalId, history: true }), payload);
  assert.equal((await h.client.reorderAlerts()).branch, null);
  assert.deepEqual(h.calls, [`GET /inventory/reorder-alerts?branch=${branch.externalId}&status=ALL`, 'GET /inventory/reorder-alerts']);
});

test('malformed alert payloads are rejected instead of shown', async () => {
  for (const bad of [{ ...payload, activeCount: -1 }, { ...payload, activeCount: '1' }, { ...payload, items: null },
    { ...payload, items: [{ ...active, status: 'OPEN' }] }, { ...payload, items: [{ ...active, resolvedAt: '2026-10-26T11:00:00Z' }] },
    { ...payload, items: [{ ...resolved, resolvedAt: null }] }, { ...payload, items: [{ ...active, currentQuantity: 4 }] },
    { ...payload, items: [{ ...active, branch: null }] }]) {
    await assert.rejects(harness([bad]).client.reorderAlerts(), /inválida/);
  }
});

test('operators are rejected by the server and keep the local catalog indicator', async () => {
  const h = harness([Response.json({ code: 'ROLE_FORBIDDEN', detail: 'Las alertas de reabastecimiento corresponden a gerentes y administradores.' },
    { status: 403 })]);
  await assert.rejects(h.client.reorderAlerts(), (error: unknown) => error instanceof ApiError && error.status === 403
    && error.problem.code === 'ROLE_FORBIDDEN');
});

test('the persistent notice and navigation name state the count in words', () => {
  assert.equal(restockNotice(0, true), null);
  assert.equal(restockNotice(1, false), '1 producto en su sucursal está en su stock mínimo o por debajo.');
  assert.equal(restockNotice(3, true), '3 productos en la red están en su stock mínimo o por debajo.');
  assert.equal(alertsNavigationLabel(0), 'Alertas de reabastecimiento, sin alertas activas');
  assert.equal(alertsNavigationLabel(2), 'Alertas de reabastecimiento, 2 activas');
});
