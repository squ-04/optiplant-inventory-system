import { useState } from 'react';
import { CheckCircle2, FileWarning, Send } from 'lucide-react';
import { SHORTAGE_TREATMENTS } from '../lib/api/client.ts';
import type { ShortageTreatment, TransitLossInput, Transfer, User } from '../lib/api/client.ts';
import {
  TREATMENT_HINT, TREATMENT_LABEL, canResend, canResolve, canWriteOff, missingLines, shortageGuidance, transferStatusLabel,
} from '../lib/transfers/transfers.ts';
import { dateTime as when, decimal } from '../lib/format.ts';
import { StatusBadge } from '../components/shared/StatusBadge.tsx';
import { Button } from '../components/ui/button';
import { Textarea } from '../components/ui/textarea';

// The server's limits (TransitLossRequest.MAX_REASON and MAX_EVIDENCE).
const MAX_REASON = 500;
const MAX_EVIDENCE = 1000;

/** Open or resolved; the label always names the state. */
export function ShortageBadge({ transfer }: { transfer: Transfer }) {
  return <StatusBadge active={transfer.discrepancy?.status === 'RESOLVED'} activeLabel="Alerta resuelta" inactiveLabel="Alerta abierta" />;
}

/** The missing quantities in the requested unit, with what is still pending in transit. */
export function ShortageLines({ transfer }: { transfer: Transfer }) {
  return <ul className="transfer-shortage-lines" aria-label={`Faltantes de ${transfer.code}`}>
    {missingLines(transfer).map(line => <li key={line.lineNo}>
      <strong>Faltan {decimal(line.missingQuantity!)} {line.unit}</strong> de {line.sku} {line.productName}
      <span className="cell-note">Enviado {decimal(line.dispatchedQuantity!)} {line.unit}, recibido {decimal(line.receivedQuantity!)} {line.unit}.
        {' '}En tránsito: {decimal(line.inTransitBaseQuantity ?? '0')} {line.baseUnitName}.</span>
    </li>)}
  </ul>;
}

/**
 * RF-0038-CA-02: «Resuelto» needs one treatment. It only documents the decision: quantities, value, transit and the
 * transfer state stay as they were.
 */
export function ShortageResolveForm({ transfer, busy, onResolve, idPrefix }: {
  transfer: Transfer; busy: boolean; onResolve: (treatment: ShortageTreatment) => Promise<boolean>; idPrefix: string;
}) {
  const [treatment, setTreatment] = useState<ShortageTreatment | ''>('');
  const [touched, setTouched] = useState(false);
  const missing = touched && treatment === '';
  const hint = `${idPrefix}-treatment-hint`;
  return <form className="transfer-resolve" aria-label={`Resolver la alerta de ${transfer.code}`} noValidate onSubmit={event => {
    event.preventDefault();
    setTouched(true);
    if (treatment !== '') void onResolve(treatment);
  }}>
    <fieldset disabled={busy} aria-describedby={hint} aria-invalid={missing || undefined}>
      <legend>Tratamiento del faltante</legend>
      {SHORTAGE_TREATMENTS.map(option => <label key={option} className="transfer-treatment">
        <input type="radio" name={`${idPrefix}-treatment`} value={option} checked={treatment === option}
          onChange={() => setTreatment(option)} />
        <span><strong>{TREATMENT_LABEL[option]}</strong><span className="cell-note">{TREATMENT_HINT[option]}</span></span>
      </label>)}
      <p id={hint} className={missing ? 'field-error' : 'text-muted-foreground'} role={missing ? 'alert' : undefined}>
        {missing ? 'Seleccione el tratamiento para marcar la alerta como resuelta.'
          : 'Marcar «Resuelto» no mueve inventario ni cambia el estado de la transferencia.'}
      </p>
      <Button type="submit"><CheckCircle2 aria-hidden="true" />{busy ? 'Guardando…' : 'Resuelto'}</Button>
    </fieldset>
  </form>;
}

/**
 * RF-0038/RF-0039: the shortage of a reception with discrepancy, its alert and its treatment. The loss of the pending
 * transit and the resend are registered apart from the alert, by their own actors.
 */
