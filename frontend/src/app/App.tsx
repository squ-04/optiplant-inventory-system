import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { FormEvent } from 'react';
import { ApiClient, ApiError } from '../lib/api/client.ts';
import { roleLabel } from '../lib/format.ts';
import type { User } from '../lib/api/client.ts';
import { Administration } from './Administration.tsx';
import { Catalog } from './Catalog.tsx';
import { Movements } from './Movements.tsx';
import { Valuation } from './Valuation.tsx';
import { Purchases } from './Purchases.tsx';
import { PriceLists } from './PriceLists.tsx';
import { Sales } from './Sales.tsx';
import { Transfers } from './Transfers.tsx';
import { Routes } from './Routes.tsx';
import { LogisticsCompliance } from './LogisticsCompliance.tsx';
import { Reports } from './Reports.tsx';
import { Dashboard } from './Dashboard.tsx';
import { ReorderAlerts, reorderAlertsQuery } from './ReorderAlerts.tsx';
import { ShortageAlerts, shortageAlertsQuery } from './ShortageAlerts.tsx';
import { Visibility } from './Visibility.tsx';
import { AppShell } from '../components/shared/AppShell.tsx';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { PasswordField } from '../components/shared/PasswordField.tsx';
import { alertsNavigationLabel, restockNotice } from '../lib/inventory/alerts.ts';
import {
  alertCounts, dismissResolution, dismissedResolutions, recentResolutions, resolutionNotice, shortageNotice,
} from '../lib/transfers/shortageAlerts.ts';
import { ArrowLeftRight, BellRing, ChartColumn, FileSpreadsheet, Gauge, Route as RouteIcon, Building2, Coins, KeyRound, LogIn, LogOut, Package, ReceiptText, Search, ShoppingCart, Tags, TriangleAlert, Truck, UserRound } from 'lucide-react';

/** The browser's storage, or null where it is blocked (private windows, cleared site data). */
function browserStorage(): Storage | null {
  try { return window.localStorage; } catch { return null; }
}

