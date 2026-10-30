import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiClient } from '../src/lib/api/client.ts';
import {
  chartPoints, monthCoverage, monthName, netAmount, productRows, rotation, rotationNote, rotationPeriodError, salesCount,
} from '../src/lib/dashboard/dashboard.ts';

const branch = { externalId: '1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d', code: 'A', name: 'Medellín', active: true };
const maceta = { productExternalId: '2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e', sku: 'MAC-01', name: 'Maceta', baseUnitName: 'unidad' };
const sustrato = { productExternalId: '3c4d5e6f-7a8b-4c9d-8e1f-2a3b4c5d6e7f', sku: 'SUS-01', name: 'Sustrato', baseUnitName: 'kg' };
const month = (overrides: Record<string, unknown>) => ({ month: '2026-08', from: '2026-08-01', to: '2026-08-31', inProgress: false,
  dataAvailable: true, partialHistory: false, salesCount: 0, netAmount: '0.00', returnsCount: 0, products: [], ...overrides });
const comparison = {
  timeZone: 'America/Bogota', generatedAt: '2026-10-29T15:00:00Z', branch, recordedFrom: '2026-07-10',
  months: [
    month({ month: '2026-07', from: '2026-07-01', to: '2026-07-31', dataAvailable: false, salesCount: null, netAmount: null,
      returnsCount: null }),
    month({ month: '2026-08', partialHistory: true, salesCount: 1, netAmount: '40.00',
      products: [{ ...sustrato, soldQuantity: '1.500', returnedQuantity: '0.000' }] }),
    month({ month: '2026-09', from: '2026-09-01', to: '2026-09-30' }),
    month({ month: '2026-10', from: '2026-10-01', to: '2026-10-29', inProgress: true, salesCount: 2, netAmount: '1280.50',
      returnsCount: 1, products: [{ ...maceta, soldQuantity: '3.000', returnedQuantity: '1.000' }] }),
  ],
};
const item = (overrides: Record<string, unknown>) => ({ ...maceta, historyStartedAt: '2026-02-01T05:00:00Z', soldQuantity: '20.000',
  averageQuantity: '10.000', rotation: '2.00', status: 'CALCULATED', ...overrides });

function client(...bodies: unknown[]) {
  const urls: string[] = [];
  return { urls, client: new ApiClient('http://localhost:8080', async input => {
    urls.push(String(input));
    return Response.json(bodies.shift());
  }) };
}

test('RF-0044: the comparison is read from the real endpoint, with the chosen branch only when given', async () => {
  const api = client(comparison, comparison);
  assert.deepEqual((await api.client.salesComparison()).months.map(item => item.month), ['2026-07', '2026-08', '2026-09', '2026-10']);
  await api.client.salesComparison(branch.externalId);
  assert.deepEqual(api.urls, ['http://localhost:8080/api/v1/dashboard/sales-comparison',
    `http://localhost:8080/api/v1/dashboard/sales-comparison?branch=${branch.externalId}`]);
});

test('RF-0044: a month without data must not carry invented figures', async () => {
  const forged = { ...comparison, months: [month({ dataAvailable: false })] };
  await assert.rejects(client(forged).client.salesComparison(), /Respuesta JSON inválida/);
});

test('RF-0044: missing data, a real zero and the month in progress read differently', () => {
  const [none, partial, zero, current] = comparison.months as Parameters<typeof monthCoverage>[0][];
  assert.equal(monthCoverage(none), 'Sin datos: la sucursal aún no estaba registrada');
  assert.equal(netAmount(none), 'Sin datos');
  assert.equal(salesCount(none), 'Sin datos');
  assert.equal(monthCoverage(partial), 'Mes incompleto: desde el registro de la sucursal');
  assert.equal(monthCoverage(zero), 'Mes completo');
  assert.equal(salesCount(zero), 'Sin ventas');
  assert.equal(netAmount(zero), '0,00 COP');
  assert.equal(monthCoverage(current), 'Mes en curso, hasta el 29/10/2026');
  assert.equal(netAmount(current), '1.280,50 COP');
  assert.equal(salesCount(current), '2 ventas');
  assert.equal(monthName('2026-10'), 'octubre de 2026');
});

