import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiClient, ApiError } from '../src/lib/api/client.ts';
import { actionFor, dueDate, lineNet, money, paymentTerm, purchaseStatusLabel, sum } from '../src/lib/purchases/purchases.ts';

test('line net follows the stored formula exactly, without rounding', () => {
  // RF-0023-CA-02: a box at 120 with 10% discount nets 108.
  assert.equal(lineNet('1', '120', '10'), '108');
  assert.equal(lineNet('2', '120', '10'), '216');
  assert.equal(lineNet('3', '3.5', '0'), '10.5');
  assert.equal(lineNet('1', '120', '100'), '0');
  // Data model §5.1 example: 17 decimals kept, displayed 0.00 rather than 0.01.
  assert.equal(lineNet('0.001', '5.000001', '0.00002'), '0.0049999999999998');
  assert.equal(money('0.0049999999999998'), '0.00');
});

test('line net is unavailable while an operand is incomplete or the discount exceeds 100', () => {
  assert.equal(lineNet('', '120', '10'), null);
  assert.equal(lineNet('1', '1,5', '0'), null);
  assert.equal(lineNet('1', '120', '100.5'), null);
});

test('sums and money keep exact strings and round half up to 2 decimals', () => {
  assert.equal(sum(['216', '17.5']), '233.5');
  assert.equal(sum([]), '0');
  assert.equal(money('233.5'), '233.50');
  assert.equal(money('7'), '7.00');
  assert.equal(money('1.005'), '1.01');
  assert.equal(money('0.125'), '0.13');
});

test('only the operator executes transitions, and only from draft or pending', () => {
  assert.equal(actionFor('OPERATOR', { status: 'DRAFT' }), 'submit');
  assert.equal(actionFor('OPERATOR', { status: 'PENDING' }), 'receive');
  assert.equal(actionFor('OPERATOR', { status: 'RECEIVED' }), null);
  assert.equal(actionFor('OPERATOR', { status: 'CANCELLED' }), null);
  assert.equal(actionFor('MANAGER', { status: 'PENDING' }), null);
  assert.equal(actionFor('ADMIN', { status: 'DRAFT' }), null);
});

test('payment term is a whole number of days, zero allowed', () => {
  assert.equal(paymentTerm('0'), 0);
  assert.equal(paymentTerm(' 30 '), 30);
  assert.equal(paymentTerm('1,5'), null);
  assert.equal(paymentTerm('-1'), null);
  assert.equal(paymentTerm('36501'), null);
});

test('labels and due dates read as the interface shows them', () => {
  assert.equal(purchaseStatusLabel('PENDING'), 'Pendiente de recepción');
  assert.equal(dueDate('2026-11-26'), '26/11/2026');
});


const branch = { externalId: '5b0f8c1e-9a64-4f39-8d7e-1d2b3c4d5e6f', code: 'A', name: 'Sucursal A', active: true };
const person = { code: 'OP-1', fullName: 'Operadora Uno' };
const line = {
  lineNo: 1, productExternalId: '0d4e9c2a-6b7f-4a1d-9e3c-2f5a8b7c6d1e', sku: 'MAC-1', productName: 'Maceta', unit: 'caja',
  factor: '12', commercialQuantity: '2.000', baseQuantity: '24.000', baseUnitName: 'unidad', unitPrice: '120',
  discountPercent: '10', netAmount: '216.00', exactNetAmount: '216', unitBaseCost: null,
};
const draftOrder = {
  externalId: '8c7b6a5d-4e3f-4a2b-9c1d-0e9f8a7b6c5d', code: 'OC-000001', status: 'DRAFT', branch,
  supplier: { externalId: '1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d', code: 'PRV-1', name: 'Viveros' },
  paymentTermDays: 30, createdAt: '2026-10-27T15:00:00Z', createdBy: person, receivedAt: null, receivedBy: null,
  dueDate: null, receiptOperationExternalId: null, totalNet: '216.00', exactTotalNet: '216', lines: [line],
  events: [{ fromStatus: null, toStatus: 'DRAFT', occurredAt: '2026-10-27T15:00:00Z', actor: person }],
};
const receivedOrder = {
  ...draftOrder, status: 'RECEIVED', receivedAt: '2026-10-28T15:00:00Z', receivedBy: person, dueDate: '2026-11-27',
  receiptOperationExternalId: '2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e', lines: [{ ...line, unitBaseCost: '9.000000' }], events: null,
};

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

