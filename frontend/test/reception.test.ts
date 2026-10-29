import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiClient } from '../src/lib/api/client.ts';
import type { Transfer } from '../src/lib/api/client.ts';
import {
  TREATMENT_LABEL, canReceive, canResend, canResolve, canWriteOff, checkReception, missingLines, pendingInTransit, receptionMessage,
} from '../src/lib/transfers/transfers.ts';

const origin = { externalId: '1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d', code: 'A', name: 'Medellín', active: true };
const destination = { externalId: '2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e', code: 'B', name: 'Bogotá', active: true };
const person = { code: 'U1', fullName: 'Ana Pérez' };
const sent = {
  lineNo: 1, productExternalId: '3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f', sku: 'MAC-1', productName: 'Maceta', unit: 'unidad',
  factor: '1', baseUnitName: 'unidad', requestedQuantity: '10.000', requestedBaseQuantity: '10.000', preparedQuantity: '10.000',
  preparedBaseQuantity: '10.000', dispatchedQuantity: '10.000', dispatchedBaseQuantity: '10.000', originAvailableQuantity: null,
  unitDivisible: false, baseDivisible: false, receivedQuantity: null, receivedBaseQuantity: null, missingQuantity: null,
  missingBaseQuantity: null, inTransitBaseQuantity: '10.000',
};
const inTransit = {
  externalId: '4d5e6f7a-8b9c-4d0e-9f1a-2b3c4d5e6f7a', code: 'TR-000001', status: 'IN_TRANSIT', origin, destination,
  requestedAt: '2026-10-28T15:00:00Z', requestedBy: person, approval: { occurredAt: '2026-10-28T17:00:00Z', actor: person },
  dispatch: { carrier: 'Transportes Andinos', departedAt: '2026-10-28T18:00:00Z', estimatedArrivalAt: '2026-10-29T20:00:00Z',
    actor: person, routeCode: 'R-AB', routePriority: 'HIGH' }, arrival: null, discrepancy: null, sourceDiscrepancy: null,
  lines: [sent], events: null,
};
const shortage = {
  externalId: '5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b', status: 'OPEN', openedAt: '2026-10-29T12:00:00Z', treatment: null,
  resolvedAt: null, resolvedBy: null, transitLoss: null, resends: [],
};
// RF-0037-CA-01/CA-03: 10 sent, 7 received, 3 missing and still in transit.
const partial = {
  ...inTransit, status: 'RECEIVED_DISCREPANCY', arrival: { occurredAt: '2026-10-29T12:00:00Z', actor: person }, discrepancy: shortage,
  lines: [{ ...sent, receivedQuantity: '7.000', receivedBaseQuantity: '7.000', missingQuantity: '3.000', missingBaseQuantity: '3.000',
    inTransitBaseQuantity: '3.000' }],
};
const resolved = { ...partial, discrepancy: { ...shortage, status: 'RESOLVED', treatment: 'CLAIM', resolvedAt: '2026-10-29T15:00:00Z',
  resolvedBy: person } };

const originOperator = { role: 'OPERATOR', branchExternalId: origin.externalId };
const originManager = { role: 'MANAGER', branchExternalId: origin.externalId };
const destinationOperator = { role: 'OPERATOR', branchExternalId: destination.externalId };
const destinationManager = { role: 'MANAGER', branchExternalId: destination.externalId };
const otherManager = { role: 'MANAGER', branchExternalId: '9f8e7d6c-5b4a-4c3d-8e2f-1a0b9c8d7e6f' };
const administrator = { role: 'ADMIN', branchExternalId: null };
const as = (value: unknown) => value as Transfer;

test('only the destination operator receives, once, while in transit', () => {
  assert.equal(canReceive(destinationOperator, as(inTransit)), true);
  for (const user of [destinationManager, originOperator, originManager, administrator]) assert.equal(canReceive(user, as(inTransit)), false);
  for (const status of ['APPROVED', 'RECEIVED_COMPLETE', 'RECEIVED_DISCREPANCY']) {
    assert.equal(canReceive(destinationOperator, as({ ...inTransit, status })), false);
  }
});

test('managers of the origin or destination resolve an open alert; nobody else, and only once', () => {
  assert.equal(canResolve(originManager, as(partial)), true);
  assert.equal(canResolve(destinationManager, as(partial)), true);
  for (const user of [originOperator, destinationOperator, administrator, otherManager]) assert.equal(canResolve(user, as(partial)), false);
  assert.equal(canResolve(originManager, as(resolved)), false);
  assert.equal(canResolve(originManager, as(inTransit)), false);
});

test('only the origin manager writes off what is pending in transit, once; a claim keeps it pending', () => {
  assert.equal(pendingInTransit(as(resolved)), true);
  assert.equal(canWriteOff(originManager, as(resolved)), true);
  for (const user of [destinationManager, originOperator, administrator]) assert.equal(canWriteOff(user, as(resolved)), false);
  const lost = { ...resolved, lines: [{ ...resolved.lines[0], inTransitBaseQuantity: '0.000' }],
    discrepancy: { ...resolved.discrepancy, transitLoss: { occurredAt: '2026-10-29T16:00:00Z', authorizedBy: person, reason: 'R', evidence: 'E' } } };
  assert.equal(canWriteOff(originManager, as(lost)), false);
});