test('RF-0044: units stay by product in their own base unit, with returns apart', () => {
  const rows = productRows(comparison.months as Parameters<typeof productRows>[0]);
  assert.deepEqual(rows.map(row => [row.sku, row.baseUnitName]), [['MAC-01', 'unidad'], ['SUS-01', 'kg']]);
  assert.deepEqual(rows[0].cells, [null, { sold: '0.000', returned: '0.000' }, { sold: '0.000', returned: '0.000' },
    { sold: '3.000', returned: '1.000' }]);
  assert.deepEqual(rows[1].cells[1], { sold: '1.500', returned: '0.000' });
  const points = chartPoints(comparison.months as Parameters<typeof chartPoints>[0]);
  assert.deepEqual(points.map(point => point.amount), [null, 40, 0, 1280.5]);
  assert.equal(points[3].inProgress, true);
});

test('RF-0045: the rotation is requested for the period and shows «No calculable» with its reason', async () => {
  const report = { timeZone: 'America/Bogota', from: '2026-03-01', to: '2026-03-10', measuredUntil: '2026-03-11T05:00:00Z',
    generatedAt: '2026-10-29T15:00:00Z', branch, items: [item({}),
      item({ ...sustrato, status: 'INSUFFICIENT_HISTORY', averageQuantity: null, rotation: null, historyStartedAt: '2026-03-03T05:00:00Z' }),
      item({ status: 'ZERO_AVERAGE', averageQuantity: '0.000', rotation: null, soldQuantity: '0.000' }),
      item({ rotation: '0.00', soldQuantity: '0.000' })] };
  const api = client(report);
  const parsed = await api.client.rotation({ from: '2026-03-01', to: '2026-03-10', branch: branch.externalId });
  assert.equal(api.urls[0], `http://localhost:8080/api/v1/dashboard/rotation?from=2026-03-01&to=2026-03-10&branch=${branch.externalId}`);
  const [calculated, insufficient, zeroAverage, zeroSales] = parsed.items;
  assert.equal(rotation(calculated), '2,00');
  assert.equal(rotationNote(calculated), '');
  assert.equal(rotation(insufficient), 'No calculable');
  assert.equal(rotationNote(insufficient), 'Historia insuficiente: el inventario de la sucursal se registra desde el 03/03/2026');
  assert.equal(rotation(zeroAverage), 'No calculable');
  assert.equal(rotationNote(zeroAverage), 'Sin existencias físicas durante el período');
  assert.equal(rotation(zeroSales), '0,00');
  assert.equal(rotationNote(zeroSales), 'Sin ventas en el período, con existencias');
  const inconsistent = item({ status: 'INCONSISTENT_HISTORY', averageQuantity: null, rotation: null }) as Parameters<typeof rotationNote>[0];
  assert.equal(rotation(inconsistent), 'No calculable');
  assert.match(rotationNote(inconsistent), /no coincide con el saldo/);
  // Real sales that round to a rotation of 0,00 are never announced as no sales.
  const small = item({ soldQuantity: '1.000', averageQuantity: '1000.000', rotation: '0.00' }) as Parameters<typeof rotationNote>[0];
  assert.equal(rotation(small), '0,00');
  assert.equal(rotationNote(small), '');
});

test('RF-0045: a rotation that is not calculable must not carry a value', async () => {
  const forged = { timeZone: 'America/Bogota', from: '2026-03-01', to: '2026-03-10', measuredUntil: '2026-03-11T05:00:00Z',
    generatedAt: '2026-10-29T15:00:00Z', branch, items: [item({ status: 'ZERO_AVERAGE' })] };
  await assert.rejects(client(forged).client.rotation({ from: '2026-03-01', to: '2026-03-10' }), /Respuesta JSON inválida/);
});

test('RF-0045: the period cannot end after today in Bogotá', () => {
  // 23:30 in Bogotá on the 29th is already the 30th in UTC.
  const now = new Date('2026-10-30T04:30:00Z');
  assert.equal(rotationPeriodError('2026-10-01', '2026-10-29', now), null);
  assert.match(rotationPeriodError('2026-10-01', '2026-10-30', now) ?? '', /posterior a hoy/);
  assert.match(rotationPeriodError('2026-10-10', '2026-10-01', now) ?? '', /anterior a la inicial/);
  assert.match(rotationPeriodError('', '2026-10-01', now) ?? '', /Indique/);
});
