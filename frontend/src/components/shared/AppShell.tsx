import type { ReactNode } from 'react';
import { useEffect, useRef } from 'react';

interface NavigationItem { id: string; label: string }

export function AppShell({ children, navigation, current, busy, session }: {
  children: ReactNode;
  navigation: NavigationItem[];
  current: string;
  busy: boolean;
  session: { code: string; role: string } | null;
}) {
  const previous = useRef(current);
  useEffect(() => {
    if (previous.current !== current) document.getElementById(current)?.focus();
    previous.current = current;
  }, [current]);
  const brand = <div className="brand"><span className="brand-mark" aria-hidden="true">OP</span>
    <div><strong>OptiPlant</strong><span>Gestión multisucursal</span></div></div>;
  return <div className={session ? 'app-layout' : 'app-layout app-layout-public'}>
    <a className="skip-link" href={`#${current}`}>Saltar al contenido</a>
    {session && <aside className="app-sidebar" aria-label="Panel de navegación">
      {brand}
      <nav className="section-nav" aria-label="Secciones de trabajo">
        {navigation.map(item => <a key={item.id} href={`#${item.id}`}
          aria-current={current === item.id ? 'page' : undefined}>{item.label}</a>)}
      </nav>
      <div className="sidebar-session"><span className="session-avatar" aria-hidden="true">{session.code.slice(0, 2)}</span>
        <div><strong>{session.code}</strong><span>{session.role}</span><a href="#account-title">Ver mi sesión</a></div>
      </div>
    </aside>}
    <main className="app-main" aria-busy={busy}>
      <header className="app-header">{!session && brand}
        <div><p>Inventario multisucursal</p><h1>Centro de operaciones</h1></div>
        {session && <span className="workspace-label">OptiPlant</span>}
      </header>
      <div className="workspace">{children}</div>
    </main>
  </div>;
}
