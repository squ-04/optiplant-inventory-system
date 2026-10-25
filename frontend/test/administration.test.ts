import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiClient, ApiError } from '../src/lib/api/client.ts';

const id = 'df06cdfe-a745-4bf4-bc81-e10db132724b';
const branch = { externalId: id, code: 'NORTH', name: 'Norte', active: true };
const account = { externalId: id, code: 'ANA', fullName: 'Ana Pérez', role: 'OPERATOR',
  branchExternalId: id, active: true, mustChangePassword: true };
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
test('branch lifecycle uses real contract and CSRF without changing its code', async () => {
  const h = harness([[branch], branch, { token: 'csrf', headerName: 'X-CSRF-TOKEN' }, branch,
    { ...branch, name: 'Nuevo nombre' }, { ...branch, active: false }]);
  assert.deepEqual(await h.client.branches(), [branch]);
  assert.deepEqual(await h.client.branch(id), branch);
  assert.deepEqual(await h.client.createBranch({ code: 'NORTH', name: 'Norte' }), branch);
  await h.client.updateBranch(id, { name: 'Nuevo nombre' });
  await h.client.deactivateBranch(id);
  assert.deepEqual(h.calls.map(c => c.url.split('/api/v1')[1]), [
    '/branches', `/branches/${id}`, '/auth/csrf', '/branches', `/branches/${id}/update`, `/branches/${id}/deactivate`,
  ]);
  assert.deepEqual(JSON.parse(String(h.calls[4].options.body)), { name: 'Nuevo nombre' });
  assert.equal(new Headers(h.calls[3].options.headers).get('X-CSRF-TOKEN'), 'csrf');
  assert.ok(h.calls.every(c => c.options.credentials === 'include'));
});
test('user lifecycle never sends password during update or returns one as a field', async () => {
  const h = harness([[account], account, { token: 'csrf', headerName: 'X-CSRF-TOKEN' }, account, account,
    new Response(null, { status: 204 })]);
  assert.deepEqual(await h.client.users(), [account]);
  assert.deepEqual(await h.client.user(id), account);
  const data = { code: 'ANA', fullName: 'Ana Pérez', role: 'OPERATOR' as const, branchExternalId: id };
  await h.client.createUser({ ...data, temporaryPassword: 'individual temporary secret' });
  await h.client.updateUser(id, data);
  await h.client.deactivateUser(id);
  assert.equal(JSON.parse(String(h.calls[3].options.body)).temporaryPassword, 'individual temporary secret');
  assert.deepEqual(JSON.parse(String(h.calls[4].options.body)), data);
  assert.equal(h.calls[5].url, `http://localhost:8080/api/v1/users/${id}/deactivate`);
});
test('administration fails closed on malformed DTOs and retains server rejection', async () => {
  for (const value of [{}, [{ ...branch, active: 'yes' }]]) {
    await assert.rejects(harness([value]).client.branches(), /inválida/);
  }
  await assert.rejects(harness([{ ...account, role: 'UNKNOWN' }]).client.user(id), /inválida/);
  const h = harness([{ token: 'csrf', headerName: 'X-CSRF-TOKEN' }, Response.json(
    { code: 'BRANCH_HAS_STOCK', detail: 'La sucursal conserva existencias.' }, { status: 409 })]);
  await assert.rejects(h.client.deactivateBranch(id), e => e instanceof ApiError && e.status === 409);
});
test('network failure is not acknowledged or retried offline', async () => {
  const h = harness([{ token: 'csrf', headerName: 'X-CSRF-TOKEN' }, new TypeError('Failed to fetch')]);
  await assert.rejects(h.client.createBranch({ code: 'NORTH', name: 'Norte' }), /Failed to fetch/);
  assert.equal(h.calls.length, 2);
});

test('visibility preserves decimal strings and scopes requested branch without granting writes', async () => {
  const inventory = [{ productExternalId: id, sku: 'SKU', name: 'Producto', physicalQuantity: '7.000' }];
  const history = { branch, inventory, purchases: [], sales: [], transfers: [], movements: [] };
  const h = harness([[branch], inventory, history]);
  assert.deepEqual(await h.client.visibleBranches(), [branch]);
  assert.deepEqual(await h.client.inventory(id), inventory);
  assert.deepEqual(await h.client.visibility(id), history);
  assert.ok(h.calls.every(c => c.options.method === 'GET'));
  assert.equal(h.calls[2].url, `http://localhost:8080/api/v1/visibility/branches/${id}`);
  await assert.rejects(harness([{ ...history, inventory: [{ ...inventory[0], physicalQuantity: 7 }] }]).client.visibility(id), /inválida/);
});
