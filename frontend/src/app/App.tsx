import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { ApiClient, ApiError } from '../lib/api/client.ts';
import type { User } from '../lib/api/client.ts';
import { Administration } from './Administration.tsx';
import { Catalog } from './Catalog.tsx';
import { Movements } from './Movements.tsx';
import { Visibility } from './Visibility.tsx';
import { AppShell } from '../components/shared/AppShell.tsx';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { PasswordField } from '../components/shared/PasswordField.tsx';
import { ArrowLeftRight, Building2, KeyRound, LogIn, LogOut, Package, Search, UserRound } from 'lucide-react';

export function App() {
  const [user, setUser] = useState<User | null>(null);
  const [busy, setBusy] = useState(true);
  const [revision, setRevision] = useState(0);
  const [message, setMessage] = useState('Comprobando sesión…');
  const [error, setError] = useState('');
  const [location, setLocation] = useState(() => window.location.hash.slice(1));
  useEffect(() => {
    const update = () => setLocation(window.location.hash.slice(1));
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
      try { setUser(await client.login(String(data.get('code')), String(data.get('password')))); }
      finally { form.reset(); }
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

  const navigation = [
    ...(!user?.mustChangePassword ? [
      ...(user?.role === 'ADMIN' ? [{ id: 'administration-title', label: 'Administración', icon: Building2 }] : []),
      // Consultas stays first for local profiles: the first entry is the landing view.
      { id: 'visibility-title', label: 'Consultas', icon: Search },
      { id: 'catalog-title', label: 'Catálogo', icon: Package },
      { id: 'movements-title', label: 'Movimientos', icon: ArrowLeftRight },
    ] : []),
    { id: 'account-title', label: 'Mi sesión', icon: UserRound },
    { id: 'password-title', label: 'Contraseña', icon: KeyRound },
  ];
  const current = !user ? 'login-title' : user.mustChangePassword ? (location === 'account-title' ? 'account-title' : 'password-title')
    : navigation.some(item => item.id === location) ? location : navigation[0].id;

  return <AppShell navigation={navigation} current={current} busy={busy} session={user}>
    <p role="status" aria-live="polite">{busy ? 'Procesando solicitud…' : message}</p>
    {error && <p role="alert" id="feedback">{error}</p>}
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
          <dt>Rol</dt><dd>{user.role}</dd><dt>Sucursal</dt><dd>{user.branchExternalId ?? 'Ámbito global'}</dd></dl>
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
      <div hidden={current !== 'catalog-title'}><Catalog client={client} user={user} onError={showError} revision={revision} /></div>
      <div hidden={current !== 'movements-title'}><Movements client={client} user={user} onError={showError} revision={revision} visible={current === 'movements-title'} /></div>
      <div hidden={current !== 'visibility-title'}><Visibility client={client} user={user} onError={showError} revision={revision} visible={current === 'visibility-title'} /></div>
      </>}
    </>}
  </AppShell>;
}
