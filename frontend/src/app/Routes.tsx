import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { ArrowRight, Pencil, Plus, RefreshCw, Save, X } from 'lucide-react';
import { ApiClient, ApiError } from '../lib/api/client.ts';
import type { Branch, Route, RoutePriority, User } from '../lib/api/client.ts';
import { ROUTE_PRIORITIES, ROUTE_PRIORITY } from '../lib/logistics/routes.ts';
import { revealEditor } from '../components/shared/revealEditor.ts';
import { RoutePriorityBadge } from '../components/shared/RoutePriorityBadge.tsx';
import { StatusBadge } from '../components/shared/StatusBadge.tsx';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Table, TableBody, TableCaption, TableCell, TableHead, TableHeader, TableRow } from '../components/ui/table';

interface Props { client: ApiClient; user: User; onError: (reason: unknown) => void; revision: number; visible: boolean }

interface Draft { code: string; origin: string; destination: string; priority: RoutePriority | ''; active: boolean }
const emptyDraft = (): Draft => ({ code: '', origin: '', destination: '', priority: '', active: true });

const failure = (reason: unknown, fallback: string) => reason instanceof TypeError
  ? 'Sin comunicación con el servidor. No se confirmó el cambio.'
  : reason instanceof Error ? reason.message : fallback;

/**
 * RF-0041 (INC-017/D01–D02, INC-003/D06): directed routes classified by manual priority. The administrator registers
 * and reclassifies them; managers and operators consult the active routes of their branch, which the dispatch uses.
 */
