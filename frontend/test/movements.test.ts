import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiClient, ApiError } from '../src/lib/api/client.ts';
import type { MovementHistoryEntry } from '../src/lib/api/client.ts';
import {
  asksUnitCost, keepsIdempotencyKey, kindLabel, kindsFor, referenceCandidates, signedQuantity,
} from '../src/lib/inventory/movements.ts';

const branch = { externalId: '0e0f6ad5-6c55-4c55-9c52-3f2b8a4e1a10', code: 'NORTE', name: 'Sucursal Norte', active: true };
const movement = {
  operationExternalId: '1b6f0d9e-4a2c-4f3e-9d1a-2b3c4d5e6f70', movementExternalId: '2c7a1e0f-5b3d-4a4f-8e2b-3c4d5e6f7a81',
  occurredAt: '2026-10-26T15:00:02.000Z', kind: 'MANUAL_EXIT', direction: 'OUT',
  productExternalId: '4c0d6c6e-2a8e-4d0a-8f61-1c2b3d4e5f60', sku: 'SKU-1', productName: 'Sustrato', unit: 'caja',
  commercialQuantity: '1.000', baseQuantity: '12.000', baseUnitName: 'kg', reason: 'Merma por humedad',
  responsibleCode: 'OP-1', referenceExternalId: null, resultingPhysicalQuantity: '0.500',
};
const entry = (patch: Partial<MovementHistoryEntry>): MovementHistoryEntry => ({
  externalId: '3d8b2f1a-6c4e-4b5a-9f3c-4d5e6f7a8b92', operationExternalId: movement.operationExternalId,
  occurredAt: movement.occurredAt, kind: 'MANUAL_ENTRY', direction: 'IN', productExternalId: movement.productExternalId,
  sku: 'SKU-1', productName: 'Sustrato', unit: 'kg', commercialQuantity: '10.000', baseQuantity: '10.000',
  baseUnitName: 'kg', reason: 'Ingreso inicial', responsible: { code: 'OP-1', fullName: 'Operador Norte' },
  referenceExternalId: null, pendingQuantity: '10.000', valuation: null, ...patch,
});

function harness(responses: Response[]) {
  const calls: { url: string; options: RequestInit }[] = [];
  const client = new ApiClient('http://localhost:8080', async (input, options) => {
    calls.push({ url: String(input), options: options ?? {} });
    const response = responses.shift();
    assert.ok(response, 'unexpected request');
    return response;
  });
  return { client, calls };
}

test('registerMovement sends the exact body with CSRF and the idempotency key', async () => {
  const h = harness([Response.json({ token: 't', headerName: 'X-CSRF-TOKEN' }), Response.json(movement, { status: 201 })]);
  const input = { kind: 'MANUAL_EXIT' as const, product: movement.productExternalId, unit: 'caja', quantity: '1', reason: 'Merma por humedad' };
  assert.deepEqual(await h.client.registerMovement(input, 'key-1'), movement);
  const headers = new Headers(h.calls[1].options.headers);
  assert.equal(h.calls[1].url, 'http://localhost:8080/api/v1/inventory/movements');
  assert.equal(h.calls[1].options.method, 'POST');
  assert.equal(headers.get('Idempotency-Key'), 'key-1');
  assert.equal(headers.get('X-CSRF-TOKEN'), 't');
  assert.deepEqual(JSON.parse(String(h.calls[1].options.body)), input);
});

test('registerMovement surfaces the server rejection and rejects malformed confirmations', async () => {
  const h = harness([
    Response.json({ token: 't', headerName: 'X-CSRF-TOKEN' }),
    Response.json({ code: 'INSUFFICIENT_STOCK', detail: 'La salida excede las existencias.' }, { status: 409 }),
    Response.json({ ...movement, baseQuantity: 12 }, { status: 201 }),
  ]);
  const input = { kind: 'MANUAL_EXIT' as const, quantity: '1', reason: 'x' };
  await assert.rejects(h.client.registerMovement(input, 'k'), (error: unknown) =>
    error instanceof ApiError && error.status === 409 && error.problem.code === 'INSUFFICIENT_STOCK'
    && error.message === 'La salida excede las existencias.');
  await assert.rejects(h.client.registerMovement(input, 'k'), /Respuesta JSON inválida/);
});