export function App() {
  const [user, setUser] = useState<User | null>(null);
  const [busy, setBusy] = useState(true);
  const [revision, setRevision] = useState(0);
  const [message, setMessage] = useState('Comprobando sesión…');
  const [error, setError] = useState('');
  const [location, setLocation] = useState(() => window.location.hash.slice(1));
  useEffect(() => {
    // A shell alert belongs to the view where it happened; navigating away dismisses it.
    const update = () => { setLocation(window.location.hash.slice(1)); setError(''); };
    window.addEventListener('hashchange', update);
    return () => window.removeEventListener('hashchange', update);
  }, []);
  const [client] = useState(() => new ApiClient(
    import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080', fetch,
    () => { setUser(null); },
  ));

  function showError(reason: unknown) {
    setError(reason instanceof ApiError && reason.status === 401
      ? 'Sesión no disponible o credenciales incorrectas. Inicie sesión de nuevo. Los formularios no enviados no se confirmaron.'
      : reason instanceof TypeError ? 'Sin comunicación con el servidor. No se confirmó la solicitud; las consultas pueden estar desactualizadas. No se guardarán operaciones para sincronización offline.'
      : reason instanceof Error ? reason.message : 'No se pudo completar la solicitud.');
    if (reason instanceof ApiError && reason.status === 403 && reason.problem.code === 'PASSWORD_CHANGE_REQUIRED') {
      setUser(previous => previous && { ...previous, mustChangePassword: true });
    }
  }

  useEffect(() => {
    let active = true;
    client.me().then(value => { if (active) setUser(value); }).catch(reason => {
      if (active && !(reason instanceof ApiError && reason.status === 401)) showError(reason);
    }).finally(() => { if (active) { setBusy(false); setMessage(''); } });
    return () => { active = false; };
  }, [client]);

  async function run(action: () => Promise<void>) {
    setBusy(true); setError(''); setMessage('');
    try { await action(); } catch (reason) { showError(reason); }
    finally { setBusy(false); }
  }

  function login(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    void run(async () => {
      try {
        setUser(await client.login(String(data.get('code')), String(data.get('password'))));
        // Each session starts on its own landing view, not on the view the previous session left in the URL.
        window.history.replaceState(null, '', window.location.pathname + window.location.search);
        setLocation('');
      } finally { form.reset(); }
    });
  }

  function changePassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    void run(async () => {
      try {
        await client.changePassword(String(data.get('currentPassword')), String(data.get('newPassword')));
        setMessage('Contraseña actualizada. Inicie sesión con la nueva contraseña.');
      } finally { form.reset(); }
    });
  }

  // RF-0020 (INC-010/D04): managers and administrators keep a persistent notice while alerts are active; operators see
  // the indicator of their local catalog instead.
  const alertsAllowed = user !== null && !user.mustChangePassword && user.role !== 'OPERATOR';
  const valuationAllowed = alertsAllowed;
  const complianceAllowed = alertsAllowed;
  const reportsAllowed = alertsAllowed;
  const alerts = useQuery(reorderAlertsQuery(client, '', false, alertsAllowed));
  // RF-0038: open shortage alerts add to the counter with their own notice; a manager is told, in this browser, when the
  // other branch's manager resolved one in the last 24 h.
  const shortages = useQuery(shortageAlertsQuery(client, 'OPEN', alertsAllowed));
  const counts = alertCounts(alertsAllowed, alerts.data, shortages.data);
  const activeAlerts = counts.restock;
  const openShortages = counts.shortages;
  const manager = alertsAllowed && user?.role === 'MANAGER';
  const resolutions = useQuery(shortageAlertsQuery(client, 'RESOLVED', manager));
  const [dismissed, setDismissed] = useState<Set<string>>(new Set());
  useEffect(() => { setDismissed(user ? dismissedResolutions(browserStorage(), user.code) : new Set()); }, [user?.code]);
  const announced = manager && user ? recentResolutions(resolutions.data, user, dismissed) : [];

  const navigation = [
    ...(!user?.mustChangePassword ? [
      ...(user?.role === 'ADMIN' ? [{ id: 'administration-title', label: 'Administración', icon: Building2 }] : []),
      // Consultas stays first for local profiles: the first entry is the landing view.
      { id: 'visibility-title', label: 'Consultas', icon: Search },
      // RF-0044–RF-0049 (UC-019): every profile reads the dashboard of its branch; administrators choose it and compare the network.
      { id: 'dashboard-title', label: 'Dashboard', icon: ChartColumn },
      { id: 'catalog-title', label: 'Catálogo', icon: Package },
      { id: 'movements-title', label: 'Movimientos', icon: ArrowLeftRight },
      { id: 'purchases-title', label: 'Compras', icon: ShoppingCart },
      // RF-0027/RF-0031: operators confirm sales of their branch; managers and administrators consult the records.
      { id: 'sales-title', label: 'Ventas', icon: ReceiptText },
      // RF-0032–RF-0035: the destination operator requests, the origin operator prepares and dispatches, the origin manager decides.
      { id: 'transfers-title', label: 'Transferencias', icon: Truck },
      // RF-0041 (INC-003/D06): every profile consults routes; only administrators maintain them.
      { id: 'routes-title', label: 'Rutas', icon: RouteIcon },
      // RF-0043 (§5.2): logistics reports for managers (own branch) and administrators; operators consult each transfer.
      ...(complianceAllowed ? [{ id: 'compliance-title', label: 'Cumplimiento', icon: Gauge }] : []),
      // RF-0050 (INC-020/D03): administrators export one branch or the network, managers their own; operators nothing.
      ...(reportsAllowed ? [{ id: 'reports-title', label: 'Reportes', icon: FileSpreadsheet }] : []),
      // RF-0030 (INC-003/D06): every profile consults price lists; only administrators maintain them.
      { id: 'prices-title', label: 'Precios', icon: Tags },
      // RF-0026 (INC-003/D04): valuation is for the branch manager and administrators, never operators.
      ...(valuationAllowed ? [{ id: 'valuation-title', label: 'Valoración', icon: Coins }] : []),
      ...(alertsAllowed ? [{ id: 'alerts-title', label: 'Alertas', icon: BellRing, count: counts.badge,
        accessibleLabel: alertsNavigationLabel(activeAlerts, openShortages) }] : []),
    ] : []),
    { id: 'account-title', label: 'Mi sesión', icon: UserRound },
    { id: 'password-title', label: 'Contraseña', icon: KeyRound },
  ];
  const current = !user ? 'login-title' : user.mustChangePassword ? (location === 'account-title' ? 'account-title' : 'password-title')
    : navigation.some(item => item.id === location) ? location : navigation[0].id;

  return <AppShell navigation={navigation} current={current} busy={busy} session={user}>
    <p role="status" aria-live="polite">{busy ? 'Procesando solicitud…' : message}</p>
    {error && <p role="alert" id="feedback">{error}</p>}
    {alertsAllowed && current !== 'alerts-title' && restockNotice(activeAlerts, user?.role === 'ADMIN') &&
      <p className="notice-warning restock-notice"><TriangleAlert aria-hidden="true" />
        <span>{restockNotice(activeAlerts, user?.role === 'ADMIN')}</span>
        <a href="#alerts-title">Ver alertas de reabastecimiento</a></p>}
    {alertsAllowed && current !== 'alerts-title' && shortageNotice(openShortages, user?.role === 'ADMIN') &&
      <p className="notice-warning restock-notice"><TriangleAlert aria-hidden="true" />
        <span>{shortageNotice(openShortages, user?.role === 'ADMIN')}</span>
        <a href="#alerts-title">Ver faltantes de transferencias</a></p>}
    {announced.map(transfer => <p key={transfer.discrepancy!.externalId} className="notice-warning restock-notice">
      <BellRing aria-hidden="true" /><span>{resolutionNotice(transfer)}</span>
      <Button variant="outline" size="sm" onClick={() => setDismissed(dismissResolution(browserStorage(), user!.code,
        transfer.discrepancy!.externalId))}>Entendido<span className="sr-only">: ocultar el aviso de {transfer.code}</span></Button>
    </p>)}
    {!user ? <section className="login-panel" aria-labelledby="login-title"><h2 id="login-title" tabIndex={-1}>Iniciar sesión</h2>
      <form onSubmit={login} aria-describedby={error ? 'feedback' : undefined}>
        <fieldset disabled={busy}><legend>Credenciales individuales</legend>
          <label>Código de usuario<Input name="code" autoComplete="username" required /></label>
          <PasswordField label="Contraseña" name="password" autoComplete="current-password" required />
          <Button type="submit"><LogIn aria-hidden="true" />Iniciar sesión</Button>
        </fieldset>
      </form>
    </section> : <>
      <section hidden={current !== 'account-title'} className="session-panel" aria-labelledby="account-title"><h2 id="account-title" tabIndex={-1}>Contexto de sesión</h2>
        <dl><dt>Usuario</dt><dd>{user.code}</dd><dt>Identificador público</dt><dd>{user.externalId}</dd>
          <dt>Rol</dt><dd>{roleLabel(user.role)}</dd><dt>Sucursal</dt><dd>{user.branchExternalId ?? 'Ámbito global'}</dd></dl>
        <Button variant="outline" disabled={busy} onClick={() => void run(async () => { await client.logout(); setMessage('Sesión cerrada.'); })}><LogOut aria-hidden="true" />Cerrar sesión</Button>
      </section>
      <section hidden={current !== 'password-title'} aria-labelledby="password-title"><h2 id="password-title" tabIndex={-1}>{user.mustChangePassword ? 'Cambio inicial obligatorio' : 'Cambiar contraseña'}</h2>
        <p>{user.mustChangePassword ? 'Debe cambiar su contraseña temporal antes de operar.' : 'El cambio cerrará todas sus sesiones.'}</p>
        <form onSubmit={changePassword} aria-describedby={error ? 'feedback' : undefined}>
          <fieldset disabled={busy}><legend>Nueva contraseña de 15 a 128 caracteres</legend>
            <PasswordField label="Contraseña actual" name="currentPassword" autoComplete="current-password" required />
            <PasswordField label="Nueva contraseña" name="newPassword" autoComplete="new-password" minLength={15} maxLength={128} required />
            <Button type="submit"><KeyRound aria-hidden="true" />Actualizar contraseña</Button>
          </fieldset>
        </form>
      </section>
      {!user.mustChangePassword && <>
      {user.role === 'ADMIN' && <div hidden={current !== 'administration-title'}><Administration client={client} onError={showError} onChanged={() => setRevision(value => value + 1)} /></div>}
      <div hidden={current !== 'catalog-title'}><Catalog client={client} user={user} onError={showError} revision={revision}
        visible={current === 'catalog-title'} /></div>
      <div hidden={current !== 'movements-title'}><Movements client={client} user={user} onError={showError} revision={revision} visible={current === 'movements-title'} /></div>
      <div hidden={current !== 'purchases-title'}><Purchases client={client} user={user} onError={showError}
        onChanged={() => setRevision(value => value + 1)} revision={revision} visible={current === 'purchases-title'} /></div>
      <div hidden={current !== 'sales-title'}><Sales client={client} user={user} onError={showError}
        onChanged={() => setRevision(value => value + 1)} revision={revision} visible={current === 'sales-title'} /></div>
      <div hidden={current !== 'transfers-title'}><Transfers client={client} user={user} onError={showError}
        revision={revision} visible={current === 'transfers-title'} /></div>
      <div hidden={current !== 'routes-title'}><Routes client={client} user={user} onError={showError}
        revision={revision} visible={current === 'routes-title'} /></div>
      <div hidden={current !== 'dashboard-title'}><Dashboard client={client} user={user} onError={showError}
        revision={revision} visible={current === 'dashboard-title'} /></div>
      {complianceAllowed && <div hidden={current !== 'compliance-title'}><LogisticsCompliance client={client} user={user}
        onError={showError} revision={revision} visible={current === 'compliance-title'} /></div>}
      {reportsAllowed && <div hidden={current !== 'reports-title'}><Reports client={client} user={user}
        onError={showError} visible={current === 'reports-title'} /></div>}
      <div hidden={current !== 'prices-title'}><PriceLists client={client} user={user} onError={showError}
        revision={revision} visible={current === 'prices-title'} /></div>
      {valuationAllowed && <div hidden={current !== 'valuation-title'}><Valuation client={client} user={user} onError={showError}
        revision={revision} visible={current === 'valuation-title'} /></div>}
      {alertsAllowed && <div hidden={current !== 'alerts-title'}><ReorderAlerts client={client} user={user} onError={showError}
        revision={revision} visible={current === 'alerts-title'} />
        <ShortageAlerts client={client} user={user} onError={showError} revision={revision} visible={current === 'alerts-title'} /></div>}
      <div hidden={current !== 'visibility-title'}><Visibility client={client} user={user} onError={showError} revision={revision} visible={current === 'visibility-title'} /></div>
      </>}
    </>}
  </AppShell>;
}