export function Routes({ client, user, onError, revision, visible }: Props) {
  const admin = user.role === 'ADMIN';
  const [routes, setRoutes] = useState<Route[] | null>(null);
  const [branches, setBranches] = useState<Branch[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [editing, setEditing] = useState<Route | 'new' | null>(null);
  // The typed data survives closing the form while the view stays open (AGENTS.md §6).
  const [draft, setDraft] = useState<Draft>(emptyDraft);
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState('');
  const [confirmed, setConfirmed] = useState('');

  function handle(reason: unknown, show: (text: string) => void, fallback: string) {
    if (reason instanceof ApiError && reason.status === 401) onError(reason);
    else show(failure(reason, fallback));
  }

  async function load() {
    setLoading(true); setLoadError('');
    try { setRoutes(await client.routes()); } catch (reason) {
      handle(reason, setLoadError, 'No se pudieron consultar las rutas.');
    } finally { setLoading(false); }
  }
  useEffect(() => {
    if (!visible) { setConfirmed(''); return; }
    void load();
    if (admin) client.visibleBranches().then(setBranches).catch(onError);
  }, [client, admin, revision, visible]);

  function edit(target: Route | 'new') {
    setEditing(target); setFormError(''); setConfirmed('');
    if (target !== 'new') {
      setDraft({ code: target.code, origin: target.origin.externalId, destination: target.destination.externalId,
        priority: target.priority, active: target.active });
    } else if (editing !== 'new') setDraft(emptyDraft());
    revealEditor('route-editor');
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!editing || !draft.priority) return;
    const code = draft.code.trim();
    if (editing === 'new' && draft.origin === draft.destination) {
      setFormError('El origen y el destino de una ruta deben ser sucursales distintas.');
      return;
    }
    setBusy(true); setFormError('');
    (editing === 'new'
      ? client.createRoute({ code, origin: draft.origin, destination: draft.destination, priority: draft.priority })
      : client.updateRoute(editing.externalId, { code, priority: draft.priority, active: draft.active }))
      .then(saved => {
        setConfirmed(editing === 'new'
          ? `Ruta ${saved.code} registrada con prioridad ${ROUTE_PRIORITY[saved.priority].label.toLowerCase()}.`
          : `Ruta ${saved.code} actualizada. Los despachos ya registrados conservan la prioridad que tenían.`);
        setEditing(null);
        setDraft(emptyDraft());
        void load();
      }).catch(reason => handle(reason, setFormError, 'No se pudo guardar la ruta.'))
      .finally(() => setBusy(false));
  }

  const active = branches.filter(branch => branch.active);

  return <section aria-labelledby="routes-title" aria-busy={loading || busy}>
    <h2 id="routes-title" tabIndex={-1}>Rutas</h2>
    <p>{admin
      ? 'Cada ruta va de una sucursal origen a una destino y se clasifica por prioridad manual. El despacho de una transferencia usa la ruta activa de su origen y destino.'
      : 'Rutas activas que salen de su sucursal o llegan a ella, clasificadas por prioridad. Las mantiene el administrador; el despacho usa la de cada transferencia.'}</p>
    <div className="list-heading">
      <div><h3>Clasificación por prioridad</h3>
        <p>{admin ? 'Activas e inactivas, de prioridad alta a baja.' : 'De prioridad alta a baja.'}</p></div>
      <div className="flex flex-wrap gap-2">
        <Button variant="outline" disabled={loading} onClick={() => void load()}>
          <RefreshCw aria-hidden="true" />{loading ? 'Actualizando…' : 'Actualizar rutas'}</Button>
        {admin && !editing && <Button onClick={() => edit('new')}><Plus aria-hidden="true" />Nueva ruta</Button>}
      </div>
    </div>
    {confirmed && <p role="status">{confirmed}</p>}

    {admin && editing && <form id="route-editor" onSubmit={submit} aria-describedby={formError ? 'route-error' : undefined}>
      <fieldset disabled={busy}>
        <legend>{editing === 'new' ? 'Nueva ruta' : `Editar ruta ${editing.code}`}</legend>
        <div className="grid gap-4 sm:grid-cols-2">
          <label>Código<Input value={draft.code} onChange={event => setDraft({ ...draft, code: event.target.value })}
            required maxLength={64} autoComplete="off" /></label>
          <div><label htmlFor="route-priority">Prioridad</label>
            <select id="route-priority" value={draft.priority} required
              onChange={event => setDraft({ ...draft, priority: event.target.value as RoutePriority | '' })}>
              <option value="">Seleccione la prioridad</option>
              {ROUTE_PRIORITIES.map(priority => <option key={priority} value={priority}>{ROUTE_PRIORITY[priority].label}</option>)}
            </select></div>
          {editing === 'new' ? <>
            <div><label htmlFor="route-origin">Sucursal origen</label>
              <select id="route-origin" value={draft.origin} required onChange={event => setDraft({ ...draft, origin: event.target.value })}>
                <option value="">Seleccione el origen</option>
                {active.map(branch => <option key={branch.externalId} value={branch.externalId}>{branch.code} · {branch.name}</option>)}
              </select></div>
            <div><label htmlFor="route-destination">Sucursal destino</label>
              <select id="route-destination" value={draft.destination} required
                onChange={event => setDraft({ ...draft, destination: event.target.value })}>
                <option value="">Seleccione el destino</option>
                {active.filter(branch => branch.externalId !== draft.origin).map(branch =>
                  <option key={branch.externalId} value={branch.externalId}>{branch.code} · {branch.name}</option>)}
              </select></div>
          </> : <p className="sm:col-span-2 transfer-route">
            <span><span className="transfer-route-role">Origen</span>{editing.origin.name}</span>
            <ArrowRight aria-hidden="true" /><span className="sr-only">hacia</span>
            <span><span className="transfer-route-role">Destino</span>{editing.destination.name}</span>
          </p>}
        </div>
        <p className="text-muted-foreground">{editing === 'new'
          ? 'La ruta es dirigida: de destino a origen se registra otra. Su origen y destino no se modifican después.'
          : 'El origen y el destino no se modifican; para otro trayecto registre una ruta nueva.'}</p>
        {editing !== 'new' && <label className="checkbox-field"><input type="checkbox" checked={draft.active}
          onChange={event => setDraft({ ...draft, active: event.target.checked })} />Ruta activa: disponible para nuevos despachos</label>}
        {formError && <p role="alert" id="route-error">{formError}</p>}
        <Button type="submit"><Save aria-hidden="true" />{busy ? 'Guardando…' : editing === 'new' ? 'Registrar ruta' : 'Guardar cambios'}</Button>
        <Button variant="ghost" type="button" onClick={() => { setEditing(null); setFormError(''); }}>
          <X aria-hidden="true" />Cerrar formulario
        </Button>
      </fieldset>
    </form>}

    {loadError && <p role="alert">{loadError}</p>}
    {!routes && loading && <p role="status">Consultando rutas…</p>}
    {routes && (routes.length === 0
      ? <p>{admin ? 'Todavía no hay rutas. Registre la ruta de cada origen y destino antes de despachar transferencias.'
        : 'Su sucursal no tiene rutas activas. El administrador debe registrarlas antes de que se despachen transferencias.'}</p>
      : ROUTE_PRIORITIES.map(priority => {
        const group = routes.filter(route => route.priority === priority);
        const heading = `routes-${priority.toLowerCase()}`;
        return <section key={priority} className="route-group" aria-labelledby={heading}>
          <h4 id={heading}>{ROUTE_PRIORITY[priority].group} <span className="cell-note">{group.length === 1 ? '1 ruta' : `${group.length} rutas`}</span></h4>
          {group.length === 0 ? <p className="text-muted-foreground">Sin rutas con esta prioridad.</p>
            : <div className="table-scroll" role="region" aria-label={ROUTE_PRIORITY[priority].group} tabIndex={0}>
              <Table>
                <TableCaption>Rutas dirigidas de origen a destino.</TableCaption>
                <TableHeader><TableRow>
                  <TableHead scope="col">Ruta</TableHead><TableHead scope="col">Origen</TableHead>
                  <TableHead scope="col">Destino</TableHead><TableHead scope="col">Prioridad</TableHead>
                  {admin && <TableHead scope="col">Estado</TableHead>}
                  {admin && <TableHead scope="col"><span className="sr-only">Acciones</span></TableHead>}
                </TableRow></TableHeader>
                <TableBody>{group.map(route => <TableRow key={route.externalId}>
                  <TableCell><span className="record-code">{route.code}</span></TableCell>
                  <TableCell>{route.origin.name}<span className="cell-note">{route.origin.code}</span></TableCell>
                  <TableCell>{route.destination.name}<span className="cell-note">{route.destination.code}</span></TableCell>
                  <TableCell><RoutePriorityBadge priority={route.priority} /></TableCell>
                  {admin && <TableCell><StatusBadge active={route.active} activeLabel="Activa" inactiveLabel="Inactiva" /></TableCell>}
                  {admin && <TableCell><Button variant="outline" size="sm" onClick={() => edit(route)}>
                    <Pencil aria-hidden="true" />Editar<span className="sr-only"> {route.code}</span></Button></TableCell>}
                </TableRow>)}</TableBody>
              </Table>
            </div>}
        </section>;
      }))}
  </section>;
}