test('movementHistory reads a branch with optional filters and keeps signed valuation strings', async () => {
  const valued = entry({ kind: 'ENTRY_CORRECTION', direction: 'OUT', pendingQuantity: null, referenceExternalId: movement.movementExternalId,
    valuation: { unitCost: '10.000000', baseAmount: '-20.00', differenceKind: 'HISTORICAL_CORRECTION', differenceAmount: '-10.00',
      exactDifferenceAmount: '-10', totalAmount: '-30.00' } });
  const body = { branch, readAt: '2026-10-26T15:00:03.000Z', items: [valued, entry({})] };
  const h = harness([Response.json(body), Response.json({ ...body, items: [] })]);
  assert.deepEqual(await h.client.movementHistory(branch.externalId), body);
  await h.client.movementHistory(branch.externalId, { product: movement.productExternalId, limit: 50 });
  assert.equal(h.calls[0].url, `http://localhost:8080/api/v1/inventory/branches/${branch.externalId}/movements`);
  assert.equal(h.calls[1].url, `http://localhost:8080/api/v1/inventory/branches/${branch.externalId}/movements`
    + `?product=${movement.productExternalId}&limit=50`);
});

test('movementHistory rejects entries without the audit data of RF-0018', async () => {
  for (const broken of [{ reason: '' }, { responsible: { code: 'OP-1' } }, { occurredAt: 'yesterday' }, { commercialQuantity: '-1' }]) {
    const h = harness([Response.json({ branch, readAt: '2026-10-26T15:00:03.000Z', items: [{ ...entry({}), ...broken }] })]);
    await assert.rejects(h.client.movementHistory(branch.externalId), /Respuesta JSON inválida/);
  }
});

test('each role is offered only the movements it may register (INC-009/D03)', () => {
  assert.deepEqual(kindsFor('OPERATOR').map(k => k.kind), ['MANUAL_ENTRY', 'SALE_RETURN', 'MANUAL_EXIT']);
  assert.deepEqual(kindsFor('MANAGER').map(k => k.kind), ['COUNT_ADJUSTMENT', 'ENTRY_CORRECTION']);
  // Decision L14: only count adjustments choose a direction; corrections always decrease.
  assert.deepEqual(kindsFor('MANAGER').map(k => k.direction), [null, 'OUT']);
  assert.deepEqual(kindsFor('ADMIN'), []);
  assert.equal(kindLabel('PURCHASE_RECEIPT'), 'Recepción de compra');
  assert.equal(signedQuantity({ direction: 'OUT', commercialQuantity: '2.500' }), '−2.500');
  assert.equal(signedQuantity({ direction: 'IN', commercialQuantity: '2.500' }), '+2.500');
});

test('compensations reference only movements that still admit them', () => {
  const history = [
    entry({ externalId: 'a1', kind: 'SALE', direction: 'OUT', pendingQuantity: '2.000' }),
    entry({ externalId: 'a2', kind: 'SALE', direction: 'OUT', pendingQuantity: '0.000' }),
    entry({ externalId: 'a3', kind: 'MANUAL_ENTRY', pendingQuantity: '0.000' }),
    entry({ externalId: 'a4', kind: 'PURCHASE_RECEIPT', pendingQuantity: '4.000' }),
    entry({ externalId: 'a5', kind: 'COUNT_ADJUSTMENT', pendingQuantity: null }),
  ];
  assert.deepEqual(referenceCandidates(history, 'SALE_RETURN').map(i => i.externalId), ['a1']);
  assert.deepEqual(referenceCandidates(history, 'ENTRY_CORRECTION').map(i => i.externalId), ['a4']);
  assert.deepEqual(referenceCandidates(history, 'MANUAL_ENTRY'), []);
});

test('a justified unit cost is asked only for entries without reference and without stock', () => {
  assert.equal(asksUnitCost('MANUAL_ENTRY', 'IN', false, '0.000'), true);
  assert.equal(asksUnitCost('MANUAL_ENTRY', 'IN', false, '0.500'), false);
  assert.equal(asksUnitCost('SALE_RETURN', 'IN', true, '0.000'), false);
  assert.equal(asksUnitCost('SALE_RETURN', 'IN', false, '0.000'), true);
  assert.equal(asksUnitCost('COUNT_ADJUSTMENT', 'IN', false, '0.000'), true);
  assert.equal(asksUnitCost('COUNT_ADJUSTMENT', 'OUT', false, '0.000'), false);
  assert.equal(asksUnitCost('MANUAL_EXIT', 'OUT', false, '0.000'), false);
});

test('the idempotency key survives only a retry whose outcome is unknown', () => {
  assert.equal(keepsIdempotencyKey(new TypeError('Failed to fetch')), true);
  assert.equal(keepsIdempotencyKey(new ApiError(502, {})), true);
  assert.equal(keepsIdempotencyKey(new ApiError(409, { code: 'INSUFFICIENT_STOCK' })), false);
  assert.equal(keepsIdempotencyKey(new ApiError(400, {})), false);
});
