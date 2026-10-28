import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiClient, ApiError } from '../src/lib/api/client.ts';
import { baseQuantity, compareDecimal, confirmationFailure, netUnitPrice, shortages, shownDiscount, subtract } from '../src/lib/sales/sales.ts';

test('net unit price applies the line discount exactly', () => {
  // RF-0029-CA-02: price 100 with 10% nets 90; 0% keeps 100 and 100% produces zero.
  assert.equal(netUnitPrice('100', '10'), '90');
  assert.equal(netUnitPrice('100', '0'), '100');
  assert.equal(netUnitPrice('100', '100'), '0');
  assert.equal(netUnitPrice('100', '100.5'), null);
  assert.equal(netUnitPrice('100', ''), null);
});

test('decimal helpers compare, multiply and subtract exact strings', () => {
  assert.equal(baseQuantity('2', '12'), '24');
  assert.equal(baseQuantity('1.5', '0.25'), '0.375');
  assert.equal(compareDecimal('3', '3.000'), 0);
  assert.equal(compareDecimal('3.001', '3'), 1);
  assert.equal(compareDecimal('2.5', '10'), -1);
  assert.equal(subtract('1300', '1280'), '20');
  assert.equal(subtract('100.5', '0.25'), '100.25');
  assert.equal(subtract('1', '0.001'), '0.999');
});

test('the shown discount is the shown subtotal minus the shown net', () => {
  // Exact gross 1.005 and net 0.5025 show as 1.01 and 0.50: the discount shows 0.51, not the rounded exact 0.50.
  assert.equal(shownDiscount('1.005', '0.5025'), '0.51');
  assert.equal(shownDiscount('3000', '2700'), '300.00');
});

test('shortages add every line of a product against its available base quantity', () => {
  // RF-0028: two lines of 2 units against 3 available are short together, not one by one.
  const lines = [{ product: 'p', quantity: '2', factor: '1' }, { product: 'p', quantity: '2', factor: '1' },
    { product: 'q', quantity: '1', factor: '12' }];
  assert.deepEqual([...shortages(lines, { p: '3', q: '12' })], ['p']);
  assert.deepEqual([...shortages(lines, { p: '4', q: '11.999' })], ['q']);
  assert.equal(shortages([{ product: 'p', quantity: '', factor: '1' }], { p: '0' }).size, 0);
});

test('a failed confirmation keeps the key only while its outcome is unknown', () => {
  // Connection loss or a server error: the outcome is unknown, so the same key is kept even if the draft is edited.
  const offline = confirmationFailure(new TypeError('fetch failed'));
  assert.equal(offline.keepKey, true);
  assert.equal(offline.alreadyConfirmed, false);
  assert.match(offline.message, /no se duplicará/);
  assert.equal(confirmationFailure(new ApiError(503, { detail: 'x' })).keepKey, true);
  // A business rejection is final: a later attempt uses a new key.
  const rejected = confirmationFailure(new ApiError(409, { code: 'INSUFFICIENT_STOCK', detail: 'Existencias insuficientes.' }));
  assert.deepEqual(rejected, { keepKey: false, alreadyConfirmed: false, message: 'Existencias insuficientes.' });
  // The kept key already confirmed a sale with other data: say so instead of reporting a bare conflict.
  const conflict = confirmationFailure(new ApiError(409, { code: 'IDEMPOTENCY_CONFLICT', detail: 'La clave ya se usó.' }));
  assert.equal(conflict.keepKey, false);
  assert.equal(conflict.alreadyConfirmed, true);
  assert.match(conflict.message, /sí quedó registrada/);
});

const branch = { externalId: '4d4f8f15-6cfc-4c49-9d4c-58d7a0b2c111', code: 'SUC-1', name: 'Centro', active: true };
const sale = {
  externalId: '6c1d2f60-1b0a-4a43-9d2e-2b7a1f0c9e11', code: 'VT-000001', branch, confirmedAt: '2026-10-28T15:00:00Z',
  confirmedBy: { code: 'operador', fullName: 'Ana Operadora' }, operationExternalId: '7a1d2f60-1b0a-4a43-9d2e-2b7a1f0c9e12',
  grossTotal: '200.00', discountTotal: '20.00', totalNet: '180.00', exactTotalNet: '180',
  lines: [{ lineNo: 1, productExternalId: '8b1d2f60-1b0a-4a43-9d2e-2b7a1f0c9e13', sku: 'MAC-01', productName: 'Maceta',
    unit: 'unidad', factor: '1', commercialQuantity: '2.000', baseQuantity: '2.000', baseUnitName: 'unidad',
    priceListExternalId: '9c1d2f60-1b0a-4a43-9d2e-2b7a1f0c9e14', priceListName: 'Detal', unitPrice: '100',
    discountPercent: '10', netUnitPrice: '90', netAmount: '180.00', exactNetAmount: '180',
    movementExternalId: 'ad1d2f60-1b0a-4a43-9d2e-2b7a1f0c9e15' }],
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

test('confirmSale posts the list and lines with CSRF and the idempotency key, without a unit price', async () => {
  const h = harness([Response.json({ token: 't', headerName: 'X-CSRF-TOKEN' }), Response.json(sale, { status: 201 })]);
  const input = { priceList: sale.lines[0].priceListExternalId,
    lines: [{ product: sale.lines[0].productExternalId, unit: 'unidad', quantity: '2', discountPercent: '10' }] };
  assert.deepEqual(await h.client.confirmSale(input, 'key-1'), sale);
  const headers = new Headers(h.calls[1].options.headers);
  assert.equal(h.calls[1].url, 'http://localhost:8080/api/v1/sales');
  assert.equal(h.calls[1].options.method, 'POST');
  assert.equal(headers.get('Idempotency-Key'), 'key-1');
  assert.equal(headers.get('X-CSRF-TOKEN'), 't');
  assert.deepEqual(JSON.parse(String(h.calls[1].options.body)), input);
});

test('a rejected sale surfaces the server problem', async () => {
  const h = harness([Response.json({ token: 't', headerName: 'X-CSRF-TOKEN' }),
    Response.json({ code: 'INSUFFICIENT_STOCK', detail: 'Existencias insuficientes de MAC-01 Maceta.' }, { status: 409 })]);
  await assert.rejects(h.client.confirmSale({ priceList: 'x', lines: [] }, 'k'), (error: unknown) =>
    error instanceof ApiError && error.status === 409 && error.problem.code === 'INSUFFICIENT_STOCK');
});

test('sales and sale read the records and reject malformed ones', async () => {
  const h = harness([Response.json({ readAt: '2026-10-28T15:01:00Z', branch: null, total: 1, items: [sale] }),
    Response.json(sale), Response.json({ ...sale, lines: [] })]);
  const history = await h.client.sales({ branch: branch.externalId, limit: 50 });
  assert.equal(h.calls[0].url, `http://localhost:8080/api/v1/sales?branch=${branch.externalId}&limit=50`);
  assert.equal(history.branch, null);
  assert.equal(history.items[0].lines[0].netUnitPrice, '90');
  assert.equal((await h.client.sale(sale.externalId)).code, 'VT-000001');
  await assert.rejects(h.client.sale(sale.externalId), /Respuesta JSON inválida/);
});
