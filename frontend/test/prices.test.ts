import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiClient } from '../src/lib/api/client.ts';
import type { ListPrice } from '../src/lib/api/client.ts';
import { listPriceOf, unavailableReason } from '../src/lib/prices/prices.ts';

const csrf = { token: 'token', headerName: 'X-CSRF-TOKEN' };
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status, headers: { 'Content-Type': 'application/json' },
});
const product = 'df06cdfe-a745-4bf4-bc81-e10db132724b';
const listId = '3f1b6a2e-8c4d-4e5f-9a0b-1c2d3e4f5a6b';
const price: ListPrice = {
  productExternalId: product, sku: 'SKU-1', productName: 'Maceta', productActive: true,
  unitName: 'caja', unitActive: true, price: '99999.123456',
};
const list = { externalId: listId, code: 'MAY', name: 'Mayorista', active: true, prices: [price] };

function harness(responses: Response[]) {
  const calls: { url: string; options: RequestInit }[] = [];
  const client = new ApiClient('http://localhost:8080', async (input, options) => {
    calls.push({ url: String(input), options: options ?? {} });
    const response = responses.shift();
    assert.ok(response, 'unexpected request');
    return response;
  }, () => {});
  return { client, calls };
}

test('price lists are read and the exact price string is kept', async () => {
  const h = harness([json([{ ...list, prices: null }]), json(list)]);
  assert.deepEqual(await h.client.priceLists(), [{ ...list, prices: null }]);
  assert.deepEqual(await h.client.priceList(listId), list);
  assert.deepEqual(h.calls.map(c => c.url), [
    'http://localhost:8080/api/v1/catalog/price-lists', `http://localhost:8080/api/v1/catalog/price-lists/${listId}`,
  ]);
});

test('a price is sent as an exact string to the list endpoint', async () => {
  const h = harness([json(csrf), json(list)]);
  await h.client.setListPrice(listId, { product, unit: 'caja', price: '99999.123456' });
  assert.equal(h.calls[1].url, `http://localhost:8080/api/v1/catalog/price-lists/${listId}/prices`);
  assert.equal(h.calls[1].options.method, 'POST');
  assert.deepEqual(JSON.parse(String(h.calls[1].options.body)), { product, unit: 'caja', price: '99999.123456' });
});

test('list maintenance uses the create and update endpoints', async () => {
  const h = harness([json(csrf), json({ ...list, prices: [] }, 201), json({ ...list, active: false })]);
  await h.client.createPriceList({ code: 'MAY', name: 'Mayorista' });
  assert.equal((await h.client.updatePriceList(listId, { code: 'MAY', name: 'Mayorista', active: false })).active, false);
  assert.deepEqual(JSON.parse(String(h.calls[1].options.body)), { code: 'MAY', name: 'Mayorista' });
  assert.equal(h.calls[2].url, `http://localhost:8080/api/v1/catalog/price-lists/${listId}/update`);
  assert.deepEqual(JSON.parse(String(h.calls[2].options.body)), { code: 'MAY', name: 'Mayorista', active: false });
});

test('malformed price lists fail closed', async () => {
  for (const body of [
    [{ ...list, prices: [{ ...price, price: 99 }] }],
    [{ ...list, prices: [{ ...price, price: '-1' }] }],
    [{ ...list, active: 'yes' }],
    [{ ...list, prices: 'none' }],
  ]) {
    await assert.rejects(harness([json(body)]).client.priceLists(), /Respuesta JSON inválida/);
  }
});

test('the existing price of a unit is found and unavailable prices are explained', () => {
  assert.equal(listPriceOf([price], product, 'caja'), price);
  assert.equal(listPriceOf([price], product, 'unidad'), undefined);
  assert.equal(unavailableReason(price), null);
  assert.equal(unavailableReason({ ...price, productActive: false }), 'Producto inactivo');
  assert.equal(unavailableReason({ ...price, unitActive: false }), 'Unidad inactiva');
});
