import assert from 'node:assert/strict';
import test from 'node:test';
import { explainFormatErrors } from '../src/components/shared/formatHints.ts';

/** A minimal stand-in for a form field: a pattern mismatch decided by a predicate, plus the custom validity message. */
class Field {
  tagName = 'INPUT';
  value: string;
  title: string;
  custom = '';
  matches: (value: string) => boolean;
  constructor(value: string, title: string, matches: (value: string) => boolean) {
    this.value = value; this.title = title; this.matches = matches;
  }
  get validity() { return { patternMismatch: !this.matches(this.value), customError: this.custom !== '' }; }
  setCustomValidity(message: string) { this.custom = message; }
}

/** A document stand-in that records listeners and dispatches events to them synchronously. */
function documentWith(fields: Field[]) {
  const listeners = new Map<string, ((event: { target: unknown; key?: string }) => void)[]>();
  const root = {
    addEventListener: (type: string, listener: (event: { target: unknown; key?: string }) => void) =>
      listeners.set(type, [...(listeners.get(type) ?? []), listener]),
    querySelectorAll: () => fields,
  };
  const dispatch = (type: string, target: unknown, key?: string) => (listeners.get(type) ?? []).forEach(listener => listener({ target, key }));
  return { root: root as unknown as Document, dispatch };
}

const integer = (value: string) => /^\d+$/.test(value);

test('a pattern mismatch shows the field hint instead of the generic browser message', () => {
  const quantity = new Field('1,5', 'Número entero.', integer);
  const { root, dispatch } = documentWith([quantity]);
  explainFormatErrors(root);
  dispatch('invalid', quantity);
  assert.equal(quantity.custom, 'Número entero.');
  // A field without its own hint keeps the browser message.
  const bare = new Field('x', '', integer);
  dispatch('invalid', bare);
  assert.equal(bare.custom, '');
});

test('a hint never blocks a value fixed without typing, such as a prefilled form', () => {
  // Reviewer finding R3-formathints-stale-validity: the value becomes valid through a programmatic change, with no
  // input event; pressing the button or Enter re-checks the hint before the browser validates the form.
  const price = new Field('-5', 'Decimal no negativo.', integer);
  const { root, dispatch } = documentWith([price]);
  explainFormatErrors(root);
  dispatch('invalid', price);
  assert.equal(price.custom, 'Decimal no negativo.');
  price.value = '125000';
  dispatch('pointerdown', {});
  assert.equal(price.custom, '');
  // A value that is still wrong keeps its hint when the form is submitted again with Enter.
  price.value = '12,5';
  dispatch('invalid', price);
  dispatch('keydown', price, 'Enter');
  assert.equal(price.custom, 'Decimal no negativo.');
});
