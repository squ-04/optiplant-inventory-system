import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiClient } from '../src/lib/api/client.ts';

test('S3: transport is invoked with a browser-compatible receiver', async () => {
  const user = {
    externalId: 'df06cdfe-a745-4bf4-bc81-e10db132724b', code: 'ADMIN',
    role: 'ADMIN', branchExternalId: null, mustChangePassword: true,
  };
  const transport: typeof fetch = async function (this: unknown) {
    assert.ok(this === undefined || this === globalThis,
      'Native browser fetch rejects an ApiClient receiver');
    return new Response(JSON.stringify(user), { status: 200 });
  };
  assert.deepEqual(await new ApiClient('http://localhost:8080', transport).me(), user);
});