test('createPurchase sends the conditions with CSRF and the idempotency key', async () => {
  const h = harness([Response.json({ token: 't', headerName: 'X-CSRF-TOKEN' }), Response.json(draftOrder, { status: 201 })]);
  const input = { supplier: draftOrder.supplier.externalId, paymentTermDays: 30,
    lines: [{ product: line.productExternalId, unit: 'caja', quantity: '2', unitPrice: '120', discountPercent: '10' }] };
  assert.deepEqual(await h.client.createPurchase(input, 'key-1'), draftOrder);
  const headers = new Headers(h.calls[1].options.headers);
  assert.equal(h.calls[1].url, 'http://localhost:8080/api/v1/purchases/orders');
  assert.equal(headers.get('Idempotency-Key'), 'key-1');
  assert.equal(headers.get('X-CSRF-TOKEN'), 't');
  assert.deepEqual(JSON.parse(String(h.calls[1].options.body)), input);
});

test('submit and receive post an empty body to their transition', async () => {
  const h = harness([Response.json({ token: 't', headerName: 'X-CSRF-TOKEN' }),
    Response.json({ ...draftOrder, status: 'PENDING' }), Response.json(receivedOrder)]);
  assert.equal((await h.client.submitPurchase(draftOrder.externalId, 'k1')).status, 'PENDING');
  assert.equal((await h.client.receivePurchase(draftOrder.externalId, 'k2')).lines[0].unitBaseCost, '9.000000');
  assert.equal(h.calls[1].url, `http://localhost:8080/api/v1/purchases/orders/${draftOrder.externalId}/submit`);
  assert.equal(h.calls[2].url, `http://localhost:8080/api/v1/purchases/orders/${draftOrder.externalId}/receive`);
  assert.equal(String(h.calls[2].options.body), '{}');
  assert.equal(new Headers(h.calls[2].options.headers).get('Idempotency-Key'), 'k2');
});

test('a repeated receipt surfaces the identifiable conflict, and inconsistent receipts are rejected', async () => {
  const h = harness([
    Response.json({ token: 't', headerName: 'X-CSRF-TOKEN' }),
    Response.json({ code: 'PURCHASE_ALREADY_RECEIVED', detail: 'La recepción ya fue confirmada.' }, { status: 409 }),
    Response.json({ ...receivedOrder, dueDate: null }),
    Response.json({ ...draftOrder, dueDate: '2026-11-27' }),
  ]);
  await assert.rejects(h.client.receivePurchase(draftOrder.externalId, 'k'), (error: unknown) =>
    error instanceof ApiError && error.status === 409 && error.problem.code === 'PURCHASE_ALREADY_RECEIVED');
  await assert.rejects(h.client.receivePurchase(draftOrder.externalId, 'k'), /Respuesta JSON inválida/);
  await assert.rejects(h.client.purchase(draftOrder.externalId), /Respuesta JSON inválida/);
});

test('purchaseHistory sends only the chosen filters and keeps the server summary', async () => {
  const body = { readAt: '2026-10-28T15:00:00Z', branch: null,
    summary: { orders: 2, drafts: 1, pending: 0, received: 1, cancelled: 0 }, items: [{ ...draftOrder, events: null }, receivedOrder] };
  const h = harness([Response.json(body), Response.json({ ...body, branch })]);
  assert.deepEqual(await h.client.purchaseHistory(), body);
  await h.client.purchaseHistory({ supplier: draftOrder.supplier.externalId, product: line.productExternalId, status: 'RECEIVED' });
  assert.equal(h.calls[0].url, 'http://localhost:8080/api/v1/purchases/orders');
  assert.equal(h.calls[1].url, 'http://localhost:8080/api/v1/purchases/orders'
    + `?supplier=${draftOrder.supplier.externalId}&product=${line.productExternalId}&status=RECEIVED`);
});
