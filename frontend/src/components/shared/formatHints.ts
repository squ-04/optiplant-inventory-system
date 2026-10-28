/**
 * Replaces the browser's generic pattern message («Haz coincidir el formato solicitado.») with the field's own hint, its
 * `title`, so every form explains the expected format the same way. It is the usual `invalid` / `input` pairing of
 * `setCustomValidity`, installed once for the whole document.
 */
export function explainFormatErrors(root: Document) {
  const hinted = new WeakSet<HTMLInputElement>();
  root.addEventListener('invalid', event => {
    const field = event.target;
    if (!(field instanceof HTMLInputElement) || !field.title) return;
    field.setCustomValidity('');
    if (field.validity.patternMismatch) { field.setCustomValidity(field.title); hinted.add(field); }
  }, true);
  // Typing may fix the value and choosing another option may change the pattern (another unit, for example): the hint is
  // cleared after React has rendered the change and the browser checks the field again on the next submission. The
  // `change` a text field fires when it loses focus (pressing the submit button) must not clear the hint just shown.
  const clear = () => setTimeout(() => {
    root.querySelectorAll('input').forEach(field => {
      if (hinted.has(field)) { field.setCustomValidity(''); hinted.delete(field); }
    });
  });
  root.addEventListener('input', clear, true);
  root.addEventListener('change', event => { if (event.target instanceof HTMLSelectElement) clear(); }, true);
}
