async (page) => {
  // Run through playwright-cli against the local frontend, using isolated API fixtures.
  const origin = 'http://127.0.0.1:4175';
  const id = 'df06cdfe-a745-4bf4-bc81-e10db132724b';
  const branch = { externalId: id, code: 'NORTE', name: 'Sucursal Norte', active: true };
  const account = { externalId: id, code: 'OPERADOR-01', fullName: 'Ana Pérez', role: 'OPERATOR',
    branchExternalId: id, active: true, mustChangePassword: false };
  let currentBranch = { ...branch };
  let rejectBranchUpdate = false;
  const writes = [];
  let user = null;
  const assert = (condition, message) => { if (!condition) throw new Error(message); };
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname;
    let body = [];
    let status = 200;
    if (path.endsWith('/auth/me')) {
      body = user ?? { code: 'UNAUTHORIZED', detail: 'Inicie sesión.' };
      status = user ? 200 : 401;
    } else if (path.endsWith('/auth/csrf')) {
      body = { token: 'fixture', headerName: 'X-CSRF-TOKEN' };
    } else if (path.endsWith('/branches')) {
      body = [currentBranch];
    } else if (path === `/api/v1/branches/${id}`) {
      body = currentBranch;
    } else if (path === `/api/v1/branches/${id}/update`) {
      writes.push({ method: route.request().method(), body: route.request().postDataJSON(),
        csrf: await route.request().headerValue('X-CSRF-TOKEN') });
      if (rejectBranchUpdate) {
        status = 409;
        body = { code: 'BRANCH_UPDATE_REJECTED', detail: 'No se confirmó el cambio de sucursal.' };
      } else {
        currentBranch = { ...currentBranch, name: route.request().postDataJSON().name };
        body = currentBranch;
      }
    } else if (path.endsWith('/users')) {
      body = [account];
    } else if (path === `/api/v1/users/${id}`) {
      body = account;
    } else if (path.includes('/visibility/branches/')) {
      body = { branch, inventory: [{ productExternalId: id, sku: 'MAT-001',
        name: 'Material para mantenimiento de planta', physicalQuantity: '1250.000' }],
        purchases: [], sales: [], transfers: [], movements: [] };
    }
    await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body),
      headers: { 'Access-Control-Allow-Origin': origin, 'Access-Control-Allow-Credentials': 'true' } });
  });
  const noOverflow = async () => assert(await page.evaluate(() =>
    document.documentElement.scrollWidth <= window.innerWidth), 'Page overflows horizontally');
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto(origin);
  await page.getByRole('button', { name: 'Iniciar sesión', exact: true }).waitFor({ state: 'visible' });
  await page.waitForFunction(() => document.querySelector('main')?.getAttribute('aria-busy') === 'false');
  assert(await page.getByRole('button', { name: 'Iniciar sesión', exact: true }).getAttribute('data-slot') === 'button',
    'Login must reuse the official shared button');
  assert(await page.getByLabel('Código de usuario', { exact: true }).getAttribute('data-slot') === 'input',
    'Login must reuse the official shared input');
  assert(await page.locator('nav').count() === 0, 'Unauthenticated navigation must remain hidden');
  await page.keyboard.press('Tab');
  assert(await page.getByRole('link', { name: 'Saltar al contenido' }).evaluate(el =>
    el === document.activeElement), 'Skip link must be first in keyboard order');
  await page.keyboard.press('Enter');
  assert(await page.locator('#login-title').evaluate(el => el === document.activeElement), 'Skip link must focus content');
  await noOverflow();
  const contrast = await page.evaluate(() => {
    const style = getComputedStyle(document.documentElement);
    const luminance = hex => {
      const values = hex.trim().replace('#', '').match(/../g).map(value => {
        const channel = parseInt(value, 16) / 255;
        return channel <= .04045 ? channel / 12.92 : ((channel + .055) / 1.055) ** 2.4;
      });
      return values[0] * .2126 + values[1] * .7152 + values[2] * .0722;
    };
    return ['--color-text', '--color-muted', '--color-action', '--color-danger', '--color-warning']
      .map(token => 1.05 / (luminance(style.getPropertyValue(token)) + .05));
  });
  assert(contrast.every(ratio => ratio >= 4.5), 'Text and primary action colors must meet 4.5:1 contrast on white');

  user = { externalId: id, code: 'ANA', role: 'ADMIN', branchExternalId: null, mustChangePassword: false };
  await page.goto(origin);
  await page.getByRole('navigation', { name: 'Secciones de trabajo' }).waitFor();
  await page.getByRole('link', { name: 'Administración', exact: true }).click();
  assert(new URL(page.url()).hash === '#administration-title', 'Administration link must reach its section');
  assert(await page.locator('.app-sidebar').count() === 1, 'Authenticated shell must have a persistent sidebar');
  assert(await page.getByRole('button', { name: 'Actualizar listados', exact: true }).evaluate(el =>
    getComputedStyle(el).color === 'rgb(20, 33, 61)'), 'Outline actions must keep legible navy text');
  assert(await page.getByRole('heading', { name: 'Contexto de sesión' }).isVisible() === false,
    'Only the selected workspace may be visible');
  await page.getByRole('tab', { name: 'Usuarios', exact: true }).click();
  assert(await page.getByRole('tab', { name: 'Usuarios', exact: true }).getAttribute('data-slot') === 'tabs-trigger',
    'Context navigation must reuse official Radix-backed tabs');
  await page.getByRole('columnheader', { name: 'Sucursal asignada', exact: true }).waitFor();
  await page.getByRole('cell', { name: 'Operador', exact: true }).waitFor();
  await page.getByRole('button', { name: 'Editar OPERADOR-01', exact: true }).click();
  await page.getByRole('button', { name: 'Desactivar usuario', exact: true }).waitFor();
  assert(await page.getByLabel('Nombre completo', { exact: true }).inputValue() === 'Ana Pérez',
    'User table actions must still load the original editor');
  await page.getByRole('button', { name: 'Guardar usuario', exact: true }).waitFor();
  await page.getByRole('tab', { name: 'Sucursales', exact: true }).focus();
  await page.keyboard.press('ArrowRight');
  await page.waitForFunction(() => [...document.querySelectorAll('[role="tab"]')]
    .some(tab => tab.textContent === 'Usuarios' && tab.getAttribute('aria-selected') === 'true'));
  assert(await page.getByRole('tab', { name: 'Usuarios', exact: true }).getAttribute('aria-selected') === 'true',
    'Context tabs must support arrow-key navigation');
  await page.getByRole('tab', { name: 'Sucursales', exact: true }).click();
  assert(await page.getByRole('region', { name: 'Sucursales registradas' }).locator('[data-slot="table"]').count() === 1,
    'Branch listing must reuse the official shared table');
  await page.getByRole('button', { name: 'Editar NORTE', exact: true }).click();
  await page.getByRole('button', { name: 'Desactivar sucursal', exact: true }).waitFor();
  assert(await page.locator('section[aria-labelledby="administration-title"]').getByLabel('Código', { exact: true }).getAttribute('readonly') !== null,
    'Existing branch codes must remain read-only when editing');
  assert(await page.getByRole('button', { name: 'Guardar sucursal', exact: true }).evaluate(el =>
    getComputedStyle(el).backgroundColor === 'rgb(194, 65, 12)'), 'Primary actions must use the approved accessible orange');
  await page.locator('section[aria-labelledby="administration-title"]').getByLabel('Nombre', { exact: true }).fill('Sucursal Norte revisada');
  await page.getByRole('button', { name: 'Guardar sucursal', exact: true }).click();
  await page.getByText('Sucursal guardada.', { exact: true }).waitFor();
  assert(currentBranch.name === 'Sucursal Norte revisada', 'Shared input/button must preserve confirmed writes');
  assert(writes.length === 1 && writes[0].method === 'POST' && writes[0].csrf === 'fixture'
    && JSON.stringify(writes[0].body) === JSON.stringify({ name: 'Sucursal Norte revisada' }),
    'Branch update must preserve method, CSRF and payload without changing code');
  const beforeRejection = JSON.stringify(currentBranch);
  rejectBranchUpdate = true;
  await page.locator('section[aria-labelledby="administration-title"]').getByLabel('Nombre', { exact: true }).fill('Cambio rechazado');
  await page.getByRole('button', { name: 'Guardar sucursal', exact: true }).click();
  await page.getByText('No se confirmó el cambio de sucursal.', { exact: true }).first().waitFor();
  assert(JSON.stringify(currentBranch) === beforeRejection && writes.length === 2,
    'Rejected updates must preserve stored data and must not retry');
  assert(await page.getByText('Sucursal guardada.', { exact: true }).count() === 0,
    'A rejected command must not retain a previous success confirmation');
  rejectBranchUpdate = false;
  await page.getByRole('link', { name: 'Consultas', exact: true }).click();
  await page.waitForFunction(() => document.activeElement?.id === 'visibility-title');
  await page.locator('#visibility-branch').selectOption(id);
  await page.getByRole('button', { name: 'Consultar datos actuales' }).click();
  await page.getByRole('cell', { name: '1250.000' }).waitFor();
  const table = page.getByRole('region', { name: 'Existencias por producto' });
  await table.focus();
  assert(await table.evaluate(el => el === document.activeElement), 'Table region must be keyboard focusable');
  assert(await page.getByRole('cell', { name: '1250.000' }).evaluate(el =>
    getComputedStyle(el).textAlign === 'right'), 'Quantities must align right without changing decimal strings');
  await noOverflow();
  for (const width of [768, 390, 320]) {
    await page.setViewportSize({ width, height: 844 });
    await noOverflow();
  }
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await table.focus();
  await page.keyboard.press('ArrowRight');
  await page.waitForFunction(() => document.querySelector('[aria-label="Existencias por producto"]').scrollLeft > 0);
  await page.getByRole('link', { name: 'Administración', exact: true }).click();
  await page.getByRole('tab', { name: 'Usuarios', exact: true }).click();
  await page.getByLabel('Nombre completo', { exact: true }).fill('Borrador sin enviar');
  await page.getByRole('link', { name: 'Consultas', exact: true }).click();
  await page.getByRole('link', { name: 'Administración', exact: true }).click();
  assert(await page.getByLabel('Nombre completo', { exact: true }).inputValue() === 'Borrador sin enviar',
    'Navigation must preserve unsent forms');
  user = { ...user, role: 'OPERATOR', branchExternalId: id };
  await page.goto(origin);
  await page.getByRole('link', { name: 'Consultas', exact: true }).waitFor();
  assert(await page.getByRole('link', { name: 'Administración', exact: true }).count() === 0,
    'Operator navigation must not expose administration');
  user = { ...user, mustChangePassword: true };
  await page.goto(origin);
  await page.getByRole('heading', { name: 'Cambio inicial obligatorio' }).waitFor();
  assert(await page.getByRole('link', { name: 'Consultas', exact: true }).count() === 0,
    'Initial password change must not expose operational navigation');
  return 'PASS: sidebar, accessible tabs, table editors, unsent forms, login, skip link, role visibility, quantities, responsive widths 1440/768/390/320';
}
