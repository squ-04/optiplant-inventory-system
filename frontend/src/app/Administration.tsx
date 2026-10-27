import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { ApiClient, sessionProblem } from '../lib/api/client.ts';
import type { Branch, ManagedUser, Role, UserInput } from '../lib/api/client.ts';
import { roleLabel } from '../lib/format.ts';
import { SectionTabs } from '../components/shared/SectionTabs.tsx';
import { PasswordField } from '../components/shared/PasswordField.tsx';
import { revealEditor } from '../components/shared/revealEditor.ts';
import { useConfirmation } from '../components/shared/ConfirmDialog.tsx';
import { Ban, Pencil, Plus, RefreshCw, Save, X } from 'lucide-react';
import { StatusBadge } from '../components/shared/StatusBadge';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Table, TableBody, TableCaption, TableCell, TableHead, TableHeader, TableRow } from '../components/ui/table';

interface Props { client: ApiClient; onError: (reason: unknown) => void; onChanged: () => void }

export function Administration({ client, onError, onChanged }: Props) {
  const [branches, setBranches] = useState<Branch[]>([]);
  const [users, setUsers] = useState<ManagedUser[]>([]);
  const [branch, setBranch] = useState<Branch | null>(null);
  const [account, setAccount] = useState<ManagedUser | null>(null);
  // Editors exist only after an explicit create or edit action, never as an idle form on load.
  const [branchOpen, setBranchOpen] = useState(false);
  const [accountOpen, setAccountOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const { confirm, dialog } = useConfirmation();

  async function reload() {
    const [nextBranches, nextUsers] = await Promise.all([client.branches(), client.users()]);
    setBranches(nextBranches); setUsers(nextUsers); setLoaded(true); onChanged();
  }
  useEffect(() => {
    let active = true;
    setBusy(true);
    Promise.all([client.branches(), client.users()]).then(([b, u]) => {
      if (active) { setBranches(b); setUsers(u); setLoaded(true); }
    }).catch(reason => { if (active) onError(reason); })
      .finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [client]);

  async function run(action: () => Promise<void>) {
    setBusy(true); setMessage(''); setError('');
    try { await action(); }
    catch (reason) {
      setError(reason instanceof TypeError
        ? 'Sin comunicación con el servidor. No se confirmó la operación; las consultas pueden estar desactualizadas. Verifique el listado antes de reintentar.'
        : reason instanceof Error ? reason.message : 'No se pudo completar la solicitud.');
      // Business rejections stay in this view; only session problems are escalated to the shell.
      if (sessionProblem(reason)) onError(reason);
    } finally { setBusy(false); }
  }

  function saveBranch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    void run(async () => {
      const name = String(data.get('name'));
      const result = branch
        ? await client.updateBranch(branch.externalId, { name })
        : await client.createBranch({ code: String(data.get('code')), name });
      setBranch(result); await reload(); setMessage('Sucursal guardada.');
    });
  }

  return <section aria-labelledby="administration-title" aria-busy={busy}>
    <h2 id="administration-title" tabIndex={-1}>Administración de la red</h2>
    <p>Las bajas conservan identidad e historial. No se eliminan registros ni se reactivan automáticamente.</p>
    <p role="status">{busy ? 'Consultando servidor…' : message}</p>
    {error && <p role="alert">{error}</p>}
    <Button variant="outline" disabled={busy} onClick={() => void run(reload)}><RefreshCw aria-hidden="true" />Actualizar listados</Button>
    {!loaded && <p>Los datos todavía no están disponibles.</p>}
    <SectionTabs label="Administración de la red" items={[{ label: 'Sucursales', content: <>
    <div className="list-heading">
      <div><h3>Sucursales</h3><p>Identificación y disponibilidad de las sedes de la red.</p></div>
      <Button disabled={busy} onClick={() => {
        if (branch) setBranch(null);
        setBranchOpen(true); revealEditor('branch-editor');
      }}><Plus aria-hidden="true" />Nueva sucursal</Button>
    </div>
    {loaded && branches.length === 0 && <p>No hay sucursales registradas. Use «Nueva sucursal» para crear la primera.</p>}
    {branches.length > 0 && <div className="table-scroll" role="region" aria-label="Sucursales registradas" tabIndex={0}>
      <Table>
        <TableCaption>Sucursales registradas</TableCaption>
        <TableHeader><TableRow>
          <TableHead scope="col">Código</TableHead><TableHead scope="col">Nombre</TableHead>
          <TableHead scope="col">Estado</TableHead><TableHead scope="col" className="text-right">Acción</TableHead>
        </TableRow></TableHeader>
        <TableBody>{branches.map(item => <TableRow key={item.externalId}>
          <TableCell><span className="record-code">{item.code}</span></TableCell>
          <TableCell className="font-medium">{item.name}</TableCell>
          <TableCell><StatusBadge active={item.active} activeLabel="Activa" inactiveLabel="Inactiva" /></TableCell>
          <TableCell className="text-right"><Button variant="outline" disabled={busy}
            onClick={() => void run(async () => {
              setBranch(await client.branch(item.externalId)); setBranchOpen(true); revealEditor('branch-editor');
            })}>
            <Pencil aria-hidden="true" />Editar <span className="sr-only">{item.code}</span>
          </Button></TableCell>
        </TableRow>)}</TableBody>
      </Table></div>}
    {branchOpen && <form id="branch-editor" key={branch ? JSON.stringify(branch) : 'new-branch'} onSubmit={saveBranch}>
      <fieldset disabled={busy}>
        <legend>{branch ? 'Editar sucursal' : 'Crear sucursal'}</legend>
        {branch && <p>Identificador público: {branch.externalId}</p>}
        <div className="grid gap-4 sm:grid-cols-2">
          <label>Código<Input name="code" required maxLength={64} defaultValue={branch?.code ?? ''} readOnly={!!branch} /></label>
          <label>Nombre<Input name="name" required maxLength={160} defaultValue={branch?.name ?? ''} /></label>
        </div>
        <Button type="submit"><Save aria-hidden="true" />Guardar sucursal</Button>
        {branch?.active && <Button variant="outline" className="text-destructive" type="button" onClick={async () => {
          if (await confirm({ title: `¿Desactivar la sucursal ${branch.code}?`, confirmLabel: 'Desactivar sucursal', destructive: true,
            description: 'Se verificará que no tenga existencias ni documentos pendientes. Las cuentas asignadas no se desactivan.' })) {
            void run(async () => { setBranch(await client.deactivateBranch(branch.externalId)); await reload(); setMessage('Sucursal desactivada. Las cuentas no fueron desactivadas.'); });
          }
        }}><Ban aria-hidden="true" />Desactivar sucursal</Button>}
        <Button variant="ghost" type="button" onClick={() => { setBranchOpen(false); setBranch(null); }}><X aria-hidden="true" />Cerrar formulario</Button>
      </fieldset>
    </form>}
    </> }, { label: 'Usuarios', content: <>
    <div className="list-heading">
      <div><h3>Usuarios</h3><p>Identidad, perfil y sucursal asignada de cada cuenta.</p></div>
      <Button disabled={busy} onClick={() => {
        if (account) setAccount(null);
        setAccountOpen(true); revealEditor('user-editor');
      }}><Plus aria-hidden="true" />Nuevo usuario</Button>
    </div>
    {loaded && users.length === 0 && <p>No hay usuarios registrados. Use «Nuevo usuario» para crear el primero.</p>}
    {users.length > 0 && <div className="table-scroll" role="region" aria-label="Usuarios registrados" tabIndex={0}>
      <Table>
        <TableCaption>Usuarios registrados</TableCaption>
        <TableHeader><TableRow>
          <TableHead scope="col">Usuario</TableHead><TableHead scope="col">Nombre completo</TableHead>
          <TableHead scope="col">Perfil</TableHead><TableHead scope="col">Sucursal asignada</TableHead>
          <TableHead scope="col">Estado</TableHead><TableHead scope="col" className="text-right">Acción</TableHead>
        </TableRow></TableHeader>
        <TableBody>{users.map(item => {
          const assigned = branches.find(value => value.externalId === item.branchExternalId);
          return <TableRow key={item.externalId}>
            <TableCell><span className="record-code">{item.code}</span></TableCell>
            <TableCell className="font-medium">{item.fullName}</TableCell>
            <TableCell>{roleLabel(item.role)}</TableCell>
            <TableCell>{assigned ? <><strong className="block font-medium">{assigned.name}</strong>
              <span className="text-muted-foreground">{assigned.code}{assigned.active ? '' : ' (inactiva)'}</span></>
              : item.branchExternalId ?? (item.role === 'ADMIN' ? 'Ámbito global' : 'Sin sucursal asignada')}</TableCell>
            <TableCell><StatusBadge active={item.active} /></TableCell>
            <TableCell className="text-right"><Button variant="outline" disabled={busy}
              onClick={() => void run(async () => {
                setAccount(await client.user(item.externalId)); setAccountOpen(true); revealEditor('user-editor');
              })}>
              <Pencil aria-hidden="true" />Editar <span className="sr-only">{item.code}</span>
            </Button></TableCell>
          </TableRow>;
        })}</TableBody>
      </Table></div>}
    {accountOpen && <UserEditor key={account ? JSON.stringify(account) : 'new-user'} account={account} branches={branches} busy={busy}
      onSave={(data, password) => run(async () => {
        const result = account
          ? await client.updateUser(account.externalId, data)
          : await client.createUser({ ...data, temporaryPassword: password });
        setAccount(result);
        await client.me(); // A self-change of role or branch must discard the previous UI context.
        await reload(); setMessage('Usuario guardado. Entregue la contraseña temporal por un canal privado, si corresponde.');
      })}
      onDeactivate={async () => {
        if (account && await confirm({ title: `¿Desactivar el usuario ${account.code}?`, confirmLabel: 'Desactivar usuario',
          destructive: true, description: 'Sus accesos previos quedarán revocados. La cuenta y su historial se conservan.' })) {
          void run(async () => {
            await client.deactivateUser(account.externalId); await client.me();
            setAccount(await client.user(account.externalId)); await reload(); setMessage('Usuario desactivado.');
          });
        }
      }}
      onClose={() => { setAccountOpen(false); setAccount(null); }} />}
    </> }]} />
    {dialog}
  </section>;
}

function UserEditor({ account, branches, busy, onSave, onDeactivate, onClose }: {
  account: ManagedUser | null; branches: Branch[]; busy: boolean;
  onSave: (data: UserInput, password: string) => Promise<void>; onDeactivate: () => void; onClose: () => void;
}) {
  const [role, setRole] = useState<Role>(account?.role ?? 'OPERATOR');
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    const password = String(data.get('temporaryPassword') ?? '');
    // Clear the secret immediately; it is never retained in component or browser storage.
    const input = form.elements.namedItem('temporaryPassword');
    if (input instanceof HTMLInputElement) input.value = '';
    void onSave({ code: String(data.get('code')), fullName: String(data.get('fullName')), role,
      branchExternalId: String(data.get('branchExternalId') ?? '') || null }, password);
  }
  return <form id="user-editor" onSubmit={submit}>
    <fieldset disabled={busy}>
      <legend>{account ? 'Editar usuario' : 'Crear usuario'}</legend>
      {account && <p>Identificador público: {account.externalId} · {account.active ? 'Activo' : 'Inactivo'}</p>}
      <label>Nombre completo<Input name="fullName" required maxLength={160} defaultValue={account?.fullName ?? ''} /></label>
      <label>Código de usuario<Input name="code" required maxLength={64} autoComplete="off" defaultValue={account?.code ?? ''} /></label>
      <label htmlFor="managed-user-role">Perfil</label>
      <select id="managed-user-role" name="role" value={role} onChange={e => setRole(e.target.value as Role)}>
        <option value="ADMIN">Administrador</option><option value="MANAGER">Gerente</option><option value="OPERATOR">Operador</option>
      </select>
      <label htmlFor="managed-user-branch">{role === 'ADMIN' ? 'Sucursal activa (opcional; ámbito global)' : 'Sucursal activa'}</label>
      <select id="managed-user-branch"
        name="branchExternalId" required={role !== 'ADMIN'} defaultValue={account?.branchExternalId ?? ''}>
        <option value="">{role === 'ADMIN' ? 'Sin sucursal; ámbito global' : 'Seleccione una sucursal'}</option>
        {branches.filter(b => b.active || b.externalId === account?.branchExternalId).map(b =>
          <option key={b.externalId} value={b.externalId} disabled={!b.active}>{b.code} · {b.name}{!b.active ? ' (inactiva)' : ''}</option>)}
      </select>
      {!account && <>
        <PasswordField label="Contraseña temporal individual" name="temporaryPassword" required
          autoComplete="new-password" minLength={15} maxLength={128} aria-describedby="temporary-password-hint" />
        <p id="temporary-password-hint">De 15 a 128 caracteres y distinta del código de usuario.</p>
      </>}
      <Button type="submit"><Save aria-hidden="true" />Guardar usuario</Button>
      {account?.active && <Button variant="outline" className="text-destructive" type="button" onClick={onDeactivate}><Ban aria-hidden="true" />Desactivar usuario</Button>}
      <Button variant="ghost" type="button" onClick={onClose}><X aria-hidden="true" />Cerrar formulario</Button>
    </fieldset>
  </form>;
}
