import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Pencil, Plus, Save, X } from 'lucide-react';
import { ApiClient, ApiError } from '../lib/api/client.ts';
import type { Supplier } from '../lib/api/client.ts';
import { revealEditor } from '../components/shared/revealEditor.ts';
import { StatusBadge } from '../components/shared/StatusBadge.tsx';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Table, TableBody, TableCaption, TableCell, TableHead, TableHeader, TableRow } from '../components/ui/table';

interface Props { client: ApiClient; onError: (reason: unknown) => void; onChanged: () => void; revision: number }

/** RF-0021-CA-02: the administrator keeps the minimum supplier reference (code and name, no fiscal data). */
export function Suppliers({ client, onError, onChanged, revision }: Props) {
  const [suppliers, setSuppliers] = useState<Supplier[] | null>(null);
  const [loadError, setLoadError] = useState('');
  const [editing, setEditing] = useState<Supplier | 'new' | null>(null);
  const [draft, setDraft] = useState({ code: '', name: '' });
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState('');
  const [confirmed, setConfirmed] = useState('');

  async function load() {
    setLoadError('');
    try { setSuppliers(await client.suppliers()); } catch (reason) {
      if (reason instanceof ApiError && reason.status === 401) onError(reason);
      else setLoadError(reason instanceof Error ? reason.message : 'No se pudo consultar los proveedores.');
    }
  }
  useEffect(() => { void load(); }, [client, revision]);

  function open(target: Supplier | 'new') {
    setEditing(target); setFormError(''); setConfirmed('');
    setDraft(target === 'new' ? { code: '', name: '' } : { code: target.code, name: target.name });
    revealEditor('supplier-editor');
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!editing) return;
    const data = { code: draft.code.trim(), name: draft.name.trim() };
    setBusy(true); setFormError('');
    (editing === 'new' ? client.createSupplier(data) : client.updateSupplier(editing.externalId, data)).then(saved => {
      setConfirmed(editing === 'new' ? `Proveedor ${saved.code} creado.` : `Proveedor ${saved.code} actualizado.`);
      setEditing(null);
      onChanged();
      void load();
    }).catch(reason => {
      if (reason instanceof ApiError && reason.status === 401) { onError(reason); return; }
      setFormError(reason instanceof TypeError ? 'Sin comunicación con el servidor. No se confirmó el cambio.'
        : reason instanceof Error ? reason.message : 'No se pudo guardar el proveedor.');
    }).finally(() => setBusy(false));
  }

  return <section aria-labelledby="suppliers-title">
    <h3 id="suppliers-title">Proveedores</h3>
    <p>Referencia reutilizable para las órdenes de compra: código único y nombre. Las órdenes conservan el nombre que
      tenía el proveedor al pasar a pendiente de recepción, aunque luego lo modifique aquí.</p>
    {!editing && <Button onClick={() => open('new')}><Plus aria-hidden="true" />Nuevo proveedor</Button>}
    {confirmed && <p role="status">{confirmed}</p>}
    {editing && <form id="supplier-editor" onSubmit={submit} aria-describedby={formError ? 'supplier-error' : undefined}>
      <fieldset disabled={busy}>
        <legend>{editing === 'new' ? 'Nuevo proveedor' : `Editar proveedor ${editing.code}`}</legend>
        <label>Código<Input value={draft.code} onChange={event => setDraft({ ...draft, code: event.target.value })}
          required maxLength={64} autoComplete="off" /></label>
        <label>Nombre<Input value={draft.name} onChange={event => setDraft({ ...draft, name: event.target.value })}
          required maxLength={160} autoComplete="off" /></label>
        {formError && <p role="alert" id="supplier-error">{formError}</p>}
        <Button type="submit"><Save aria-hidden="true" />{busy ? 'Guardando…' : editing === 'new' ? 'Crear proveedor' : 'Guardar cambios'}</Button>
        <Button variant="ghost" type="button" onClick={() => { setEditing(null); setFormError(''); }}>
          <X aria-hidden="true" />Cerrar formulario
        </Button>
      </fieldset>
    </form>}
    {loadError && <p role="alert">{loadError}</p>}
    {!suppliers && !loadError && <p role="status">Consultando proveedores…</p>}
    {suppliers && (suppliers.length === 0
      ? <p>Todavía no hay proveedores. Cree el primero para que los operadores puedan registrar órdenes de compra.</p>
      : <div className="table-scroll" role="region" aria-label="Proveedores" tabIndex={0}>
        <Table>
          <TableCaption>Proveedores ordenados por código.</TableCaption>
          <TableHeader><TableRow>
            <TableHead scope="col">Código</TableHead><TableHead scope="col">Nombre</TableHead>
            <TableHead scope="col">Estado</TableHead><TableHead scope="col"><span className="sr-only">Acciones</span></TableHead>
          </TableRow></TableHeader>
          <TableBody>{suppliers.map(item => <TableRow key={item.externalId}>
            <TableCell><span className="record-code">{item.code}</span></TableCell>
            <TableCell>{item.name}</TableCell>
            <TableCell><StatusBadge active={item.active} /></TableCell>
            <TableCell><Button variant="outline" size="sm" onClick={() => open(item)}>
              <Pencil aria-hidden="true" />Editar<span className="sr-only"> {item.code}</span>
            </Button></TableCell>
          </TableRow>)}</TableBody>
        </Table>
      </div>)}
  </section>;
}
