import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiClient, ApiError } from '../src/lib/api/client.ts';

const user = {
  externalId: 'df06cdfe-a745-4bf4-bc81-e10db132724b', code: 'ADMIN',
  role: 'ADMINISTRATOR', branchExternalId: null, mustChangePassword: true,
};
const csrf = (token = 'first') => ({ token, headerName: 'X-CSRF-TOKEN' });
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status, headers: { 'Content-Type': 'application/json' },
});

function harness(responses: Response[]) {
  const calls: { url: string; options: RequestInit }[] = [];
  let expired = 0;
  const client = new ApiClient('http://localhost:8080', async (input, options) => {
    calls.push({ url: String(input), options: options ?? {} });
    const response = responses.shift();
    assert.ok(response, 'unexpected request');
    return response;
  }, () => { expired += 1; });
  return { client, calls, expired: () => expired };
}

test('login sends JSON, credentials and CSRF, then rotates the token', async () => {
  const h = harness([json(csrf()), json(user), json(csrf('rotated')), new Response(null, { status: 204 }), json(csrf('out'))]);
  assert.deepEqual(await h.client.login('ADMIN', 'temporary password'), user);
  await h.client.logout();
  assert.deepEqual(h.calls.map(c => c.url), [
    'http://localhost:8080/api/v1/auth/csrf', 'http://localhost:8080/api/v1/auth/login',
    'http://localhost:8080/api/v1/auth/csrf', 'http://localhost:8080/api/v1/auth/logout',
    'http://localhost:8080/api/v1/auth/csrf',
  ]);
  assert.ok(h.calls.every(c => c.options.credentials === 'include'));
  assert.equal(h.calls[1].options.method, 'POST');
  assert.equal(new Headers(h.calls[1].options.headers).get('X-CSRF-TOKEN'), 'first');
  assert.equal(new Headers(h.calls[1].options.headers).get('Content-Type'), 'application/json');
  assert.deepEqual(JSON.parse(String(h.calls[1].options.body)), { code: 'ADMIN', password: 'temporary password' });
  assert.equal(new Headers(h.calls[3].options.headers).get('X-CSRF-TOKEN'), 'rotated');
});

test('password change rotates CSRF before the next mutation', async () => {
  const h = harness([json(csrf()), new Response(null, { status: 204 }), json(csrf('new')), new Response(null, { status: 204 }), json(csrf())]);
  await h.client.changePassword('old password value', 'new password value');
  assert.equal(h.expired(), 1, 'password changes revoke the authenticated UI context');
  await h.client.logout();
  assert.equal(h.calls[1].url, 'http://localhost:8080/api/v1/auth/password');
  assert.deepEqual(JSON.parse(String(h.calls[1].options.body)), { currentPassword: 'old password value', newPassword: 'new password value' });
  assert.equal(new Headers(h.calls[3].options.headers).get('X-CSRF-TOKEN'), 'new');
});

test('me restores session context without a mutation', async () => {
  const h = harness([json(user)]);
  assert.deepEqual(await h.client.me(), user);
  assert.equal(h.calls.length, 1);
  assert.equal(h.calls[0].options.method, 'GET');
  assert.equal(h.calls[0].options.credentials, 'include');
});

test('ProblemDetail 403 preserves backend code and leaves session active', async () => {
  const problem = { type: 'about:blank', title: 'Forbidden', status: 403, detail: 'Debe cambiar su contraseña.', code: 'PASSWORD_CHANGE_REQUIRED' };
  const h = harness([json(problem, 403)]);
  await assert.rejects(h.client.bootstrap(), (error: unknown) => {
    assert.ok(error instanceof ApiError);
    assert.equal(error.status, 403);
    assert.deepEqual(error.problem, problem);
    assert.equal(error.message, problem.detail);
    return true;
  });
  assert.equal(h.expired(), 0);
  assert.equal(h.calls.length, 1);
});

test('401 clears CSRF and notifies session expiry before a fresh login', async () => {
  const h = harness([json(csrf()), json(user), json(csrf('old')), json({ title: 'Unauthorized', status: 401 }, 401), json(csrf('fresh')), json(user), json(csrf('next'))]);
  await h.client.login('ADMIN', 'password');
  await assert.rejects(h.client.me(), (e: unknown) => e instanceof ApiError && e.status === 401);
  assert.equal(h.expired(), 1);
  await h.client.login('ADMIN', 'password');
  assert.equal(new Headers(h.calls[5].options.headers).get('X-CSRF-TOKEN'), 'fresh');
});

test('bootstrap keeps exact decimals and UUID/time strings unchanged', async () => {
  const dto = { externalId: user.externalId, occurredAt: '2026-10-23T17:12:13.123456Z', amount: '999999999999999999.123456', context: { role: 'ADMINISTRATOR' } };
  const h = harness([json(dto)]);
  assert.deepEqual(await h.client.bootstrap(), dto);
  assert.equal(h.calls[0].url, 'http://localhost:8080/api/v1/bootstrap');
});

test('malformed JSON and invalid DTOs fail closed', async () => {
  for (const response of [new Response('<html>broken</html>'), json({ amount: 10 }), json({ externalId: 'bad', occurredAt: 'bad', amount: '1.00' })]) {
    const h = harness([response]);
    await assert.rejects(h.client.bootstrap(), /Respuesta JSON inválida del servidor\./);
  }
  const h = harness([json({ ...user, mustChangePassword: 'yes' })]);
  await assert.rejects(h.client.me(), /Respuesta JSON inválida del servidor\./);
});

test('invalid CSRF cannot send a mutation and non-JSON HTTP errors retain status', async () => {
  const h = harness([json({ token: '', headerName: 'X-CSRF-TOKEN' })]);
  await assert.rejects(h.client.login('ADMIN', 'password'), /Respuesta JSON inválida del servidor\./);
  assert.equal(h.calls.length, 1);
  const unavailable = harness([new Response('unavailable', { status: 503 })]);
  await assert.rejects(unavailable.client.me(), (e: unknown) => e instanceof ApiError && e.status === 503);
});
