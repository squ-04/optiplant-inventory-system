import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiClient } from '../src/lib/api/client.ts';
import {
  barWidth, comparisonCoverage, comparisonPoints, currentMonthValue, demandCoverage, impactNote, monthError, quantity, rankingScale, restockNote,
} from '../src/lib/dashboard/indicators.ts';

const branch = { externalId: '1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d', code: 'A', name: 'Medellín', active: true };
const other = { externalId: '2a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d', code: 'B', name: 'Cali', active: true };
const product = '2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e';
const seller = { productExternalId: product, sku: 'MAC-01', name: 'Maceta', soldQuantity: '30.000' };
const demand = {
  timeZone: 'America/Bogota', month: '2026-10', from: '2026-10-01', to: '2026-10-30', inProgress: true, dataAvailable: true,
  partialHistory: false, generatedAt: '2026-10-30T15:00:00Z', branch,
  rankings: [{ baseUnitName: 'unidad', productsWithSales: 1, high: [seller], low: [] }],
  withoutSales: [{ productExternalId: product, sku: 'SUS-01', name: 'Sustrato', baseUnitName: 'kg' }],
};
const line = { productExternalId: product, sku: 'MAC-01', name: 'Maceta', baseUnitName: 'unidad', forecastQuantity: null,
  inTransitQuantity: null, receivedQuantity: null, pendingShortageQuantity: null, lostQuantity: null };
const transfer = (overrides: Record<string, unknown>) => ({ externalId: '3c4d5e6f-7a8b-4c9d-8e1f-2a3b4c5d6e7f', code: 'TR-1',
  status: 'IN_TRANSIT', role: 'ORIGIN', counterpart: other, requestedAt: '2026-10-29T15:00:00Z', estimatedArrivalAt: null,
  alertStatus: null, treatment: null, lines: [{ ...line, inTransitQuantity: '10.000' }], ...overrides });
const impact = { generatedAt: '2026-10-30T15:00:00Z', branch, items: [transfer({})] };
const restock = { generatedAt: '2026-10-30T15:00:00Z', branch, items: [{ productExternalId: product, sku: 'MAC-01', name: 'Maceta',
  baseUnitName: 'unidad', availableQuantity: '5.000', minimumQuantity: '5.000' }] };
const metrics = { arrivals: 2, onTimeComplete: 1, lateComplete: 1, discrepancy: 0, inconsistentTiming: 0, compliancePercent: '50.00' };
const comparison = { timeZone: 'America/Bogota', month: '2026-10', from: '2026-10-01', to: '2026-10-30', inProgress: true,
  generatedAt: '2026-10-30T15:00:00Z', branches: [
    { branch, dataAvailable: true, partialHistory: false, salesCount: 3, netAmount: '1280.50', asOrigin: metrics,
      asDestination: { ...metrics, arrivals: 0, onTimeComplete: 0, lateComplete: 0, compliancePercent: null } },
    { branch: other, dataAvailable: false, partialHistory: false, salesCount: null, netAmount: null, asOrigin: null, asDestination: null },
  ] };

function client(...bodies: unknown[]) {
  const urls: string[] = [];
  return { urls, client: new ApiClient('http://localhost:8080', async input => {
    urls.push(String(input));
    return Response.json(bodies.shift());
  }) };
}

test('RF-0046: the demand reads the real endpoint with the month and the branch only when given', async () => {
  const api = client(demand, demand);
  assert.equal((await api.client.demand({})).rankings[0].high[0].sku, 'MAC-01');
  await api.client.demand({ month: '2026-09', branch: branch.externalId });
  assert.deepEqual(api.urls, ['http://localhost:8080/api/v1/dashboard/demand',
    `http://localhost:8080/api/v1/dashboard/demand?month=2026-09&branch=${branch.externalId}`]);
});

test('RF-0046: a malformed ranking is rejected, never drawn', async () => {
  const broken = { ...demand, rankings: [{ ...demand.rankings[0], high: [{ ...seller, soldQuantity: 30 }] }] };
  await assert.rejects(client(broken).client.demand({}));
  await assert.rejects(client({ ...demand, month: '2026-13-01' }).client.demand({}));
});

test('RF-0046: the period says what it covers and missing data is not «no sales»', () => {
  assert.equal(demandCoverage(demand), 'Mes en curso, hasta el 30/10/2026');
  assert.equal(demandCoverage({ ...demand, inProgress: false, partialHistory: true }), 'Mes incompleto: desde el registro de la sucursal');
  assert.equal(demandCoverage({ ...demand, inProgress: false, dataAvailable: false }), 'Sin datos: la sucursal aún no estaba registrada');
});

test('RF-0046: high and low bars share one scale, and a tiny quantity stays visible', () => {
  const ranking = { baseUnitName: 'kg', productsWithSales: 2, high: [{ ...seller, soldQuantity: '200.000' }], low: [{ ...seller, soldQuantity: '0.500' }] };
  const scale = rankingScale(ranking);
  assert.equal(scale, 200);
  assert.equal(barWidth('200.000', scale), 100);
  assert.equal(barWidth('100.000', scale), 50);
  assert.equal(barWidth('0.500', scale), 2);
});

