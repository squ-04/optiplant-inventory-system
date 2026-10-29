import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiClient } from '../src/lib/api/client.ts';
import {
  bogotaDateTime, compliance, complianceBase, currentMonth, deliveryOutcome, deviation, duration, periodError,
} from '../src/lib/logistics/compliance.ts';
import { documentStatusLabel } from '../src/lib/format.ts';

const origin = { externalId: '1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d', code: 'A', name: 'Medellín', active: true };
const destination = { externalId: '2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e', code: 'B', name: 'Bogotá', active: true };
const person = { code: 'U1', fullName: 'Ana Pérez' };
const metrics = (arrivals: number, onTime: number, late: number, discrepancy: number, percent: string | null, inconsistent = 0) =>
  ({ arrivals, onTimeComplete: onTime, lateComplete: late, discrepancy, inconsistentTiming: inconsistent, compliancePercent: percent });
// RF-0040-CA-02: dispatched 10:00, single partial arrival 12:00 (Bogotá).
const partialTiming = { estimatedSeconds: 3600, actualSeconds: 7200, deviationSeconds: 3600, delivery: 'DISCREPANCY' as const,
  onTime: false, overdue: false, consistent: true };
const transfer = {
  externalId: '4d5e6f7a-8b9c-4d0e-9f1a-2b3c4d5e6f7a', code: 'TR-000001', status: 'RECEIVED_DISCREPANCY', origin, destination,
  requestedAt: '2026-10-20T13:00:00Z', requestedBy: person, approval: { occurredAt: '2026-10-20T14:00:00Z', actor: person },
  dispatch: { carrier: 'Transportes Andinos', departedAt: '2026-10-20T15:00:00Z', estimatedArrivalAt: '2026-10-20T16:00:00Z',
    actor: person, routeCode: 'R-AB', routePriority: 'HIGH' }, arrival: { occurredAt: '2026-10-20T17:00:00Z', actor: person },
  timing: partialTiming, discrepancy: null, sourceDiscrepancy: null, lines: [], events: null,
};
const report = {
  timeZone: 'America/Bogota', from: '2026-10-01', to: '2026-10-29', generatedAt: '2026-10-29T15:00:00Z', branch: destination,
  summary: metrics(2, 1, 0, 1, '50.00'),
  branches: [{ branch: destination, asOrigin: metrics(0, 0, 0, 0, null), asDestination: metrics(2, 1, 0, 1, '50.00') }],
  routes: [{ externalId: '5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b', code: 'R-AB', origin, destination, priority: 'HIGH', active: true,
    metrics: metrics(2, 1, 0, 1, '50.00') }],
  overdueInTransit: [{ externalId: '6f7a8b9c-0d1e-4f2a-8b3c-4d5e6f7a8b9c', code: 'TR-000003', origin, destination, routeCode: 'R-AB',
    carrier: 'Transportes Andinos', departedAt: '2026-10-28T13:00:00Z', estimatedArrivalAt: '2026-10-28T14:00:00Z' }],
};

function client(...bodies: unknown[]) {
  const urls: string[] = [];
  return { urls, client: new ApiClient('http://localhost:8080', async input => {
    urls.push(String(input));
    return Response.json(bodies.shift());
  }) };
}

test('RF-0040: a partial arrival keeps its two hours and is never presented as complete', async () => {
  const h = client(transfer);
  const read = await h.client.transfer(transfer.externalId);
  assert.deepEqual(read.timing, partialTiming);
  assert.equal(duration(read.timing!.actualSeconds!), '2 h');
  assert.equal(deviation(read.timing!), '1 h después de lo estimado');
  assert.equal(deliveryOutcome(read.timing!), 'Recibida con discrepancia: entrega parcial, no cumple');
  // The business clock is shown whatever the device's zone: 17:00 UTC is 12:00 in Bogotá.
  assert.match(bogotaDateTime(transfer.arrival.occurredAt), /20\/10\/2026.*12:00/);
});

test('RF-0040: timing without arrival invents nothing, and inconsistent timings are rejected', async () => {
  const pending = { estimatedSeconds: 14400, actualSeconds: null, deviationSeconds: null, delivery: null, onTime: null, overdue: true,
    consistent: true };
  const h = client({ ...transfer, status: 'IN_TRANSIT', arrival: null, timing: pending },
    { ...transfer, timing: { ...partialTiming, onTime: true } },
    { ...transfer, timing: { ...pending, delivery: 'COMPLETE' } },
    { ...transfer, dispatch: null, arrival: null, status: 'APPROVED' },
    { ...transfer, timing: undefined });
  assert.equal(deliveryOutcome((await h.client.transfer(transfer.externalId)).timing!), 'En tránsito, llegada estimada vencida');
  await assert.rejects(h.client.transfer(transfer.externalId));
  await assert.rejects(h.client.transfer(transfer.externalId));
  await assert.rejects(h.client.transfer(transfer.externalId));
  // A replay stored before RF-0040 carries no timing: it reads as not computed.
  assert.equal((await h.client.transfer(transfer.externalId)).timing, null);
});

