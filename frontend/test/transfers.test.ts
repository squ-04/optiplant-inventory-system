import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiClient, ApiError } from '../src/lib/api/client.ts';
import {
  canCancel, canDecide, canPrepare, cancelReasonRequired, checkPreparation, compareDecimal, multiply, side, transferStatusLabel,
} from '../src/lib/transfers/transfers.ts';

const origin = { externalId: '1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d', code: 'A', name: 'Medellín', active: true };
const destination = { externalId: '2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e', code: 'B', name: 'Bogotá', active: true };
const person = { code: 'U1', fullName: 'Ana Pérez' };
const line = {
  lineNo: 1, productExternalId: '3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f', sku: 'MAC-1', productName: 'Maceta', unit: 'caja',
  factor: '12', baseUnitName: 'unidad', requestedQuantity: '2.000', requestedBaseQuantity: '24.000', preparedQuantity: null,
  preparedBaseQuantity: null, originAvailableQuantity: null,
};
const requested = {
  externalId: '4d5e6f7a-8b9c-4d0e-9f1a-2b3c4d5e6f7a', code: 'TR-000001', status: 'REQUESTED', origin, destination,
  requestedAt: '2026-10-28T15:00:00Z', requestedBy: person, approval: null, lines: [line],
  events: [{ fromStatus: null, toStatus: 'REQUESTED', occurredAt: '2026-10-28T15:00:00Z', actor: person, reason: null, preparation: null }],
};
const preparing = {
  ...requested, status: 'PREPARING',
  lines: [{ ...line, preparedQuantity: '1.000', preparedBaseQuantity: '12.000', originAvailableQuantity: '30.000' }],
  events: [...requested.events, { fromStatus: 'REQUESTED', toStatus: 'PREPARING', occurredAt: '2026-10-28T16:00:00Z', actor: person,
    reason: null, preparation: [{ lineNo: 1, quantity: '1.000', baseQuantity: '12.000' }] }],
};

const originOperator = { role: 'OPERATOR', branchExternalId: origin.externalId };
const originManager = { role: 'MANAGER', branchExternalId: origin.externalId };
const destinationOperator = { role: 'OPERATOR', branchExternalId: destination.externalId };
const destinationManager = { role: 'MANAGER', branchExternalId: destination.externalId };
const administrator = { role: 'ADMIN', branchExternalId: null };
const at = (status: string) => ({ status, origin, destination }) as Parameters<typeof canPrepare>[1];

test('only the origin operator prepares, while requested or in preparation', () => {
  assert.equal(canPrepare(originOperator, at('REQUESTED')), true);
  assert.equal(canPrepare(originOperator, at('PREPARING')), true);
  for (const status of ['APPROVED', 'IN_TRANSIT', 'CANCELLED', 'REJECTED']) assert.equal(canPrepare(originOperator, at(status)), false);
  for (const user of [originManager, destinationOperator, destinationManager, administrator]) {
    assert.equal(canPrepare(user, at('REQUESTED')), false);
  }
});

test('only the origin manager approves or rejects, only in preparation', () => {
  assert.equal(canDecide(originManager, at('PREPARING')), true);
  for (const status of ['REQUESTED', 'APPROVED', 'IN_TRANSIT']) assert.equal(canDecide(originManager, at(status)), false);
  // RF-0034-CA-04: the destination manager does not obtain the permission by supervising the receipt.
  for (const user of [originOperator, destinationManager, destinationOperator, administrator]) {
    assert.equal(canDecide(user, at('PREPARING')), false);
  }
});

test('destination cancels before approval; once approved only the origin manager, with a reason; never in transit', () => {
  assert.equal(canCancel(destinationOperator, at('REQUESTED')), true);
  assert.equal(canCancel(destinationOperator, at('PREPARING')), true);
  assert.equal(canCancel(destinationOperator, at('APPROVED')), false);
  assert.equal(canCancel(originManager, at('REQUESTED')), false);
  assert.equal(canCancel(originManager, at('APPROVED')), true);
  for (const user of [originOperator, originManager, destinationOperator, administrator]) assert.equal(canCancel(user, at('IN_TRANSIT')), false);
  assert.equal(canCancel(administrator, at('REQUESTED')), false);
  assert.equal(cancelReasonRequired({ status: 'APPROVED' }), true);
  assert.equal(cancelReasonRequired({ status: 'REQUESTED' }), false);
});

test('the side names the branch role of the viewer', () => {
  assert.equal(side(originOperator, at('REQUESTED')), 'origin');
  assert.equal(side(destinationManager, at('REQUESTED')), 'destination');
  assert.equal(side(administrator, at('REQUESTED')), 'network');
  assert.equal(transferStatusLabel('PREPARING'), 'En preparación');
  assert.equal(transferStatusLabel('REJECTED'), 'Rechazada');
});

