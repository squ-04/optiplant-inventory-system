import { PackageCheck } from 'lucide-react';
import type { Transfer, TransferReceptionLine } from '../lib/api/client.ts';
import { blocksPreparation, checkReception, compareDecimal, receptionMessage } from '../lib/transfers/transfers.ts';
import { apiDecimal, decimal, decimalInputPattern, inputDecimal } from '../lib/format.ts';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';

interface Props {
  transfer: Transfer; busy: boolean;
  /** Owned by the transfer detail, so the typed quantities survive going back while the detail stays open. */
  quantities: Record<number, string>; onChange: (quantities: Record<number, string>) => void;
  onReceive: (lines: TransferReceptionLine[], shortages: number) => Promise<boolean>; onBack: () => void;
}

/** The received quantity of every line starts at what was sent: the operator lowers only what did not arrive. */
export const initialReception = (transfer: Transfer): Record<number, string> =>
  Object.fromEntries(transfer.lines.map(line => [line.lineNo, inputDecimal(line.dispatchedQuantity ?? '0')]));

/**
 * RF-0036/RF-0037: the destination operator confirms the single arrival of the dispatch with what was counted in every
 * line. Equal to what was sent in every line is a complete reception; less (even 0) leaves the difference as a shortage
 * with its alert. There is no second reception: surpluses are rejected before confirming.
 */
export function TransferReceptionForm({ transfer, busy, quantities, onChange, onReceive, onBack }: Props) {
  const checks = Object.fromEntries(transfer.lines.map(line => [line.lineNo,
    checkReception(apiDecimal(quantities[line.lineNo] ?? ''), line)]));
  const blocked = Object.values(checks).some(blocksPreparation);
  const short = transfer.lines.filter(line => checks[line.lineNo] === 'ok'
    && compareDecimal(apiDecimal(quantities[line.lineNo] ?? ''), line.dispatchedQuantity ?? '0')! < 0);

  return <form className="purchase-cancel transfer-receive" aria-labelledby="transfer-receive-title" noValidate onSubmit={event => {
    event.preventDefault();
    if (blocked) return;
    void onReceive(transfer.lines.map(line => ({ lineNo: line.lineNo, quantity: apiDecimal(quantities[line.lineNo]) })), short.length);
  }}>
    <fieldset disabled={busy}>
      <legend id="transfer-receive-title">Confirmar la recepción de {transfer.code}</legend>
      <p>Indique lo que llegó de cada producto; use 0 si no llegó. Se confirma una sola vez: lo recibido entra a su inventario
        y lo que falte queda en tránsito con una alerta de faltante. Otra entrega requiere una nueva transferencia.</p>
      {transfer.lines.map(line => {
        const check = checks[line.lineNo];
        const blocking = blocksPreparation(check);
        const message = receptionMessage(check, line);
        const hint = `transfer-receive-hint-${line.lineNo}`;
        const missing = short.includes(line);
        return <div key={line.lineNo} className="transfer-prepare-line">
          <label htmlFor={`transfer-receive-${line.lineNo}`}>{line.sku} {line.productName} ({line.unit})</label>
          <Input id={`transfer-receive-${line.lineNo}`} value={quantities[line.lineNo] ?? ''} required
            inputMode={line.unitDivisible ? 'decimal' : 'numeric'} pattern={decimalInputPattern(line.unitDivisible ? 3 : 0)}
            title={line.unitDivisible ? 'Hasta 3 decimales con coma, por ejemplo 2,5.' : 'Número entero.'}
            aria-describedby={hint} aria-invalid={blocking || undefined}
            onChange={event => onChange({ ...quantities, [line.lineNo]: event.target.value })} />
          <p id={hint} className={blocking ? 'field-error' : missing ? 'transfer-warning' : 'text-muted-foreground'}>
            {message ?? `Enviado: ${decimal(line.dispatchedQuantity ?? '0')} ${line.unit}.`
              + (missing ? ' Lo que falte quedará registrado como faltante.' : '')}
          </p>
        </div>;
      })}
      <p className={short.length > 0 ? 'notice-warning' : 'transfer-receive-summary'} role="status">
        {blocked ? 'Corrija las cantidades marcadas para confirmar.'
          : short.length > 0
            ? `Faltan productos en ${short.length === 1 ? '1 línea' : `${short.length} líneas`}: la transferencia quedará «Recibida con discrepancia» y se abrirá una alerta para los gerentes.`
            : 'Llegó todo lo enviado: la transferencia quedará «Recibida completa».'}
      </p>
      <Button type="submit" disabled={blocked}><PackageCheck aria-hidden="true" />{busy ? 'Confirmando…' : 'Confirmar recepción'}</Button>
      <Button type="button" variant="ghost" onClick={onBack}>Volver sin confirmar</Button>
    </fieldset>
  </form>;
}
