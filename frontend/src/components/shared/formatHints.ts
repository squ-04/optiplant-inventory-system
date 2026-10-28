/** The part of an input this module uses, so it also runs against test doubles without a browser DOM. */
interface HintedField {
  tagName: string;
  title: string;
  validity: { patternMismatch: boolean };
  setCustomValidity(message: string): void;
}

const isInput = (target: unknown): target is HintedField => typeof target === 'object' && target !== null
  && (target as HintedField).tagName === 'INPUT' && typeof (target as HintedField).setCustomValidity === 'function';
const isSelect = (target: unknown) => typeof target === 'object' && target !== null
  && (target as { tagName?: string }).tagName === 'SELECT';

/**
 * Replaces the browser's generic pattern message («Haz coincidir el formato solicitado.») with the field's own hint, its
 * `title`, so every form explains the expected format the same way. It is the usual `invalid` / `input` pairing of
 * `setCustomValidity`, installed once for the whole document.
 */
export function explainFormatErrors(root: Document) {
  const hinted = new Set<HintedField>();
  const fields = () => [...(root.querySelectorAll('input') as unknown as Iterable<unknown>)].filter(isInput);
  // Sets the hint while the value does not match its pattern and removes it otherwise.
  const check = (field: HintedField) => {
    field.setCustomValidity('');
    if (field.validity.patternMismatch && field.title) { field.setCustomValidity(field.title); hinted.add(field); }
    else hinted.delete(field);
  };
  root.addEventListener('invalid', event => { if (isInput(event.target)) check(event.target); }, true);
  // Typing may fix the value and choosing another option may change the pattern (another unit, for example): the hint is
  // cleared after React has rendered the change. The `change` a text field fires when it loses focus (pressing the
  // submit button) must not clear the hint just shown.
  const clear = () => setTimeout(() => {
    fields().forEach(field => { if (hinted.has(field)) { field.setCustomValidity(''); hinted.delete(field); } });
  });
  root.addEventListener('input', clear, true);
  root.addEventListener('change', event => { if (isSelect(event.target)) clear(); }, true);
  // A value can also become valid without any typing, such as a form prefilled by the view. Hints are re-checked right
  // before the browser validates a submission (pressing a button or Enter), so a stale hint never blocks a valid form.
  const recheck = () => fields().forEach(field => { if (hinted.has(field)) check(field); });
  root.addEventListener('pointerdown', recheck, true);
  root.addEventListener('keydown', event => { if ((event as KeyboardEvent).key === 'Enter') recheck(); }, true);
}
