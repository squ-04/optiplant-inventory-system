import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiClient } from '../src/lib/api/client.ts';
import {
  ROUTE_PRIORITIES, bogotaInstant, bogotaLocal, canDispatch, dispatchProblem, isRoutePriority, routeFor, routePriorityLabel,
} from '../src/lib/logistics/routes.ts';

const origin = { externalId: '1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d', code: 'A', name: 'Medellín', active: true };
const destination = { externalId: '2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e', code: 'B', name: 'Bogotá', active: true };
const person = { code: 'U1', fullName: 'Ana Pérez' };
const route = { externalId: '5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b', code: 'R-AB', origin, destination, priority: 'HIGH', active: true };
const line = {
  lineNo: 1, productExternalId: '3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f', sku: 'MAC-1', productName: 'Maceta', unit: 'caja',
  factor: '12', baseUnitName: 'unidad', requestedQuantity: '2.000', requestedBaseQuantity: '24.000', preparedQuantity: '1.000',
  preparedBaseQuantity: '12.000', dispatchedQuantity: '1.000', dispatchedBaseQuantity: '12.000', originAvailableQuantity: '18.000',
  unitDivisible: false, baseDivisible: false,
};
const dispatched = {
  externalId: '4d5e6f7a-8b9c-4d0e-9f1a-2b3c4d5e6f7a', code: 'TR-000001', status: 'IN_TRANSIT', origin, destination,
  requestedAt: '2026-10-28T15:00:00Z', requestedBy: person, approval: { occurredAt: '2026-10-28T17:00:00Z', actor: person },
  dispatch: { carrier: 'Transportes Andinos', departedAt: '2026-10-28T18:00:00Z', estimatedArrivalAt: '2026-10-29T20:00:00Z',
    actor: person, routeCode: 'R-AB', routePriority: 'HIGH' }, lines: [line], events: null,
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
const csrf = () => Response.json({ token: 't', headerName: 'X-CSRF-TOKEN' });

test('priorities are high, medium and low, in classification order', () => {
  assert.deepEqual(ROUTE_PRIORITIES, ['HIGH', 'MEDIUM', 'LOW']);
  assert.deepEqual(ROUTE_PRIORITIES.map(routePriorityLabel), ['Alta', 'Media', 'Baja']);
  assert.equal(isRoutePriority('URGENT'), false);
});

test('the dispatch uses the active route of the same origin and destination', () => {
  const reverse = { ...route, origin: destination, destination: origin };
  assert.equal(routeFor([reverse, route], { origin, destination }), route);
  assert.equal(routeFor([reverse], { origin, destination }), null);
  assert.equal(routeFor([{ ...route, active: false }], { origin, destination }), null);
});

test('only the origin operator dispatches, only an approved transfer', () => {
  const at = (status: string) => ({ status, origin }) as Parameters<typeof canDispatch>[1];
  assert.equal(canDispatch({ role: 'OPERATOR', branchExternalId: origin.externalId }, at('APPROVED')), true);
  for (const status of ['PREPARING', 'IN_TRANSIT', 'CANCELLED']) {
    assert.equal(canDispatch({ role: 'OPERATOR', branchExternalId: origin.externalId }, at(status)), false);
  }
  assert.equal(canDispatch({ role: 'MANAGER', branchExternalId: origin.externalId }, at('APPROVED')), false);
  assert.equal(canDispatch({ role: 'OPERATOR', branchExternalId: destination.externalId }, at('APPROVED')), false);
  assert.equal(canDispatch({ role: 'ADMIN', branchExternalId: null }, at('APPROVED')), false);
});

test('Bogotá entries convert exactly to instants at -05:00', () => {
  assert.equal(bogotaInstant('2026-10-28T13:05'), '2026-10-28T13:05:00-05:00');
  assert.equal(Date.parse(bogotaInstant('2026-10-28T23:30')!), Date.parse('2026-10-29T04:30:00Z'));
  assert.equal(bogotaInstant('2026-02-30T10:00'), null);
  assert.equal(bogotaInstant(''), null);
  assert.equal(bogotaLocal(new Date('2026-10-29T04:30:59Z')), '2026-10-28T23:30');
});

test('dispatch data is checked as the server checks it', () => {
  const now = new Date('2026-10-28T19:00:30Z'); // 14:00 in Bogotá
  const approved = '2026-10-28T18:10:40Z'; // 13:10:40 in Bogotá
  const draft = { carrier: 'Transportes Andinos', departedAt: '2026-10-28T13:30', estimatedArrivalAt: '2026-10-29T09:00' };
  assert.equal(dispatchProblem(draft, now, approved), null);
  assert.equal(dispatchProblem({ ...draft, carrier: '  ' }, now, approved), 'Indique el transportista: nombre o identificador.');
  assert.match(dispatchProblem({ ...draft, carrier: 'x'.repeat(161) }, now, approved)!, /hasta 160/);
  assert.equal(dispatchProblem({ ...draft, estimatedArrivalAt: '' }, now, approved), 'Indique la fecha y hora estimada de llegada.');
  assert.equal(dispatchProblem({ ...draft, estimatedArrivalAt: '2026-10-28T13:29' }, now, approved),
    'La llegada estimada no puede ser anterior a la salida real.');
  // A device clock up to 5 minutes ahead is tolerated, as on the server; beyond it the departure is future.
  assert.equal(dispatchProblem({ ...draft, departedAt: '2026-10-28T14:05', estimatedArrivalAt: '2026-10-28T15:00' }, now, approved), null);
  assert.match(dispatchProblem({ ...draft, departedAt: '2026-10-28T14:06', estimatedArrivalAt: '2026-10-28T15:00' }, now, approved)!,
    /posterior al momento actual/);
  // The approval's own minute is accepted; the minute before is not.
  assert.equal(dispatchProblem({ ...draft, departedAt: '2026-10-28T13:10' }, now, approved), null);
  assert.match(dispatchProblem({ ...draft, departedAt: '2026-10-28T13:09' }, now, approved)!, /anterior a la aprobación/);
});

test('dispatchTransfer posts the data with CSRF and its key, and the response keeps the dispatch', async () => {
  const h = harness([csrf(), Response.json(dispatched)]);
  const input = { carrier: 'Transportes Andinos', departedAt: '2026-10-28T13:00:00-05:00', estimatedArrivalAt: '2026-10-29T15:00:00-05:00' };
  const result = await h.client.dispatchTransfer(dispatched.externalId, input, 'key-9');
  assert.equal(result.dispatch?.carrier, 'Transportes Andinos');
  assert.equal(result.lines[0].dispatchedQuantity, '1.000');
  assert.equal(h.calls[1].url, `http://localhost:8080/api/v1/transfers/${dispatched.externalId}/dispatch`);
  assert.equal(new Headers(h.calls[1].options.headers).get('Idempotency-Key'), 'key-9');
  assert.deepEqual(JSON.parse(String(h.calls[1].options.body)), input);
});

test('a dispatch with an unknown priority or half a quantity is rejected as invalid', async () => {
  const h = harness([Response.json({ ...dispatched, dispatch: { ...dispatched.dispatch, routePriority: 'URGENT' } }),
    Response.json({ ...dispatched, lines: [{ ...line, dispatchedBaseQuantity: null }] })]);
  await assert.rejects(h.client.transfer(dispatched.externalId));
  await assert.rejects(h.client.transfer(dispatched.externalId));
});

test('routes are listed, created and updated through their endpoints', async () => {
  const h = harness([Response.json([route]), csrf(), Response.json(route, { status: 201 }),
    Response.json({ ...route, priority: 'LOW', active: false })]);
  assert.deepEqual(await h.client.routes('HIGH'), [route]);
  assert.equal(h.calls[0].url, 'http://localhost:8080/api/v1/logistics/routes?priority=HIGH');
  await h.client.createRoute({ code: 'R-AB', origin: origin.externalId, destination: destination.externalId, priority: 'HIGH' });
  const updated = await h.client.updateRoute(route.externalId, { code: 'R-AB', priority: 'LOW', active: false });
  assert.equal(updated.priority, 'LOW');
  assert.equal(h.calls[3].url, `http://localhost:8080/api/v1/logistics/routes/${route.externalId}/update`);
  assert.deepEqual(JSON.parse(String(h.calls[3].options.body)), { code: 'R-AB', priority: 'LOW', active: false });
  await assert.rejects(harness([Response.json([{ ...route, priority: 'URGENT' }])]).client.routes());
});

test('a partial arrival is named as RF-0042 names it', async () => {
  const { TRANSFER_STATUS } = await import('../src/lib/transfers/statuses.ts');
  assert.equal(TRANSFER_STATUS.RECEIVED_DISCREPANCY.label, 'Recibida con discrepancia');
});
