import assert from 'node:assert/strict';
import test from 'node:test';
import { confirmationSlot } from '../src/lib/confirmation.ts';

test('a confirmation settles once and only the action accepts it', async () => {
  const slot = confirmationSlot();
  const accepted = slot.open();
  slot.settle(true);
  // Radix closes the dialog after the action: the late "false" must not change the answer.
  slot.settle(false);
  assert.equal(await accepted, true);
  const dismissed = slot.open();
  slot.settle(false);
  assert.equal(await dismissed, false);
});

test('a new request or an unmount cancels the pending confirmation instead of leaving it waiting', async () => {
  const slot = confirmationSlot();
  const first = slot.open();
  const second = slot.open();
  assert.equal(await first, false);
  slot.cancel();
  assert.equal(await second, false);
  // Nothing is pending after a cancellation.
  slot.settle(true);
});
