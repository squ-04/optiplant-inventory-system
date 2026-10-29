import { Clock } from 'lucide-react';
import type { Transfer } from '../lib/api/client.ts';
import { ZONE_NOTE, arrivalNote, bogotaDateTime, deliveryOutcome, deviation, duration } from '../lib/logistics/compliance.ts';

/**
 * RF-0040: the estimated and the real delivery of one transfer side by side. The real time ends at the single confirmed
 * arrival; the resolution of a shortage alert is not an arrival and never appears here.
 */
export function TransferTiming({ transfer }: { transfer: Transfer }) {
  const { dispatch, arrival, timing } = transfer;
  if (!dispatch || !timing) return null;
  const note = arrivalNote(timing, transfer);
  const needsAttention = timing.overdue || (timing.onTime === false) || !timing.consistent;
  return <section className="transfer-timing" aria-labelledby={`timing-${transfer.externalId}`}>
    <h4 id={`timing-${transfer.externalId}`}><Clock aria-hidden="true" />Tiempo de entrega</h4>
    <p>Salida real: {bogotaDateTime(dispatch.departedAt)}</p>
    {/* Side by side when there is room, stacked on a phone: each column answers the same two questions. */}
    <div className="transfer-timing-compare">
      <div><h5>Estimado al despachar</h5>
        <dl><div><dt>Llegada</dt><dd>{bogotaDateTime(dispatch.estimatedArrivalAt)}</dd></div>
          <div><dt>Duración</dt><dd>{duration(timing.estimatedSeconds)}</dd></div></dl></div>
      <div><h5>Real</h5>
        <dl><div><dt>Llegada</dt><dd>{arrival ? bogotaDateTime(arrival.occurredAt) : 'Sin llegada confirmada'}</dd></div>
          <div><dt>Duración</dt><dd>{timing.actualSeconds === null ? 'Sin calcular hasta la llegada'
            : !timing.consistent ? 'No calculable: llegada anterior a la salida'
            : `${duration(timing.actualSeconds)}${note}`}</dd></div></dl></div>
    </div>
    <p className={needsAttention ? 'notice-warning' : 'text-muted-foreground'}>{deliveryOutcome(timing, note === ' sin mercancía recibida')}
      {deviation(timing) && <>. {deviation(timing)}</>}.</p>
    <p className="cell-note">{ZONE_NOTE}. La resolución de una alerta de faltante no cambia estos tiempos.</p>
  </section>;
}
