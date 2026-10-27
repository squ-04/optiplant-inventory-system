import { useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter,
  AlertDialogHeader, AlertDialogTitle,
} from '../ui/alert-dialog';
import { confirmationSlot } from '../../lib/confirmation.ts';

interface Confirmation { title: string; description: ReactNode; confirmLabel: string; destructive?: boolean }

/**
 * Asks for an explicit confirmation before a consequential action. `confirm` resolves to `true` only when the action
 * button is chosen; Escape, the cancel button, closing the dialog, a newer request or unmounting the owning view
 * resolve to `false`. Render `dialog` once in the component that calls `confirm`. Focus returns to the control that
 * asked for the confirmation.
 */
export function useConfirmation() {
  const [request, setRequest] = useState<Confirmation | null>(null);
  const [slot] = useState(confirmationSlot);
  const origin = useRef<HTMLElement | null>(null);
  useEffect(() => () => slot.cancel(), [slot]);
  const confirm = (confirmation: Confirmation) => {
    origin.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    setRequest(confirmation);
    return slot.open();
  };
  const close = (accepted: boolean) => { slot.settle(accepted); setRequest(null); };
  const dialog = <AlertDialog open={request !== null} onOpenChange={open => { if (!open) close(false); }}>
    {request && <AlertDialogContent onCloseAutoFocus={event => {
      // There is no Radix trigger: return focus explicitly, unless the action removed that control.
      if (origin.current?.isConnected) { event.preventDefault(); origin.current.focus(); }
    }}>
      <AlertDialogHeader>
        <AlertDialogTitle>{request.title}</AlertDialogTitle>
        <AlertDialogDescription>{request.description}</AlertDialogDescription>
      </AlertDialogHeader>
      <AlertDialogFooter>
        <AlertDialogCancel>Cancelar</AlertDialogCancel>
        <AlertDialogAction variant={request.destructive ? 'destructive' : 'default'} onClick={() => close(true)}>
          {request.confirmLabel}
        </AlertDialogAction>
      </AlertDialogFooter>
    </AlertDialogContent>}
  </AlertDialog>;
  return { confirm, dialog };
}