test('decimals are compared and multiplied exactly', () => {
  assert.equal(compareDecimal('1.000', '1'), 0);
  assert.equal(compareDecimal('0.999', '1'), -1);
  assert.equal(compareDecimal('2.5', '2.4999'), 1);
  assert.equal(compareDecimal('1,5', '1'), null);
  assert.equal(multiply('2', '12'), '24');
  assert.equal(multiply('0.25', '0.5'), '0.125');
});

test('preparation never exceeds the request and warns when it exceeds the origin availability', () => {
  assert.equal(checkPreparation('2', '2.000', '12', '30.000'), 'ok');
  assert.equal(checkPreparation('0', '2.000', '12', '30.000'), 'ok');
  assert.equal(checkPreparation('3', '2.000', '12', null), 'exceeds');
  // 2 boxes of 12 = 24 units, only 20 available: allowed, because preparing does not reserve, but warned.
  assert.equal(checkPreparation('2', '2.000', '12', '20.000'), 'short');
  assert.equal(checkPreparation('2', '2.000', '12', null), 'ok');
  assert.equal(checkPreparation('', '2.000', '12', null), 'invalid');
  assert.equal(checkPreparation('-1', '2.000', '12', null), 'invalid');
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
const csrf = () => Response.json({ token: 't', headerName: 'X-CSRF-TOKEN' });

test('requestTransfer sends origin and lines with CSRF and the idempotency key', async () => {
  const h = harness([csrf(), Response.json(requested, { status: 201 })]);
  const input = { origin: origin.externalId, lines: [{ product: line.productExternalId, unit: 'caja', quantity: '2' }] };
  assert.deepEqual(await h.client.requestTransfer(input, 'key-1'), requested);
  const headers = new Headers(h.calls[1].options.headers);
  assert.equal(h.calls[1].url, 'http://localhost:8080/api/v1/transfers');
  assert.equal(headers.get('Idempotency-Key'), 'key-1');
  assert.equal(headers.get('X-CSRF-TOKEN'), 't');
  assert.deepEqual(JSON.parse(String(h.calls[1].options.body)), input);
});

test('transitions post to their endpoint with their own body', async () => {
  const id = requested.externalId;
  const h = harness([csrf(), Response.json(preparing), Response.json({ ...preparing, status: 'REJECTED' }),
    Response.json({ ...requested, status: 'CANCELLED' }), Response.json({ ...requested, status: 'CANCELLED' }),
    Response.json({ ...preparing, status: 'APPROVED', approval: { occurredAt: '2026-10-28T17:00:00Z', actor: person } })]);
  assert.equal((await h.client.prepareTransfer(id, [{ lineNo: 1, quantity: '1' }], 'k1')).lines[0].preparedQuantity, '1.000');
  await h.client.rejectTransfer(id, 'Sin vehículo', 'k2');
  await h.client.cancelTransfer(id, null, 'k3');
  await h.client.cancelTransfer(id, 'Ya no se necesita', 'k4');
  assert.equal((await h.client.approveTransfer(id, 'k5')).approval?.actor.code, 'U1');
  assert.deepEqual(h.calls.slice(1).map(call => [call.url.replace(`http://localhost:8080/api/v1/transfers/${id}/`, ''),
    JSON.parse(String(call.options.body))]), [
    ['prepare', { lines: [{ lineNo: 1, quantity: '1' }] }], ['reject', { reason: 'Sin vehículo' }], ['cancel', {}],
    ['cancel', { reason: 'Ya no se necesita' }], ['approve', {}],
  ]);
});

test('transfers builds the filter query and validates the listing', async () => {
  const h = harness([Response.json({ readAt: '2026-10-28T18:00:00Z', branch: destination, items: [{ ...requested, events: null }] })]);
  const listed = await h.client.transfers({ direction: 'INCOMING', status: 'REQUESTED' });
  assert.equal(h.calls[0].url, 'http://localhost:8080/api/v1/transfers?direction=INCOMING&status=REQUESTED');
  assert.equal(listed.items[0].events, null);
  assert.equal(listed.branch?.code, 'B');
});

test('a malformed transfer is rejected and server problems surface as ApiError', async () => {
  const broken = harness([Response.json({ ...requested, lines: [{ ...line, preparedQuantity: '1.000' }] })]);
  await assert.rejects(broken.client.transfer(requested.externalId), /Respuesta JSON inválida/);
  const conflict = harness([csrf(), Response.json({ code: 'PREPARED_EXCEEDS_REQUESTED', detail: 'Línea 1: no se puede enviar más.' },
    { status: 409, headers: { 'Content-Type': 'application/problem+json' } })]);
  await assert.rejects(conflict.client.prepareTransfer(requested.externalId, [{ lineNo: 1, quantity: '3' }], 'k'),
    (error: unknown) => error instanceof ApiError && error.status === 409 && error.problem.code === 'PREPARED_EXCEEDS_REQUESTED');
});