test('RF-0043: the report keeps its 50 % with the overdue transfer apart and «No calculable» without arrivals', async () => {
  const h = client(report, { ...report, summary: metrics(0, 0, 0, 0, null) });
  const read = await h.client.complianceReport({ from: '2026-10-01', to: '2026-10-29', branch: destination.externalId });
  assert.equal(h.urls[0], `http://localhost:8080/api/v1/logistics/compliance?from=2026-10-01&to=2026-10-29&branch=${destination.externalId}`);
  assert.equal(compliance(read.summary), '50,00 %');
  assert.equal(complianceBase(read.summary), '1 de 2 llegadas completas a tiempo');
  assert.equal(read.overdueInTransit.length, 1);
  assert.equal(compliance(read.branches[0].asOrigin), 'No calculable');
  const empty = await h.client.complianceReport({ from: '2026-10-01', to: '2026-10-29' });
  assert.equal(h.urls[1], 'http://localhost:8080/api/v1/logistics/compliance?from=2026-10-01&to=2026-10-29');
  assert.equal(compliance(empty.summary), 'No calculable');
  assert.equal(complianceBase(empty.summary), 'Sin llegadas confirmadas en el período');
});

test('an arrival recorded before its departure is shown as an anomaly instead of hiding the listing', async () => {
  const anomalous = { ...partialTiming, actualSeconds: -120, deviationSeconds: -3720, delivery: 'COMPLETE' as const,
    consistent: false };
  const h = client({ readAt: '2026-10-29T15:00:00Z', branch: null,
    items: [{ ...transfer, status: 'RECEIVED_COMPLETE', timing: anomalous }, { ...transfer, externalId: '7a8b9c0d-1e2f-4a3b-8c4d-5e6f7a8b9c0d' }] },
    { ...transfer, timing: { ...anomalous, consistent: true } }, { ...transfer, timing: { ...anomalous, onTime: true } },
    { ...report, summary: metrics(2, 1, 0, 0, '50.00', 1) });
  const listing = await h.client.transfers();
  assert.equal(listing.items.length, 2);
  assert.equal(deliveryOutcome(listing.items[0].timing!),
    'La llegada registrada es anterior a la salida; revise los datos del despacho. No cuenta como cumplida');
  assert.equal(deviation(listing.items[0].timing!), null);
  // A negative duration must be flagged, and is never on time.
  await assert.rejects(h.client.transfer(transfer.externalId));
  await assert.rejects(h.client.transfer(transfer.externalId));
  const read = await h.client.complianceReport({ from: '2026-10-01', to: '2026-10-29' });
  assert.equal(read.summary.inconsistentTiming, 1);
  assert.equal(compliance(read.summary), '50,00 %');
});

test('RF-0043: a report whose KPI contradicts its counts is rejected', async () => {
  const h = client({ ...report, summary: metrics(0, 0, 0, 0, '0.00') }, { ...report, summary: metrics(2, 1, 0, 0, '50.00') },
    { ...report, routes: [{ ...report.routes[0], metrics: metrics(1, 1, 0, 0, null) }] });
  for (let i = 0; i < 3; i++) await assert.rejects(h.client.complianceReport({ from: '2026-10-01', to: '2026-10-29' }));
});

test('periods default to the current Bogotá month and are validated before asking', () => {
  // 03:00 UTC on 1 November is still 31 October in Bogotá.
  assert.deepEqual(currentMonth(new Date('2026-11-01T03:00:00Z')), { from: '2026-10-01', to: '2026-10-31' });
  assert.equal(periodError('2026-10-01', '2026-10-29'), null);
  assert.equal(periodError('2026-10-29', '2026-10-01'), 'La fecha final del período no puede ser anterior a la inicial.');
  assert.equal(periodError('', '2026-10-01'), 'Indique la fecha inicial y la fecha final del período.');
});

test('durations read in hours and minutes without rounding a short delay to zero', () => {
  assert.equal(duration(0), '0 min');
  assert.equal(duration(30), 'menos de 1 min');
  assert.equal(duration(5400), '1 h 30 min');
  assert.equal(duration(600), '10 min');
  assert.equal(deviation({ ...partialTiming, deviationSeconds: -1800 }), '30 min antes de lo estimado');
  assert.equal(deviation({ ...partialTiming, deviationSeconds: 0 }), 'A la hora estimada');
  // The server rounds lateness up, so half a second late reads as late, never as the estimated instant.
  assert.equal(deviation({ ...partialTiming, delivery: 'COMPLETE', deviationSeconds: 1 }), 'menos de 1 min después de lo estimado');
});

test('RF-0042: every view names the received-with-discrepancy state the same way', () => {
  assert.equal(documentStatusLabel('RECEIVED_DISCREPANCY'), 'Recibida con discrepancia');
  assert.equal(documentStatusLabel('RECEIVED_COMPLETE'), 'Recibida completa');
  assert.equal(documentStatusLabel('RECEIVED'), 'Recibida');
  assert.equal(documentStatusLabel('DRAFT'), 'Borrador');
});
