import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { ApiClient, ApiError } from '../lib/api/client.ts';
import type { BootstrapDto, User } from '../lib/api/client.ts';

export function App() {
  const [user, setUser] = useState<User | null>(null);
  const [dto, setDto] = useState<BootstrapDto | null>(null);
  const [busy, setBusy] = useState(true);
  const [message, setMessage] = useState('Comprobando sesión…');
  const [error, setError] = useState('');
  const [client] = useState(() => new ApiClient(
    import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080', fetch,
    () => { setUser(null); setDto(null); },
  ));

  function showError(reason: unknown) {
    setError(reason instanceof ApiError && reason.status === 401
      ? 'Sesión no disponible o credenciales incorrectas. Inicie sesión de nuevo.'
      : reason instanceof Error ? reason.message : 'No se pudo completar la solicitud.');
    if (reason instanceof ApiError && reason.status === 403 && reason.problem.code === 'PASSWORD_CHANGE_REQUIRED') {
      setUser(previous => previous && { ...previous, mustChangePassword: true });
      setDto(null);
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

  return <main aria-busy={busy}>
    <header><h1>OptiPlant · Preparación técnica</h1>
      <p>Verificación mínima de sesión y API REST. Los módulos de negocio no están implementados en esta pantalla.</p></header>
    <p role="status" aria-live="polite">{busy ? 'Procesando solicitud…' : message}</p>
    {error && <p role="alert" id="feedback">{error}</p>}
    {!user ? <section aria-labelledby="login-title"><h2 id="login-title">Iniciar sesión</h2>
      <form onSubmit={login} aria-describedby={error ? 'feedback' : undefined}>
        <fieldset disabled={busy}><legend>Credenciales individuales</legend>
          <label>Código de usuario<input name="code" autoComplete="username" required /></label>
          <label>Contraseña<input name="password" type="password" autoComplete="current-password" required /></label>
          <button type="submit">Iniciar sesión</button>
        </fieldset>
      </form>
    </section> : <>
      <section aria-labelledby="account-title"><h2 id="account-title">Contexto de sesión</h2>
        <dl><dt>Usuario</dt><dd>{user.code}</dd><dt>Identificador público</dt><dd>{user.externalId}</dd>
          <dt>Rol</dt><dd>{user.role}</dd><dt>Sucursal</dt><dd>{user.branchExternalId ?? 'Ámbito global'}</dd></dl>
        <button disabled={busy} onClick={() => void run(async () => { await client.logout(); setMessage('Sesión cerrada.'); })}>Cerrar sesión</button>
      </section>
      {user.mustChangePassword ? <section aria-labelledby="password-title"><h2 id="password-title">Cambio inicial obligatorio</h2>
        <p>Debe cambiar su contraseña temporal antes de acceder a la comprobación protegida.</p>
        <form onSubmit={changePassword} aria-describedby={error ? 'feedback' : undefined}>
          <fieldset disabled={busy}><legend>Nueva contraseña de 15 a 128 caracteres</legend>
            <label>Contraseña actual<input name="currentPassword" type="password" autoComplete="current-password" required /></label>
            <label>Nueva contraseña<input name="newPassword" type="password" autoComplete="new-password" minLength={15} maxLength={128} required /></label>
            <button type="submit">Actualizar contraseña</button>
          </fieldset>
        </form>
      </section> : <section aria-labelledby="api-title"><h2 id="api-title">Comprobación API protegida</h2>
        <button disabled={busy} onClick={() => void run(async () => { setDto(null); setDto(await client.bootstrap()); setMessage('Respuesta recibida del backend.'); })}>Consultar prueba REST</button>
        {dto && <><p>Importe exacto recibido: <output>{dto.amount}</output>. Se conserva como cadena decimal.</p>
          <pre aria-label="DTO recibido del backend">{JSON.stringify(dto, null, 2)}</pre></>}
      </section>}
    </>}
  </main>;
}