test('a resend is requested by the destination operator of a transfer with a shortage', () => {
  assert.equal(canResend(destinationOperator, as(partial)), true);
  for (const user of [originOperator, destinationManager, administrator]) assert.equal(canResend(user, as(partial)), false);
  assert.equal(canResend(destinationOperator, as({ ...partial, discrepancy: null })), false);
  assert.deepEqual(missingLines(as(partial)).map(line => line.missingQuantity), ['3.000']);
  assert.deepEqual(Object.values(TREATMENT_LABEL), ['Reenvío', 'Ajuste', 'Reclamación']);
});

test('received quantities follow the unit rules and never exceed what was sent; 0 is a valid arrival', () => {
  assert.equal(checkReception('10', sent), 'ok');
  assert.equal(checkReception('7', sent), 'ok');
  assert.equal(checkReception('0', sent), 'ok');
  assert.equal(checkReception('11', sent), 'exceeds');
  assert.match(receptionMessage('exceeds', sent)!, /No puede superar lo enviado: 10,000 unidad/);
  assert.equal(checkReception('7.5', sent), 'fraction');
  assert.equal(checkReception('', sent), 'invalid');
  assert.equal(checkReception('-1', sent), 'invalid');
  assert.match(receptionMessage('invalid', sent)!, /use 0 si no llegó nada/);
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

test('reception, resolution and transit loss post to their endpoints with CSRF, their key and their body', async () => {
  const id = inTransit.externalId;
  const lost = { ...resolved, discrepancy: { ...resolved.discrepancy,
    transitLoss: { occurredAt: '2026-10-29T16:00:00Z', authorizedBy: person, reason: 'Extravío', evidence: 'Acta 15' } } };
  const h = harness([csrf(), Response.json(partial), Response.json(resolved), Response.json(lost)]);
  const received = await h.client.receiveTransfer(id, [{ lineNo: 1, quantity: '7' }], 'k1');
  assert.equal(received.lines[0].missingQuantity, '3.000');
  assert.equal(received.discrepancy?.status, 'OPEN');
  assert.equal((await h.client.resolveShortage(id, 'CLAIM', 'k2')).discrepancy?.treatment, 'CLAIM');
  assert.equal((await h.client.writeOffTransit(id, { reason: 'Extravío', evidence: 'Acta 15' }, 'k3')).discrepancy?.transitLoss?.evidence,
    'Acta 15');
  assert.deepEqual(h.calls.slice(1).map(call => [call.url.replace(`http://localhost:8080/api/v1/transfers/${id}/`, ''),
    new Headers(call.options.headers).get('Idempotency-Key'), new Headers(call.options.headers).get('X-CSRF-TOKEN'),
    JSON.parse(String(call.options.body))]), [
    ['receive', 'k1', 't', { lines: [{ lineNo: 1, quantity: '7' }] }],
    ['discrepancy/resolve', 'k2', 't', { treatment: 'CLAIM' }],
    ['discrepancy/transit-loss', 'k3', 't', { reason: 'Extravío', evidence: 'Acta 15' }],
  ]);
});

test('a resend sends its shortage and the listing filters by alert state', async () => {
  const resend = { ...inTransit, status: 'REQUESTED', dispatch: null, approval: null, lines: [{ ...sent, preparedQuantity: null,
    preparedBaseQuantity: null, dispatchedQuantity: null, dispatchedBaseQuantity: null, inTransitBaseQuantity: null }],
    sourceDiscrepancy: { externalId: shortage.externalId, transferExternalId: inTransit.externalId, transferCode: 'TR-000001' } };
  const h = harness([csrf(), Response.json(resend, { status: 201 }),
    Response.json({ readAt: '2026-10-29T18:00:00Z', branch: destination, items: [{ ...partial, discrepancy: { ...shortage, resends: null } }] })]);
  const input = { origin: origin.externalId, sourceDiscrepancy: shortage.externalId, lines: [{ product: sent.productExternalId, unit: 'unidad', quantity: '3' }] };
  assert.equal((await h.client.requestTransfer(input, 'k')).sourceDiscrepancy?.transferCode, 'TR-000001');
  assert.deepEqual(JSON.parse(String(h.calls[1].options.body)), input);
  const listed = await h.client.transfers({ discrepancy: 'OPEN' });
  assert.equal(h.calls[2].url, 'http://localhost:8080/api/v1/transfers?discrepancy=OPEN');
  assert.equal(listed.items[0].discrepancy?.resends, null);
});

test('inconsistent receptions and alerts are rejected instead of shown', async () => {
  for (const broken of [
    { ...partial, discrepancy: { ...shortage, status: 'RESOLVED' } },
    { ...partial, discrepancy: { ...shortage, treatment: 'CLAIM' } },
    { ...partial, discrepancy: { ...shortage, status: 'CLOSED' } },
    { ...partial, discrepancy: { ...shortage, status: 'RESOLVED', treatment: 'LOSS', resolvedAt: '2026-10-29T15:00:00Z', resolvedBy: person } },
    { ...partial, lines: [{ ...partial.lines[0], missingQuantity: null }] },
    { ...partial, arrival: { occurredAt: 'ayer', actor: person } },
  ]) {
    await assert.rejects(harness([Response.json(broken)]).client.transfer(inTransit.externalId), /Respuesta JSON inválida/);
  }
});
