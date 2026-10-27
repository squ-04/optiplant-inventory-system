import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiClient, ApiError } from '../src/lib/api/client.ts';

const id = 'df06cdfe-a745-4bf4-bc81-e10db132724b';
const branchId = '7c9e6679-7425-40de-944b-e07fc1f90ae7';
const csrf = { token: 'csrf', headerName: 'X-CSRF-TOKEN' };
const product = { externalId: id, sku: 'SKU-1', name: 'Helecho', description: null, baseUnitName: 'unidad',
  baseDivisible: false, active: true, inUse: false, branches: null, units: null };
const managed = { ...product, branches: [{ branchExternalId: branchId, branchCode: 'NORTH', branchName: 'Norte',
  branchActive: true, enabled: true, minimumQuantity: '5.000' }] };

function harness(values: unknown[]) {
  const calls: { url: string; options: RequestInit }[] = [];
  const client = new ApiClient('http://localhost:8080', async (url, options) => {
    calls.push({ url: String(url), options: options! });
    const value = values.shift();
    if (value instanceof Error) throw value;
    if (value instanceof Response) return value;
    return Response.json(value);
  });
  return { client, calls };
}
const paths = (calls: { url: string }[]) => calls.map(c => c.url.split('/api/v1')[1]);

test('product queries keep catalog data separate from local enablement', async () => {
  const h = harness([[product], managed]);
  assert.deepEqual(await h.client.products(), [product]);
  assert.deepEqual(await h.client.product(id), managed);
  assert.deepEqual(paths(h.calls), ['/products', `/products/${id}`]);
  assert.ok(h.calls.every(c => c.options.method === 'GET' && c.options.credentials === 'include'));
});

test('product maintenance uses the real contract, CSRF and exact decimal strings', async () => {
  const h = harness([csrf, managed, managed, managed, { ...managed, active: false }, managed]);
  const attributes = { sku: 'SKU-1', name: 'Helecho', description: null, baseUnitName: 'unidad', baseDivisible: false };
  await h.client.createProduct({ ...attributes, branches: [{ branchExternalId: branchId, minimumQuantity: '5' }] });
  await h.client.updateProduct(id, { ...attributes, name: 'Helecho grande' });
  await h.client.enableProduct(id, { branchExternalId: branchId, minimumQuantity: '0.500' });
  await h.client.deactivateProduct(id);
  await h.client.reactivateProduct(id);
  assert.deepEqual(paths(h.calls), ['/auth/csrf', '/products', `/products/${id}/update`, `/products/${id}/branches`,
    `/products/${id}/deactivate`, `/products/${id}/reactivate`]);
  assert.ok(h.calls.slice(1).every(c => c.options.method === 'POST'
    && new Headers(c.options.headers).get('X-CSRF-TOKEN') === 'csrf'));
  assert.deepEqual(JSON.parse(String(h.calls[1].options.body)).branches, [{ branchExternalId: branchId, minimumQuantity: '5' }]);
  assert.deepEqual(JSON.parse(String(h.calls[2].options.body)), { ...attributes, name: 'Helecho grande' });
  assert.equal(JSON.parse(String(h.calls[3].options.body)).minimumQuantity, '0.500');
  assert.equal(h.calls[4].options.body, undefined);
  assert.ok(!h.calls.some(c => c.options.method === 'DELETE'));
});

test('product DTOs fail closed when malformed', async () => {
  for (const value of [{}, [{ ...product, active: 'yes' }], [{ ...product, externalId: '1' }],
    [{ ...product, description: 5 }]]) {
    await assert.rejects(harness([value]).client.products(), /inválida/);
  }
  const badMinimum = { ...managed, branches: [{ ...managed.branches[0], minimumQuantity: 5 }] };
  await assert.rejects(harness([badMinimum]).client.product(id), /inválida/);
});

test('deactivation rejection keeps the server reason and is not retried', async () => {
  const h = harness([csrf, Response.json({ code: 'PRODUCT_DEACTIVATION_BLOCKED',
    detail: 'El producto tiene existencias físicas distintas de cero en al menos una sucursal.' }, { status: 409 })]);
  await assert.rejects(h.client.deactivateProduct(id), e => e instanceof ApiError && e.status === 409
    && e.problem.code === 'PRODUCT_DEACTIVATION_BLOCKED' && /existencias/.test(e.message));
  assert.equal(h.calls.length, 2);
});