test('the month is validated like the server: format, range and no future month', () => {
  const now = new Date('2026-10-30T15:00:00Z');
  assert.equal(monthError('2026-10', now), null);
  assert.equal(monthError('2026-09', now), null);
  assert.equal(monthError('2026-11', now), 'El mes consultado no puede ser posterior al mes en curso.');
  assert.equal(monthError('', now), 'Indique el mes con el formato AAAA-MM.');
  assert.equal(monthError('2026-13', now), 'Indique el mes con el formato AAAA-MM.');
  assert.equal(monthError('1899-12', now), 'El mes consultado debe estar entre los años 1900 y 9999.');
});

test('the current month is the one of Bogotá, not the one of the browser', () => {
  assert.equal(currentMonthValue(new Date('2026-11-01T03:00:00Z')), '2026-10');
});

test('RF-0047: the transfers read the real endpoint, and an unknown state is rejected', async () => {
  const api = client(impact, { ...impact, items: [transfer({ status: 'CLOSED' })] });
  assert.equal((await api.client.transferImpact(branch.externalId)).items[0].lines[0].inTransitQuantity, '10.000');
  assert.deepEqual(api.urls, [`http://localhost:8080/api/v1/dashboard/transfers?branch=${branch.externalId}`]);
  await assert.rejects(api.client.transferImpact());
});

test('RF-0047: each state explains its effect from the side of the consulted branch', () => {
  const read = (overrides: Record<string, unknown>) => impactNote(transfer(overrides) as never);
  assert.equal(read({ status: 'REQUESTED' }), 'Previsión: aún no reserva ni mueve existencias');
  assert.equal(read({ status: 'APPROVED', role: 'DESTINATION' }), 'Previsión: aún no reserva ni mueve existencias');
  assert.equal(read({}), 'Ya salió del físico de esta sucursal');
  assert.equal(read({ role: 'DESTINATION' }), 'Aún no entra al físico de esta sucursal');
  assert.equal(read({ status: 'RECEIVED_DISCREPANCY' }), 'Lo recibido ya está en el físico de destino');
  assert.equal(read({ status: 'RECEIVED_DISCREPANCY', role: 'DESTINATION', alertStatus: 'OPEN' }),
    'Lo recibido ya está en el físico de esta sucursal. Alerta de faltante abierta');
  // «Resuelto» documents a decision: the note never implies the shortage is settled.
  assert.equal(read({ status: 'RECEIVED_DISCREPANCY', alertStatus: 'RESOLVED', treatment: 'CLAIM' }),
    'Lo recibido ya está en el físico de destino. Alerta resuelta con reclamación; no cambia las cantidades');
});

test('RF-0047: a quantity that does not apply is not a zero', () => {
  assert.equal(quantity(null, 'unidad'), 'No aplica');
  assert.equal(quantity('0.000', 'unidad'), '0,000 unidad');
  assert.equal(quantity('1250.500', 'kg'), '1.250,500 kg');
});

test('RF-0048: the indicator reads the real endpoint and tells an empty shelf from one at its minimum', async () => {
  const api = client(restock);
  assert.equal((await api.client.restockIndicators()).items[0].availableQuantity, '5.000');
  assert.deepEqual(api.urls, ['http://localhost:8080/api/v1/dashboard/restock']);
  assert.equal(restockNote(restock.items[0]), 'En su mínimo');
  assert.equal(restockNote({ ...restock.items[0], availableQuantity: '2.000' }), 'Por debajo de su mínimo');
  assert.equal(restockNote({ ...restock.items[0], availableQuantity: '0.000', minimumQuantity: '0.000' }), 'Sin existencias');
});

test('RF-0049: the comparison keeps null apart from zero', async () => {
  const api = client(comparison);
  const report = await api.client.branchComparison('2026-10');
  assert.deepEqual(api.urls, ['http://localhost:8080/api/v1/dashboard/branch-comparison?month=2026-10']);
  assert.equal(comparisonCoverage(report.branches[0], report), 'Mes en curso, hasta el 30/10/2026');
  assert.equal(comparisonCoverage(report.branches[1], report), 'Sin datos: la sucursal aún no estaba registrada');
  assert.deepEqual(comparisonPoints(report.branches).map(point => point.amount), [1280.5, null]);
  assert.equal(comparisonPoints(report.branches)[1].exact, 'Sin datos');
});

test('RF-0049: a report that mixes figures with missing data is rejected', async () => {
  const mixed = { ...comparison, branches: [{ ...comparison.branches[1], salesCount: 0 }] };
  await assert.rejects(client(mixed).client.branchComparison());
  const unmeasured = { ...comparison, branches: [{ ...comparison.branches[0], asOrigin: null }] };
  await assert.rejects(client(unmeasured).client.branchComparison());
});
