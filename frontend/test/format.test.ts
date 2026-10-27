import assert from 'node:assert/strict';
import test from 'node:test';

process.env.TZ = 'America/Bogota';
const {
  apiDecimal, clockTime, dateTime, decimal, decimalInputPattern, directionLabel, documentStatusLabel, inputDecimal, roleLabel,
} = await import('../src/lib/format.ts');

test('dates and times use one es-CO format in every view', () => {
  assert.match(dateTime('2026-10-27T15:09:45.221196Z'), /^27\/10\/26, 10:09:45\sa\.\sm\.$/u);
  assert.match(clockTime('2026-10-27T15:09:45Z'), /^10:09:45\sa\.\sm\.$/u);
});

test('roles, directions and document statuses are shown in Spanish', () => {
  assert.deepEqual(['ADMIN', 'MANAGER', 'OPERATOR'].map(roleLabel), ['Administrador', 'Gerente', 'Operador']);
  assert.deepEqual(['IN', 'OUT'].map(directionLabel), ['Entrada', 'Salida']);
  assert.equal(documentStatusLabel('CONFIRMED'), 'Confirmada');
  assert.equal(documentStatusLabel('IN_TRANSIT'), 'En tránsito');
  // Unknown codes are shown as received instead of hidden.
  assert.equal(documentStatusLabel('NEW_STATUS'), 'NEW_STATUS');
  assert.equal(roleLabel('AUDITOR'), 'AUDITOR');
});

test('decimals use a comma and thousands a dot, keeping the scale sent by the server', () => {
  assert.equal(decimal('8.000'), '8,000');
  assert.equal(decimal('1250.000'), '1.250,000');
  assert.equal(decimal('48000.000000'), '48.000,000000');
  assert.equal(decimal('-240000.00'), '−240.000,00');
  assert.equal(decimal('1234567'), '1.234.567');
  assert.equal(decimal('0.000'), '0,000');
  assert.equal(decimal('999.5'), '999,5');
});

test('decimal inputs are typed with a comma and sent to the API with a dot', () => {
  const pattern = new RegExp(`^(?:${decimalInputPattern(3)})$`);
  assert.ok(pattern.test('2,5') && pattern.test('4') && pattern.test('0,125'));
  // A dot would read as a thousands separator, so it is rejected instead of guessed.
  assert.ok(!pattern.test('2.5') && !pattern.test('1.000') && !pattern.test('2,5000') && !pattern.test('-1'));
  assert.ok(!new RegExp(`^(?:${decimalInputPattern(0)})$`).test('2,5'));
  assert.equal(apiDecimal('2,5'), '2.5');
  assert.equal(apiDecimal('4'), '4');
  // Values from the server prefill inputs without insignificant zeros.
  assert.equal(inputDecimal('12.000'), '12');
  assert.equal(inputDecimal('2.500'), '2,5');
  assert.equal(inputDecimal('0.000'), '0');
});