const units = [{ name: 'unidad', factor: '1.000', divisible: false, base: true, active: true },
  { name: 'caja', factor: '12.000', divisible: false, base: false, active: true }];
const configured = { ...managed, units };

test('legacy product payloads without units are normalized to null', async () => {
  const { units: _omitted, ...legacy } = product;
  assert.deepEqual(await harness([[legacy]]).client.products(), [product]);
});

test('minimum and unit maintenance use the real contract with CSRF and exact decimal strings', async () => {
  const h = harness([csrf, configured, configured, configured]);
  assert.deepEqual(await h.client.updateMinimum(id, branchId, '0'), configured);
  await h.client.createUnit(id, { name: 'caja', factor: '12', divisible: false });
  await h.client.updateUnit(id, { name: 'caja', factor: '10', active: false });
  assert.deepEqual(paths(h.calls), ['/auth/csrf', `/products/${id}/branches/${branchId}/minimum`, `/products/${id}/units`,
    `/products/${id}/units/update`]);
  assert.ok(h.calls.slice(1).every(c => c.options.method === 'POST'
    && new Headers(c.options.headers).get('X-CSRF-TOKEN') === 'csrf'));
  assert.deepEqual(JSON.parse(String(h.calls[1].options.body)), { minimumQuantity: '0' });
  assert.deepEqual(JSON.parse(String(h.calls[2].options.body)), { name: 'caja', factor: '12', divisible: false });
  assert.deepEqual(JSON.parse(String(h.calls[3].options.body)), { name: 'caja', factor: '10', active: false });
});

test('unit conversion is computed by the server and encodes its query', async () => {
  const result = { unitName: 'caja grande', quantity: '1', factor: '12.000', baseUnitName: 'unidad', baseQuantity: '12.000' };
  const h = harness([result]);
  assert.deepEqual(await h.client.convertUnit(id, 'caja grande', '1'), result);
  assert.equal(paths(h.calls)[0], `/products/${id}/units/convert?unit=caja+grande&quantity=1`);
  assert.equal(h.calls[0].options.method, 'GET');
  await assert.rejects(harness([{ ...result, baseQuantity: 12 }]).client.convertUnit(id, 'caja', '1'), /inválida/);
  const rejected = harness([Response.json({ code: 'VALIDATION_FAILED', detail: 'La unidad no admite fracciones: la cantidad debe ser entera.' },
    { status: 400 })]);
  await assert.rejects(rejected.client.convertUnit(id, 'caja', '1.5'),
    (error: unknown) => error instanceof ApiError && error.status === 400 && /fracciones/.test(error.message));
});

test('local catalog keeps physical, available and transit as separate exact strings', async () => {
  const local = { branchExternalId: branchId, branchCode: 'NORTH', branchName: 'Norte', branchActive: true, items: [{
    productExternalId: id, sku: 'SKU-1', name: 'Helecho', description: null, baseUnitName: 'unidad', baseDivisible: false,
    minimumQuantity: '0.000', physicalQuantity: '0.000', availableQuantity: '0.000', inTransitQuantity: '3.000', restockAlert: true, units }] };
  const h = harness([local]);
  assert.deepEqual(await h.client.localCatalog(), local);
  assert.deepEqual(paths(h.calls), ['/products/local']);
  for (const bad of [{ ...local, branchExternalId: 'x' }, { ...local, items: [{ ...local.items[0], physicalQuantity: 0 }] },
    { ...local, items: [{ ...local.items[0], units: [{ name: 'caja' }] }] }, { ...local, items: null },
    { ...local, items: [{ ...local.items[0], restockAlert: 'true' }] }]) {
    await assert.rejects(harness([bad]).client.localCatalog(), /inválida/);
  }
  const forbidden = harness([Response.json({ code: 'ROLE_FORBIDDEN', detail: 'El catálogo local corresponde a la sucursal asignada.' },
    { status: 403 })]);
  await assert.rejects(forbidden.client.localCatalog(), (error: unknown) => error instanceof ApiError && error.status === 403);
});
