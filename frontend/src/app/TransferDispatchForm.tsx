import { useState } from 'react';
import { Truck } from 'lucide-react';
import type { Route, Transfer, TransferDispatchInput } from '../lib/api/client.ts';
import { BUSINESS_ZONE, MAX_CARRIER, bogotaInstant, bogotaLocal, dispatchProblem } from '../lib/logistics/routes.ts';
import type { DispatchDraft } from '../lib/logistics/routes.ts';
import { decimal } from '../lib/format.ts';
import { compareDecimal } from '../lib/transfers/transfers.ts';
import { RoutePriorityBadge } from '../components/shared/RoutePriorityBadge.tsx';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';

interface Props {
  transfer: Transfer; route: Route | null; routesLoaded: boolean; busy: boolean;
  onDispatch: (input: TransferDispatchInput) => Promise<boolean>; onBack: () => void;
}

/**
 * RF-0035: the origin operator registers the carrier, the actual departure and the estimated arrival (Bogotá time).
 * The prepared quantities leave the origin into transit at once; the server revalidates stock and catalog for every line.
 */
export function TransferDispatchForm({ transfer, route, routesLoaded, busy, onDispatch, onBack }: Props) {
  const [draft, setDraft] = useState<DispatchDraft>(() => ({ carrier: '', departedAt: bogotaLocal(new Date()), estimatedArrivalAt: '' }));
  const [touched, setTouched] = useState(false);
  const problem = dispatchProblem(draft, new Date(), transfer.approval?.occurredAt ?? null);
  const sent = transfer.lines.filter(line => line.preparedBaseQuantity !== null && compareDecimal(line.preparedBaseQuantity, '0') !== 0);

  return <form className="purchase-cancel transfer-dispatch" aria-labelledby="transfer-dispatch-title" noValidate onSubmit={event => {
    event.preventDefault();
    setTouched(true);
    if (problem || !route) return;
    void onDispatch({ carrier: draft.carrier.trim(), departedAt: bogotaInstant(draft.departedAt)!,
      estimatedArrivalAt: bogotaInstant(draft.estimatedArrivalAt)! });
  }}>
    <fieldset disabled={busy}>
      <legend id="transfer-dispatch-title">Registrar el despacho de {transfer.code}</legend>
      <p>Saldrán del origen las cantidades preparadas y quedarán en tránsito hasta la recepción del destino. Se confirma todo
        o nada: si un producto no tiene existencias suficientes, no se despacha ninguno.</p>
      <ul className="transfer-dispatch-lines" aria-label="Cantidades que salen">
        {sent.map(line => <li key={line.lineNo}>{decimal(line.preparedQuantity!)} {line.unit}: {line.sku} {line.productName}</li>)}
      </ul>
      {route
        ? <p className="transfer-dispatch-route">Ruta <span className="record-code">{route.code}</span> <RoutePriorityBadge priority={route.priority} /></p>
        : routesLoaded && <p className="notice-warning" role="alert">No hay una ruta activa de {transfer.origin.name} a {transfer.destination.name}.
          El administrador debe registrarla o reactivarla antes del despacho.</p>}
      <div className="grid gap-4 sm:grid-cols-3">
        <label>Transportista<Input value={draft.carrier} maxLength={MAX_CARRIER} required autoComplete="off"
          placeholder="Nombre o identificador" onChange={event => setDraft({ ...draft, carrier: event.target.value })} /></label>
        <label>Salida real<Input type="datetime-local" value={draft.departedAt} required
          onChange={event => setDraft({ ...draft, departedAt: event.target.value })} /></label>
        <label>Llegada estimada<Input type="datetime-local" value={draft.estimatedArrivalAt} required min={draft.departedAt}
          onChange={event => setDraft({ ...draft, estimatedArrivalAt: event.target.value })} /></label>
      </div>
      <p id="transfer-dispatch-hint" className={touched && problem ? 'field-error' : 'text-muted-foreground'}
        role={touched && problem ? 'alert' : undefined}>
        {touched && problem ? problem : `Fechas y horas de ${BUSINESS_ZONE} (UTC−5). La salida no puede ser futura.`}
      </p>
      <Button type="submit" disabled={!route}><Truck aria-hidden="true" />{busy ? 'Despachando…' : 'Confirmar despacho'}</Button>
      <Button type="button" variant="ghost" onClick={onBack}>Volver sin despachar</Button>
    </fieldset>
  </form>;
}
