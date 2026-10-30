import assert from 'node:assert/strict';
import test from 'node:test';
import type { Transfer, TransferHistory } from '../src/lib/api/client.ts';
import { ApiClient } from '../src/lib/api/client.ts';
import { alertsNavigationLabel } from '../src/lib/inventory/alerts.ts';
import {
  alertCounts, dismissResolution, dismissedResolutions, recentResolutions, resolutionNotice, shortageNotice,
} from '../src/lib/transfers/shortageAlerts.ts';

const origin = { externalId: '1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d', code: 'MED', name: 'Medellín', active: true };
const destination = { externalId: '2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e', code: 'BOG', name: 'Bogotá', active: true };
const other = { code: 'ger.bog', fullName: 'Andrea Torres' };
const me = { code: 'ger.med', fullName: 'Laura Gómez' };

function resolvedTransfer(code: string, discrepancy: string, resolvedAt: string, resolvedBy = other): Transfer {
  return {
    externalId: `${discrepancy.slice(0, 8)}-1111-4111-8111-111111111111`, code, status: 'RECEIVED_DISCREPANCY', origin, destination,
    discrepancy: { externalId: discrepancy, status: 'RESOLVED', openedAt: '2026-10-29T12:00:00Z', treatment: 'CLAIM', resolvedAt,
      resolvedBy, transitLoss: null, resends: null },
  } as unknown as Transfer;
}

const recent = resolvedTransfer('TR-000002', '5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b', '2026-10-29T15:00:00Z');
const old = resolvedTransfer('TR-000001', '6f7a8b9c-0d1e-4f2a-8b3c-4d5e6f7a8b9c', '2026-10-28T11:59:59Z');
const mine = resolvedTransfer('TR-000003', '7a8b9c0d-1e2f-4a3b-8c4d-5e6f7a8b9c0d', '2026-10-29T14:00:00Z', me);
const history: TransferHistory = { readAt: '2026-10-29T16:00:00Z', branch: origin, total: 3, items: [recent, old, mine] };

class MemoryStorage {
  values = new Map<string, string>();
  getItem(key: string) { return this.values.get(key) ?? null; }
  setItem(key: string, value: string) { this.values.set(key, value); }
}

test('the shortage notice states the open count of the scope in words', () => {
  assert.equal(shortageNotice(0, false), null);
  assert.equal(shortageNotice(1, false), '1 faltante de transferencia en su sucursal está pendiente de resolver.');
  assert.equal(shortageNotice(2, true), '2 faltantes de transferencias en la red están pendientes de resolver.');
});

test('the navigation name keeps the restock wording and adds open shortages', () => {
  // Previous behavior: without shortages the name is the restock one.
  assert.equal(alertsNavigationLabel(0), 'Alertas de reabastecimiento, sin alertas activas');
  assert.equal(alertsNavigationLabel(2), 'Alertas de reabastecimiento, 2 activas');
  assert.equal(alertsNavigationLabel(2, 0), 'Alertas de reabastecimiento, 2 activas');
  assert.equal(alertsNavigationLabel(0, 1), 'Alertas: sin reabastecimiento activo y 1 faltante de transferencia abierto');
  assert.equal(alertsNavigationLabel(2, 3), 'Alertas: 2 de reabastecimiento activas y 3 faltantes de transferencias abiertos');
});

test('only resolutions by another manager within 24 h of the server reading are announced', () => {
  assert.deepEqual(recentResolutions(history, me, new Set()).map(item => item.code), ['TR-000002']);
  assert.deepEqual(recentResolutions(history, me, new Set([recent.discrepancy!.externalId])), []);
  assert.deepEqual(recentResolutions(undefined, me, new Set()), []);
  // Exactly 24 h before the reading is still announced.
  const edge = resolvedTransfer('TR-000004', '8b9c0d1e-2f3a-4b4c-8d5e-6f7a8b9c0d1e', '2026-10-28T16:00:00Z');
  assert.deepEqual(recentResolutions({ ...history, items: [edge] }, me, new Set()).map(item => item.code), ['TR-000004']);
});

test('the resolution notice names the transfer, branches, manager and treatment', () => {
  assert.match(resolutionNotice(recent),
    /^La alerta de faltante de TR-000002 \(MED hacia BOG\) fue resuelta por Andrea Torres \(ger\.bog\) con tratamiento reclamación el .+\.$/);
  assert.doesNotMatch(resolutionNotice(recent), /\.\.$/);
});

test('dismissals are kept per user and survive unavailable storage', () => {
  const storage = new MemoryStorage();
  assert.deepEqual([...dismissedResolutions(storage, 'ger.med')], []);
  dismissResolution(storage, 'ger.med', 'a');
  dismissResolution(storage, 'ger.med', 'a');
  dismissResolution(storage, 'ger.med', 'b');
  assert.deepEqual([...dismissedResolutions(storage, 'ger.med')], ['a', 'b']);
  assert.deepEqual([...dismissedResolutions(storage, 'ger.bog')], []);
  storage.setItem('optiplant.dismissed-shortage-resolutions.ger.bog', 'not json');
  assert.deepEqual([...dismissedResolutions(storage, 'ger.bog')], []);
  const broken = { getItem: () => { throw new Error('blocked'); }, setItem: () => { throw new Error('blocked'); } };
  assert.deepEqual([...dismissedResolutions(broken, 'ger.med')], []);
  assert.doesNotThrow(() => dismissResolution(broken, 'ger.med', 'a'));
  assert.deepEqual([...dismissedResolutions(null, 'ger.med')], []);
});

test('the shortage listing forwards its abort signal', async () => {
  let received: AbortSignal | null | undefined;
  const client = new ApiClient('http://localhost:8080', async (_url, options) => {
    received = options?.signal;
    return Response.json({ readAt: '2026-10-29T16:00:00Z', branch: null, total: 0, items: [] });
  });
  const controller = new AbortController();
  await client.transfers({ discrepancy: 'OPEN' }, controller.signal);
  assert.equal(received, controller.signal);
});

test('the listing exposes every match as total, beyond the listed rows, and rejects an incoherent one', async () => {
  const listing = (total: unknown) => new ApiClient('http://localhost:8080',
    async () => Response.json({ readAt: '2026-10-29T16:00:00Z', branch: null, total, items: [] }));
  // The server lists at most its limit of rows; the counter reads the total of open alerts.
  assert.equal((await listing(150).transfers({ discrepancy: 'OPEN' })).total, 150);
  assert.equal((await listing(0).transfers()).total, 0);
  for (const bad of [undefined, -1, 1.5, '2', null]) {
    await assert.rejects(listing(bad).transfers({ discrepancy: 'OPEN' }), /inválida/);
  }
});

test('the sidebar counter adds active restock alerts and every open shortage, not the listed rows', () => {
  const restock = { activeCount: 2 };
  // 150 open shortages while the listing returned only its first rows.
  const shortages = { total: 150, items: [recent] };
  assert.deepEqual(alertCounts(true, restock, shortages), { restock: 2, shortages: 150, badge: 152 });
  // Before the first reading, nothing is counted.
  assert.deepEqual(alertCounts(true, undefined, undefined), { restock: 0, shortages: 0, badge: 0 });
  // Operators and profiles that must change their password have no alerts entry.
  assert.deepEqual(alertCounts(false, restock, shortages), { restock: 0, shortages: 0, badge: 0 });
});
