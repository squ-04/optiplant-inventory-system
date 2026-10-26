async page => {
  // Independent regression probe, retained after verification of the component migration.
  await page.unrouteAll({ behavior: 'wait' });
  let admin = false;
  const mutations = [];
  let release;
  const hold = new Promise(resolve => { release = resolve; });
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname;
    let body = [], status = 200;
    if (path.endsWith('/auth/me')) {
      status = admin ? 200 : 401;
      body = admin ? { externalId: 'df06cdfe-a745-4bf4-bc81-e10db132724b', code: 'ADMIN', role: 'ADMIN', branchExternalId: null, mustChangePassword: false } : { code: 'UNAUTHORIZED', detail: 'Inicie sesión.' };
    } else if (path.endsWith('/auth/csrf')) body = { token: 'probe', headerName: 'X-CSRF-TOKEN' };
    else if (route.request().method() === 'POST') {
      mutations.push(route.request().postDataJSON());
      await hold;
      status = 409;
      body = { code: 'REJECTED', detail: 'Probe rejected.' };
    }
    await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body),
      headers: { 'Access-Control-Allow-Origin': 'http://127.0.0.1:4175', 'Access-Control-Allow-Credentials': 'true' } });
  });
  const check = (ok, message) => { if (!ok) throw Error(message); };
  await page.goto('http://127.0.0.1:4175');
  const login = page.getByRole('button', { name: 'Iniciar sesión', exact: true });
  await login.waitFor();
  await login.click();
  check(mutations.length === 0, 'Empty login must not submit');
  admin = true;
  await page.reload();
  await page.getByRole('link', { name: 'Administración', exact: true }).click();
  await page.getByText('No hay sucursales registradas.', { exact: true }).waitFor();
  await page.locator('section[aria-labelledby="administration-title"]').getByLabel('Código', { exact: true }).fill('SUR');
  await page.locator('section[aria-labelledby="administration-title"]').getByLabel('Nombre', { exact: true }).fill('Sucursal Sur');
  await page.getByRole('tab', { name: 'Usuarios', exact: true }).click();
  await page.getByText('No hay usuarios registrados.', { exact: true }).waitFor();
  await page.getByLabel('Nombre completo', { exact: true }).fill('Draft User');
  await page.getByRole('tab', { name: 'Sucursales', exact: true }).click();
  check(await page.locator('section[aria-labelledby="administration-title"]').getByLabel('Código', { exact: true }).inputValue() === 'SUR', 'Branch draft lost');
  await page.getByRole('button', { name: 'Guardar sucursal', exact: true }).click();
  await page.waitForFunction(() => document.querySelector('fieldset:disabled'));
  check(await page.locator('section[aria-labelledby="administration-title"]').getByLabel('Nombre', { exact: true }).isDisabled(), 'Busy input must inherit disabled fieldset');
  check(await page.getByRole('button', { name: 'Guardar sucursal', exact: true }).isDisabled(), 'Busy button must inherit disabled fieldset');
  release();
  await page.getByText('Probe rejected.', { exact: true }).first().waitFor();
  check(mutations.length === 1 && JSON.stringify(mutations[0]) === '{"code":"SUR","name":"Sucursal Sur"}', 'Unexpected payload or retry');
  check(await page.locator('section[aria-labelledby="administration-title"]').getByLabel('Nombre', { exact: true }).inputValue() === 'Sucursal Sur', 'Rejected draft lost');
  await page.getByRole('tab', { name: 'Usuarios', exact: true }).click();
  check(await page.getByLabel('Nombre completo', { exact: true }).inputValue() === 'Draft User', 'User draft lost');
  return 'PASS: empty validation, empty lists, both tab drafts, inherited disabled controls, rejection payload/no retry';
}
