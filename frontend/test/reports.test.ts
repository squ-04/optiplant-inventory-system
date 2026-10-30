import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiClient, ApiError, XLSX_TYPE } from '../src/lib/api/client.ts';
import { REPORT_CATEGORIES, downloadSummary, reportPeriodError } from '../src/lib/reports/reports.ts';

const file = (rows: string, name = 'ventas_2026-10-01_2026-10-30.xlsx') => new Response(new Uint8Array([0x50, 0x4b]), { status: 200,
  headers: { 'Content-Type': XLSX_TYPE, 'Content-Disposition': `attachment; filename="${name}"`, 'X-Report-Rows': rows } });

function client(response: () => Response) {
  const requests: { url: string; init?: RequestInit }[] = [];
  return { requests, client: new ApiClient('http://localhost:8080', async (input, init) => {
    requests.push({ url: String(input), init });
    return response();
  }) };
}

test('RF-0050: the three approved categories and only them, each declaring its date', () => {
  assert.deepEqual(REPORT_CATEGORIES.map(item => item.id), ['movements', 'sales', 'transfers']);
  assert.match(REPORT_CATEGORIES[0].dateNote, /registro/);
  assert.match(REPORT_CATEGORIES[1].dateNote, /confirmación/);
  assert.match(REPORT_CATEGORIES[2].dateNote, /solicitud/);
});

test('RF-0051: the download keeps the binary, the server file name and the session credentials', async () => {
  const h = client(() => file('3'));
  const download = await h.client.downloadReport('sales', { from: '2026-10-01', to: '2026-10-30' });
  assert.equal(download.fileName, 'ventas_2026-10-01_2026-10-30.xlsx');
  assert.equal(download.rows, 3);
  assert.equal(download.blob.type, XLSX_TYPE);
  assert.deepEqual([...new Uint8Array(await download.blob.arrayBuffer())], [0x50, 0x4b]);
  assert.equal(h.requests[0].url, 'http://localhost:8080/api/v1/reports/sales?from=2026-10-01&to=2026-10-30');
  assert.equal(h.requests[0].init?.credentials, 'include');
  assert.match(new Headers(h.requests[0].init?.headers).get('Accept')!, /spreadsheetml/);
});

test('RF-0055: branch and status travel as filters, and the status only to the transfers report', async () => {
  const h = client(() => file('1'));
  const filter = { from: '2026-10-01', to: '2026-10-30', branch: '1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d', status: 'IN_TRANSIT' as const };
  await h.client.downloadReport('transfers', filter);
  await h.client.downloadReport('movements', filter);
  assert.match(h.requests[0].url, /\/reports\/transfers\?.*branch=1a2b3c4d.*status=IN_TRANSIT/);
  assert.doesNotMatch(h.requests[1].url, /status=/);
});

test('RF-0050-CA-03: a rejected report is an error with the server reason, never a file', async () => {
  const h = client(() => Response.json({ code: 'REPORT_TOO_LARGE', detail: 'El reporte supera el máximo de 10.000 filas.' },
    { status: 422, headers: { 'Content-Type': 'application/problem+json' } }));
  await assert.rejects(h.client.downloadReport('sales', { from: '2026-10-01', to: '2026-10-30' }), (error: unknown) => {
    assert.ok(error instanceof ApiError);
    assert.equal(error.status, 422);
    assert.equal(error.problem.code, 'REPORT_TOO_LARGE');
    assert.equal(error.message, 'El reporte supera el máximo de 10.000 filas.');
    return true;
  });
});

test('RF-0050-CA-04: an expired session is reported like any other request', async () => {
  let expired = false;
  const api = new ApiClient('http://localhost:8080', async () => Response.json({}, { status: 401 }), () => { expired = true; });
  await assert.rejects(api.downloadReport('sales', { from: '2026-10-01', to: '2026-10-30' }), ApiError);
  assert.equal(expired, true);
});

test('RF-0055: the period is mandatory, ordered and at most 12 calendar months', () => {
  assert.equal(reportPeriodError('', '2026-10-30'), 'Indique la fecha inicial y la fecha final del período.');
  assert.equal(reportPeriodError('2026-10-30', '2026-10-01'), 'La fecha final del período no puede ser anterior a la inicial.');
  assert.equal(reportPeriodError('2026-01-01', '2026-12-31'), null);
  assert.equal(reportPeriodError('2026-03-15', '2027-03-14'), null);
  assert.equal(reportPeriodError('2026-10-01', '2026-10-01'), null);
  assert.match(reportPeriodError('2026-01-01', '2027-01-01')!, /12 meses/);
  assert.match(reportPeriodError('2026-03-15', '2027-03-15')!, /12 meses/);
  // A leap day has no match a year later: like the server, the limit falls on February 28.
  assert.equal(reportPeriodError('2024-02-29', '2025-02-27'), null);
  assert.match(reportPeriodError('2024-02-29', '2025-02-28')!, /12 meses/);
});

test('RF-0050-CA-03: an empty file says so instead of reading as data', () => {
  const blob = new Blob();
  assert.deepEqual(downloadSummary({ blob, fileName: 'ventas.xlsx', rows: 0 }),
    { empty: true, message: 'Se descargó ventas.xlsx sin filas: no hay registros para esos filtros.' });
  assert.deepEqual(downloadSummary({ blob, fileName: 'ventas.xlsx', rows: 1 }), { empty: false, message: 'Se descargó ventas.xlsx con 1 fila.' });
  assert.deepEqual(downloadSummary({ blob, fileName: 'ventas.xlsx', rows: 10000 }), { empty: false, message: 'Se descargó ventas.xlsx con 10.000 filas.' });
  assert.deepEqual(downloadSummary({ blob, fileName: 'ventas.xlsx', rows: null }), { empty: false, message: 'Se descargó ventas.xlsx.' });
});
