"""Exercise real UI/API/session integration against an explicitly disposable local stack."""
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys
import tempfile
from urllib.parse import urlparse


def main():
    if os.environ.get("OPTIPLANT_DISPOSABLE_TEST") != "1":
        print("Requiere OPTIPLANT_DISPOSABLE_TEST=1 y una pila local desechable; modifica datos de prueba.")
        return 2
    url = os.environ.get("OPTIPLANT_TEST_FRONTEND", "http://localhost:5173")
    parsed = urlparse(url)
    if parsed.scheme != "http" or parsed.hostname != "localhost" or parsed.username or parsed.password:
        print("Solo se admite una pila HTTP local en localhost.")
        return 2
    password = os.environ.get("OPTIPLANT_TEST_ADMIN_PASSWORD", "")
    if not 15 <= len(password) <= 128:
        print("Falta una contraseña inicial de prueba válida.")
        return 2
    directory = Path(tempfile.mkdtemp(prefix="optiplant-browser-"))
    session = "identity-" + secrets.token_hex(6)
    values = {"url": url, "admin": os.environ.get("OPTIPLANT_TEST_ADMIN_CODE", "ADMIN"),
              "initial": password, "adminPassword": secrets.token_urlsafe(30),
              "temporary": secrets.token_urlsafe(30), "userPassword": secrets.token_urlsafe(30),
              "suffix": secrets.token_hex(4)}
    script = r'''async page => {
      const config = CONFIG;
      let stage = 'admin-initial-login';
      try {
      const check = (condition, message) => { if (!condition) throw new Error(message); };
      const login = async (p, code, password) => {
        await p.getByLabel('Código de usuario', { exact: true }).fill(code);
        await p.getByLabel('Contraseña', { exact: true }).fill(password);
        const [response] = await Promise.all([
          p.waitForResponse(r => r.url().endsWith('/api/v1/auth/login') && r.request().method() === 'POST'),
          p.getByRole('button', { name: 'Iniciar sesión', exact: true }).click(),
        ]);
        return response.status();
      };
      const change = async (p, current, replacement) => {
        await p.getByLabel('Contraseña actual', { exact: true }).fill(current);
        await p.getByLabel('Nueva contraseña', { exact: true }).fill(replacement);
        await p.getByRole('button', { name: 'Actualizar contraseña', exact: true }).click();
        await p.getByRole('heading', { name: 'Iniciar sesión', exact: true }).waitFor();
      };
      await page.goto(config.url);
      await login(page, config.admin, config.initial);
      await page.getByRole('heading', { name: 'Cambio inicial obligatorio' }).waitFor();
      check(await page.getByRole('heading', { name: 'Administración de la red' }).count() === 0, 'Initial password gate missing');
      await change(page, config.initial, config.adminPassword);
      stage = 'admin-login-after-change';
      await login(page, config.admin, config.adminPassword);
      await page.getByRole('heading', { name: 'Administración de la red' }).waitFor();
      const a = 'UI-A-' + config.suffix, b = 'UI-B-' + config.suffix;
      stage = 'create-branches';
      for (const code of [a, b]) {
        await page.getByRole('button', { name: 'Nueva sucursal', exact: true }).click();
        const form = page.getByRole('group', { name: 'Crear sucursal', exact: true });
        await form.getByLabel('Código', { exact: true }).fill(code);
        await form.getByLabel('Nombre', { exact: true }).fill(code);
        await form.getByRole('button', { name: 'Guardar sucursal' }).click();
        await page.getByText('Sucursal guardada.', { exact: true }).waitFor();
      }
      // No reload: a registry change must refresh the directory used by the query panel.
      await page.getByRole('link', { name: 'Consultas', exact: true }).click();
      await page.getByLabel('Sucursal', { exact: true }).getByRole('option', { name: b + ' · ' + b, exact: true }).waitFor({ state: 'attached' });
      const user = 'UI-OP-' + config.suffix;
      stage = 'create-user';
      await page.getByRole('link', { name: 'Administración', exact: true }).click();
      await page.getByRole('tab', { name: 'Usuarios', exact: true }).click();
      await page.getByRole('button', { name: 'Nuevo usuario', exact: true }).click();
      const userForm = page.getByRole('group', { name: 'Crear usuario', exact: true });
      await userForm.getByLabel('Nombre completo').fill('Persona de prueba UI');
      await userForm.getByLabel('Código de usuario', { exact: true }).fill(user);
      await userForm.getByLabel('Sucursal activa').selectOption({ label: a + ' · ' + a });
      await userForm.getByLabel('Contraseña temporal individual', { exact: true }).fill(config.temporary);
      await userForm.getByRole('button', { name: 'Guardar usuario' }).click();
      await page.getByText('Usuario guardado.', { exact: false }).waitFor();
      const context = await page.context().browser().newContext();
      try {
        const operator = await context.newPage();
        stage = 'operator-initial-login';
        await operator.goto(config.url);
        await login(operator, user, config.temporary);
        await operator.getByRole('heading', { name: 'Cambio inicial obligatorio' }).waitFor();
        stage = 'operator-change-password';
        await change(operator, config.temporary, config.userPassword);
        stage = 'operator-local-query';
        await login(operator, user, config.userPassword);
        await operator.getByRole('heading', { name: 'Consulta de sucursal' }).waitFor();
        check(await operator.getByRole('heading', { name: 'Administración de la red' }).count() === 0, 'Operator sees administration');
        await operator.getByRole('button', { name: 'Consultar datos actuales' }).click();
        await operator.getByRole('heading', { name: 'Compras', exact: true }).waitFor();
        await operator.getByLabel('Sucursal', { exact: true }).selectOption({ label: b + ' · ' + b });
        await operator.getByRole('button', { name: 'Consultar datos actuales' }).click();
        await operator.getByRole('heading', { name: 'Existencias físicas' }).waitFor();
        check(await operator.getByRole('heading', { name: 'Compras', exact: true }).count() === 0, 'Cross-branch commerce leaked');
        stage = 'offline';
        await context.setOffline(true);
        await operator.getByRole('button', { name: 'Consultar datos actuales' }).click();
        await operator.getByRole('alert').filter({ hasText: 'Sin comunicación' }).waitFor();
        await context.setOffline(false);
        // Change scope in the original administrator session, then probe the old live UI.
        stage = 'scope-revocation';
        const edit = page.getByRole('group', { name: 'Editar usuario', exact: true });
        await edit.getByLabel('Perfil').selectOption('MANAGER');
        await edit.getByLabel('Sucursal activa').selectOption({ label: b + ' · ' + b });
        await edit.getByRole('button', { name: 'Guardar usuario' }).click();
        await page.getByText('Usuario guardado.', { exact: false }).waitFor();
        await operator.getByRole('button', { name: 'Consultar datos actuales' }).click();
        await operator.getByRole('heading', { name: 'Iniciar sesión', exact: true }).waitFor();
        await login(operator, user, config.userPassword);
        await operator.getByRole('heading', { name: 'Consulta de sucursal' }).waitFor();
        stage = 'deactivation-revocation';
        // Deactivations are confirmed in the shared Alert Dialog, not with a native confirm.
        await page.getByRole('button', { name: 'Desactivar usuario', exact: true }).click();
        await page.getByRole('alertdialog').getByRole('button', { name: 'Desactivar usuario', exact: true }).click();
        await page.getByText('Usuario desactivado.', { exact: true }).waitFor();
        await operator.getByRole('button', { name: 'Consultar datos actuales' }).click();
        await operator.getByRole('heading', { name: 'Iniciar sesión', exact: true }).waitFor();
        check(await login(operator, user, config.userPassword) === 401, 'Inactive account login was not rejected');
        await operator.getByRole('alert').waitFor();
        check(await operator.getByRole('heading', { name: 'Consulta de sucursal' }).count() === 0, 'Inactive user authenticated');
      } finally { await context.close(); }
      stage = 'branch-deactivation';
      await page.getByRole('tab', { name: 'Sucursales', exact: true }).click();
      await page.getByRole('button', { name: 'Editar ' + a, exact: true }).click();
      await page.getByRole('button', { name: 'Desactivar sucursal', exact: true }).click();
      await page.getByRole('alertdialog').getByRole('button', { name: 'Desactivar sucursal', exact: true }).click();
      await page.getByText('Sucursal desactivada. Las cuentas no fueron desactivadas.', { exact: true }).waitFor();
      stage = 'logout';
      await page.getByRole('link', { name: 'Mi sesión', exact: true }).click();
      await page.getByRole('button', { name: 'Cerrar sesión', exact: true }).click();
      await page.getByRole('heading', { name: 'Iniciar sesión', exact: true }).waitFor();
      check(await page.evaluate(() => localStorage.length + sessionStorage.length) === 0, 'Credentials/session persisted in web storage');
      return { status: 'passed', scenarios: 10 };
      } catch (error) { return { status: 'failed', stage, error: String(error) }; }
    }'''.replace("CONFIG", json.dumps(values), 1)
    path = directory / "exercise.js"
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w") as stream:
        stream.write(script)
    command = ["playwright-cli", "-s=" + session]
    try:
        subprocess.run(command + ["open", url], cwd=directory, capture_output=True, text=True, check=True, timeout=60)
        result = subprocess.run(command + ["run-code", "--filename=" + str(path)], cwd=directory,
                                capture_output=True, text=True, check=False, timeout=180)
        descriptor = os.open(directory / "result.log", os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, "w") as stream:
            stream.write(result.stdout + result.stderr)
        if not re.search(r'"status"\s*:\s*"passed"', result.stdout):
            stage = re.search(r'"stage"\s*:\s*"([a-z-]+)"', result.stdout)
            if stage:
                print("Etapa fallida:", stage.group(1))
            # Raw browser transcripts can contain test passwords; never print them.
            raise RuntimeError("La comprobación del navegador no produjo un resultado aprobado.")
        print("Navegador OK: 10 escenarios UI/API reales, cambio inicial, usuarios/sucursales, ámbito, revocación y red.")
        return 0
    except (OSError, RuntimeError, subprocess.SubprocessError):
        print("Fallo en la comprobación UI. Diagnósticos privados en el directorio temporal indicado; no publicarlos sin depuración.")
        return 1
    finally:
        try:
            subprocess.run(command + ["close"], cwd=directory, capture_output=True, timeout=30, check=False)
        except (OSError, subprocess.SubprocessError):
            pass
        print("Directorio temporal de prueba (retirar tras inspección):", directory)


if __name__ == "__main__":
    sys.exit(main())
