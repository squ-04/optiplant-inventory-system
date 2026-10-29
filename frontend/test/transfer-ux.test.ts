import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError } from '../src/lib/api/client.ts';
import { sentence } from '../src/lib/format.ts';
import { bogotaLocal, freshDeparture } from '../src/lib/logistics/routes.ts';
import { arrivalNote, deliveryOutcome } from '../src/lib/logistics/compliance.ts';
import { progressSteps, shortageGuidance } from '../src/lib/transfers/transfers.ts';

const draft = { carrier: 'Transportes E2E', departedAt: '2026-10-29T14:43', estimatedArrivalAt: '2026-10-29T16:52' };
const now = new Date('2026-10-29T19:52:30Z');

test('S1: the dispatch form proposes the current departure unless the operator edited it', () => {
  assert.deepEqual(freshDeparture(draft, false, now), { ...draft, departedAt: bogotaLocal(now) });
  assert.equal(freshDeparture(draft, false, now).departedAt, '2026-10-29T14:52');
  assert.deepEqual(freshDeparture(draft, true, now), draft);
});

const shortage = (inTransit: string) => ({ lines: [{ missingQuantity: '0.500', inTransitBaseQuantity: inTransit }] });

test('S2: the shortage guidance never says that a resend releases the transit', () => {
  const pending = shortageGuidance(shortage('0.500') as never);
  assert.match(pending, /registre su pérdida/);
  assert.match(pending, /un reenvío es una transferencia nueva y no lo libera/);
  assert.doesNotMatch(pending, /o se reciba un reenvío/);
  const settled = shortageGuidance(shortage('0.000') as never);
  assert.match(settled, /Ya no queda nada en tránsito/);
  assert.doesNotMatch(settled, /sigue en tránsito/);
});

const events = (...statuses: string[]) => statuses.map(toStatus => ({ toStatus }));

test('S6: a closed transfer shows the steps it never reached as not carried out', () => {
  const rejected = progressSteps({ status: 'REJECTED', events: events('REQUESTED', 'PREPARING', 'REJECTED') } as never);
  assert.deepEqual(rejected.map(step => step.state), ['done', 'done', 'skipped', 'skipped', 'skipped']);
  const cancelled = progressSteps({ status: 'CANCELLED', events: events('REQUESTED', 'CANCELLED') } as never);
  assert.deepEqual(cancelled.map(step => step.state), ['done', 'skipped', 'skipped', 'skipped', 'skipped']);
  const transit = progressSteps({ status: 'IN_TRANSIT', events: null } as never);
  assert.deepEqual(transit.map(step => step.state), ['done', 'done', 'done', 'current', 'pending']);
  const discrepancy = progressSteps({ status: 'RECEIVED_DISCREPANCY', events: null } as never);
  assert.deepEqual(discrepancy.map(step => step.state), ['done', 'done', 'done', 'done', 'current']);
});

test('S8: a sentence ending in an abbreviation gets no second period', () => {
  assert.equal(sentence('el 29/10/2026, 2:45:15 p. m.'), 'el 29/10/2026, 2:45:15 p. m.');
  assert.equal(sentence('Lectura del servidor: 29/10/2026'), 'Lectura del servidor: 29/10/2026.');
});

test('S9: a server error without detail explains what to do', () => {
  assert.equal(new ApiError(500, {}).message,
    'El servidor no pudo completar la solicitud (HTTP 500). Vuelva a intentarlo; si persiste, avise al administrador.');
  assert.equal(new ApiError(500, { title: 'Internal Server Error' }).message,
    'El servidor no pudo completar la solicitud (HTTP 500). Vuelva a intentarlo; si persiste, avise al administrador.');
  assert.equal(new ApiError(409, { detail: 'Ya existe.' }).message, 'Ya existe.');
  assert.equal(new ApiError(404, { title: 'Not Found' }).message, 'Not Found');
});

test('S5: a reception with nothing received is not described as a partial arrival', () => {
  const timing = { delivery: 'DISCREPANCY' } as never;
  assert.equal(arrivalNote(timing, { lines: [{ receivedQuantity: '0.000' }] } as never), ' sin mercancía recibida');
  assert.equal(arrivalNote(timing, { lines: [{ receivedQuantity: '2.000' }, { receivedQuantity: '0.000' }] } as never),
    ' hasta la llegada parcial');
  assert.equal(arrivalNote({ delivery: 'COMPLETE' } as never, { lines: [{ receivedQuantity: '1.000' }] } as never), '');
  const zero = { delivery: 'DISCREPANCY', consistent: true } as never;
  assert.equal(deliveryOutcome(zero, true), 'Recibida con discrepancia: no llegó mercancía, no cumple');
  assert.equal(deliveryOutcome(zero), 'Recibida con discrepancia: entrega parcial, no cumple');
});