export function TransferShortagePanel({ transfer, user, busy, onResolve, onWriteOff, onResend }: {
  transfer: Transfer; user: User; busy: boolean;
  onResolve: (treatment: ShortageTreatment) => Promise<boolean>;
  onWriteOff: (input: TransitLossInput) => Promise<boolean>; onResend: () => void;
}) {
  const [lossOpen, setLossOpen] = useState(false);
  const [loss, setLoss] = useState<TransitLossInput>({ reason: '', evidence: '' });
  const [lossTouched, setLossTouched] = useState(false);
  const shortage = transfer.discrepancy!;
  const lossProblem = !loss.reason.trim() ? 'Indique el motivo de la pérdida.'
    : !loss.evidence.trim() ? 'Indique la evidencia de la pérdida.' : '';

  return <section className="transfer-shortage" aria-labelledby="transfer-shortage-title">
    <header>
      <h4 id="transfer-shortage-title"><FileWarning aria-hidden="true" />Faltante de la recepción</h4>
      <ShortageBadge transfer={transfer} />
    </header>
    <ShortageLines transfer={transfer} />
    <dl className="purchase-facts">
      <div><dt>Abierta el</dt><dd>{when(shortage.openedAt)}</dd></div>
      {shortage.status === 'RESOLVED' && <>
        <div><dt>Tratamiento</dt><dd>{TREATMENT_LABEL[shortage.treatment!]}</dd></div>
        <div><dt>Resuelta por</dt><dd>{shortage.resolvedBy!.fullName} ({shortage.resolvedBy!.code}), {when(shortage.resolvedAt!)}</dd></div>
      </>}
      {shortage.transitLoss && <div><dt>Pérdida en tránsito</dt><dd>Autorizada por {shortage.transitLoss.authorizedBy.fullName}{' '}
        ({shortage.transitLoss.authorizedBy.code}), {when(shortage.transitLoss.occurredAt)}
        <span className="cell-note">Motivo: {shortage.transitLoss.reason}</span>
        <span className="cell-note">Evidencia: {shortage.transitLoss.evidence}</span></dd></div>}
      {shortage.resends && shortage.resends.length > 0 && <div><dt>Reenvíos</dt><dd>
        {shortage.resends.map(resend => <span key={resend.externalId} className="block">
          <span className="record-code">{resend.code}</span> {transferStatusLabel(resend.status)}</span>)}</dd></div>}
    </dl>
    <p className="text-muted-foreground">{shortageGuidance(transfer)}</p>

    {canResolve(user, transfer) && <ShortageResolveForm transfer={transfer} busy={busy} onResolve={onResolve} idPrefix="detail" />}

    {canWriteOff(user, transfer) && (lossOpen ? <form className="purchase-cancel" aria-labelledby="transfer-loss-title" noValidate
      onSubmit={event => {
        event.preventDefault();
        setLossTouched(true);
        if (lossProblem) return;
        void onWriteOff({ reason: loss.reason.trim(), evidence: loss.evidence.trim() })
          .then(done => { if (done) { setLossOpen(false); setLossTouched(false); } });
      }}>
      <fieldset disabled={busy}>
        <legend id="transfer-loss-title">Registrar la pérdida en tránsito</legend>
        <p>Se dará de baja, una sola vez, todo lo que sigue en tránsito de este faltante, con su valor congelado del despacho.
          No cambia el inventario físico del origen ni del destino.</p>
        <label htmlFor="transfer-loss-reason">Motivo</label>
        <Textarea id="transfer-loss-reason" value={loss.reason} maxLength={MAX_REASON} rows={2} required
          onChange={event => setLoss({ ...loss, reason: event.target.value })} />
        <label htmlFor="transfer-loss-evidence">Evidencia</label>
        <Textarea id="transfer-loss-evidence" value={loss.evidence} maxLength={MAX_EVIDENCE} rows={2} required
          placeholder="Acta, guía o reporte del transportista" aria-describedby="transfer-loss-hint"
          onChange={event => setLoss({ ...loss, evidence: event.target.value })} />
        <p id="transfer-loss-hint" className={lossTouched && lossProblem ? 'field-error' : 'text-muted-foreground'}
          role={lossTouched && lossProblem ? 'alert' : undefined}>
          {lossTouched && lossProblem ? lossProblem : 'Motivo y evidencia son obligatorios y quedan con su autorización.'}
        </p>
        <Button type="submit" variant="destructive">{busy ? 'Registrando…' : 'Registrar pérdida'}</Button>
        <Button type="button" variant="ghost" onClick={() => setLossOpen(false)}>Volver sin registrar</Button>
      </fieldset>
    </form> : <div className="purchase-actions">
      <Button variant="outline" disabled={busy} onClick={() => setLossOpen(true)}>Registrar pérdida en tránsito</Button>
    </div>)}

    {canResend(user, transfer) && <div className="purchase-actions">
      <Button variant="outline" disabled={busy} onClick={onResend}><Send aria-hidden="true" />Solicitar reenvío del faltante</Button>
    </div>}
  </section>;
}
