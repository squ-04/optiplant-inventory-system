import { useEffect, useId, useRef, useState } from 'react';
import type { ComponentProps } from 'react';
import { Eye, EyeOff } from 'lucide-react';
import { Button } from '../ui/button';
import { Input } from '../ui/input';

/**
 * Password input with a visibility toggle. The toggle sits outside the label so the field keeps its exact
 * accessible name, and visibility returns to hidden whenever the owning form is reset.
 */
export function PasswordField({ label, ...props }: { label: string } & Omit<ComponentProps<'input'>, 'type' | 'id'>) {
  const id = useId();
  const input = useRef<HTMLInputElement>(null);
  const [visible, setVisible] = useState(false);
  useEffect(() => {
    const form = input.current?.form;
    if (!form) return;
    const hide = () => setVisible(false);
    form.addEventListener('reset', hide);
    form.addEventListener('submit', hide);
    return () => { form.removeEventListener('reset', hide); form.removeEventListener('submit', hide); };
  }, []);
  return <>
    <label htmlFor={id}>{label}</label>
    <div className="password-field">
      <Input ref={input} id={id} type={visible ? 'text' : 'password'} {...props} />
      <Button type="button" variant="ghost" size="icon" aria-controls={id} aria-pressed={visible}
        aria-label={visible ? `Ocultar ${label.toLowerCase()}` : `Mostrar ${label.toLowerCase()}`}
        onClick={() => setVisible(value => !value)}>
        {visible ? <EyeOff aria-hidden="true" /> : <Eye aria-hidden="true" />}
      </Button>
    </div>
  </>;
}
