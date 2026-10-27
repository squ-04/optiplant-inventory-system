"""Exercise RF-0019/RF-0020 restock alerts in a real browser against an isolated, disposable Compose stack."""
import http.cookiejar
import json
import os
from pathlib import Path
import re
import secrets
import socket
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
SKU = "ALERTA-HELECHO"


def available_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


class Client:
    def __init__(self, base):
        self.base = base
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def request(self, path, method="GET", body=None):
        headers = {"Accept": "application/json", "Content-Type": "application/json"}
        if method != "GET":
            csrf = self.request("/auth/csrf")[1]
            headers[csrf["headerName"]] = csrf["token"]
            headers["Idempotency-Key"] = str(uuid.uuid4())
        request = urllib.request.Request(self.base + "/api/v1" + path, method=method, headers=headers,
                                         data=None if body is None else json.dumps(body).encode())
        try:
            with self.http.open(request, timeout=20) as response:
                raw = response.read()
                return response.status, json.loads(raw) if raw else None
        except urllib.error.HTTPError as error:
            return error.code, None


def expect(result, status, label):
    assert result[0] == status, f"{label}: HTTP {result[0]}, se esperaba {status}"
    return result[1]



# Each stage returns {status, stage, detail}; details never include credentials.
STAGES = {
    "operador-indicador-local": """
      await login(config.operator);
      await page.getByRole('heading', { name: 'Consulta de sucursal' }).waitFor();
      check(await page.getByRole('link', { name: /Alertas/ }).count() === 0, 'El operador ve la sección de alertas');
      check(await page.getByText('stock mínimo o por debajo').count() === 0, 'El operador ve el aviso de gerencia');
      await page.getByRole('link', { name: 'Catálogo', exact: true }).click();
      await page.getByRole('tab', { name: 'Mi sucursal' }).click();
      const cells = await page.getByRole('row').filter({ hasText: config.sku }).getByRole('cell').allInnerTexts();
      check(cells.includes('Requiere reabastecimiento'), 'Indicador ausente: ' + cells.join(' | '));
      await logout();
      return 'indicador «Requiere reabastecimiento» en el catálogo local; sin alertas de gerencia';""",
    "gerente-aviso-persistente": """
      await login(config.manager);
      const notice = page.getByText('1 producto en su sucursal está en su stock mínimo o por debajo.');
      await notice.waitFor({ timeout: 10000 });
      check(await page.getByRole('link', { name: 'Alertas de reabastecimiento, 1 activa' }).count() === 1, 'Navegación sin conteo accesible');
      await page.getByRole('link', { name: 'Catálogo', exact: true }).click();
      check(await notice.isVisible(), 'El aviso no persiste al cambiar de vista');
      await page.getByRole('link', { name: 'Ver alertas de reabastecimiento' }).click();
      await page.getByRole('heading', { name: 'Alertas de reabastecimiento', level: 2 }).waitFor();
      const cells = await alertRow().getByRole('cell').allInnerTexts();
      check(cells.some(c => c.startsWith('0,000')) && cells.some(c => c.startsWith('5,000')) && cells.some(c => c.startsWith('Activa')),
        'Fila inesperada: ' + cells.join(' | '));
      return 'aviso persistente, conteo accesible y alerta activa con disponible 0 y mínimo 5';""",
    "gerente-resolucion-automatica": """
      const start = Date.now();
      await page.getByText('Ningún producto está en su stock mínimo o por debajo.').waitFor({ timeout: 10000 });
      const waited = Date.now() - start;
      await page.getByLabel('Historial completo').check();
      await alertRow().filter({ hasText: 'Resuelta' }).waitFor({ timeout: 10000 });
      await page.getByRole('link', { name: 'Consultas', exact: true }).click();
      check(await page.getByText('stock mínimo o por debajo').count() === 0, 'El aviso sigue visible tras resolver');
      await logout();
      return 'alerta resuelta sin pulsar tras ' + waited + ' ms; conservada como «Resuelta» en el historial';""",
    "administrador-red-y-movil": """
      await login(config.admin);
      await page.getByText(/en la red (está|están) en su stock mínimo o por debajo/).waitFor({ timeout: 10000 });
      await page.getByRole('link', { name: 'Ver alertas de reabastecimiento' }).click();
      await alertRow().filter({ hasText: 'Activa' }).filter({ hasText: 'Sucursal Sur (SUR)' }).waitFor({ timeout: 10000 });
      await page.locator('#alerts-branch').selectOption({ label: 'SUR · Sucursal Sur' });
      await page.getByRole('heading', { name: 'Alertas activas de Sucursal Sur (SUR)' }).waitFor();
      await page.setViewportSize({ width: 390, height: 844 });
      await page.waitForTimeout(300);
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
      check(overflow <= 0, 'Desplazamiento horizontal del documento en móvil: ' + overflow + ' px');
      await page.screenshot({ path: config.screenshot, fullPage: true });
      return 'alerta reabierta por el nuevo mínimo visible en la red y filtrada por sucursal; sin desbordamiento a 390 px';""",
    "administrador-confirmacion": """
      // Deactivation goes through the shared Alert Dialog: only its action may reach the API.
      await page.setViewportSize({ width: 1280, height: 800 });
      const sent = [];
      page.on('request', request => { if (request.url().endsWith('/deactivate')) sent.push(request.url()); });
      await page.getByRole('link', { name: 'Administración', exact: true }).click();
      await page.getByRole('button', { name: 'Editar ' + config.disposableBranch, exact: true }).click();
      const trigger = page.getByRole('button', { name: 'Desactivar sucursal', exact: true });
      const dialog = page.getByRole('alertdialog');
      const branchRow = () => page.getByRole('row').filter({ hasText: config.disposableBranch });
      for (const dismiss of ['Escape', 'Cancelar']) {
        await trigger.click();
        await dialog.waitFor();
        check(await dialog.getByRole('heading', { name: '¿Desactivar la sucursal ' + config.disposableBranch + '?' }).count() === 1,
          'Diálogo sin el registro afectado');
        check(await page.evaluate(() => document.activeElement?.textContent?.trim()) === 'Cancelar', 'El foco inicial no está en Cancelar');
        if (dismiss === 'Escape') await page.keyboard.press('Escape');
        else await dialog.getByRole('button', { name: 'Cancelar', exact: true }).click();
        await dialog.waitFor({ state: 'detached' });
        // Radix restores focus on a macrotask after unmounting the dialog.
        await page.waitForFunction(() => document.activeElement?.textContent?.trim() === 'Desactivar sucursal', null, { timeout: 2000 })
          .catch(() => { throw new Error('El foco no volvió al botón tras ' + dismiss); });
        check(sent.length === 0, dismiss + ' envió la desactivación');
        check((await branchRow().allInnerTexts()).join(' ').includes('Activa'), 'La sucursal cambió tras ' + dismiss);
      }
      await trigger.click();
      await dialog.getByRole('button', { name: 'Desactivar sucursal', exact: true }).click();
      await page.getByText('Sucursal desactivada. Las cuentas no fueron desactivadas.', { exact: true }).waitFor();
      check(sent.length === 1, 'Se esperaba una sola desactivación; enviadas: ' + sent.length);
      await branchRow().filter({ hasText: 'Inactiva' }).waitFor();
      await logout();
      return 'Escape y Cancelar no envían la baja y devuelven el foco; la acción del diálogo desactiva una sola vez';""",
    "operador-catalogo-tras-movimiento": """
      // H1: the local catalog stays mounted while hidden and must show the stock left by a movement, without reloading.
      await login(config.operator);
      await page.evaluate(() => { window.__sameDocument = true; });
      const row = () => page.getByRole('row').filter({ hasText: config.sku });
      await page.getByRole('link', { name: 'Catálogo', exact: true }).click();
      await page.getByRole('tab', { name: 'Mi sucursal' }).click();
      await row().filter({ hasText: '6,000 unidad' }).first().waitFor();
      await page.getByRole('link', { name: 'Movimientos', exact: true }).click();
      await page.getByRole('button', { name: 'Nuevo movimiento', exact: true }).click();
      await page.getByLabel('Producto', { exact: true }).selectOption({ label: config.sku + ' Helecho de prueba: 6,000 unidad en existencia' });
      await page.getByLabel(/^Cantidad/).fill('2');
      await page.getByLabel('Motivo', { exact: true }).fill('Ingreso de prueba desde la interfaz');
      await page.getByRole('button', { name: 'Registrar movimiento', exact: true }).click();
      await page.getByText(/Existencias resultantes: 8,000 unidad/).waitFor();
      await page.getByRole('link', { name: 'Catálogo', exact: true }).click();
      await page.getByRole('tab', { name: 'Mi sucursal' }).click();
      const cells = async () => (await row().first().getByRole('cell').allInnerTexts()).slice(2, 4);
      const deadline = Date.now() + 5000;
      while (Date.now() < deadline && (await cells())[0] !== '8,000 unidad') await page.waitForTimeout(200);
      const shown = await cells();
      check(shown[0] === '8,000 unidad' && shown[1] === '8,000 unidad', 'Existencias locales desactualizadas: ' + shown.join(' | '));
      check(await page.evaluate(() => window.__sameDocument === true), 'La vista se recargó');
      await logout();
      return 'tras registrar +2 en Movimientos, «Mi sucursal» muestra 8,000 unidad sin recargar el navegador';""",
}


def stage_script(name, body, config):
    return """async page => {
      const config = CONFIG;
      const check = (condition, message) => { if (!condition) throw new Error(message); };
      const alertRow = () => page.locator('section[aria-labelledby="alerts-title"]').getByRole('row').filter({ hasText: config.sku });
      const login = async account => {
        await page.getByLabel('Código de usuario', { exact: true }).fill(account.code);
        await page.getByLabel('Contraseña', { exact: true }).fill(account.password);
        await page.getByRole('button', { name: 'Iniciar sesión', exact: true }).click();
        await page.getByRole('link', { name: 'Mi sesión', exact: true }).waitFor();
      };
      const logout = async () => {
        await page.getByRole('link', { name: 'Mi sesión', exact: true }).click();
        await page.getByRole('button', { name: 'Cerrar sesión' }).click();
        await page.getByRole('heading', { name: 'Iniciar sesión' }).waitFor();
      };
      try { return { status: 'passed', stage: STAGE, detail: await (async () => { BODY })() }; }
      catch (error) { return { status: 'failed', stage: STAGE, detail: String(error).slice(0, 300) }; }
    }""".replace("CONFIG", json.dumps(config), 1).replace("STAGE", json.dumps(name)).replace("BODY", body, 1)


def main():
    project = "optiplant-restock-" + secrets.token_hex(6)
    directory = Path(tempfile.mkdtemp(prefix="optiplant-restock-"))
    backend_port, frontend_port = available_port(), available_port()
    while frontend_port == backend_port:
        frontend_port = available_port()
    admin_password, admin_new, temporary, operator_password, manager_password = (secrets.token_urlsafe(30) for _ in range(5))
    values = {
        "POSTGRES_DB": "optiplant", "POSTGRES_USER": "optiplant_owner",
        "POSTGRES_PASSWORD": secrets.token_urlsafe(36), "APP_DB_PASSWORD": secrets.token_urlsafe(36),
        "APP_DB_MIGRATION_PASSWORD": secrets.token_urlsafe(36), "APP_ADMIN_CODE": "ADMIN",
        "APP_ADMIN_PASSWORD": admin_password, "APP_DEMO_DATA": "true",
        "BACKEND_PORT": str(backend_port), "FRONTEND_PORT": str(frontend_port),
    }
    env_path = directory / ".env"
    descriptor = os.open(env_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w") as stream:
        stream.write("".join(f"{key}={value}\n" for key, value in values.items()))
    # Do not let ambient COMPOSE_* or .env values select another project or credential.
    env = {key: value for key, value in os.environ.items() if not key.startswith("COMPOSE_") and key not in values}
    env["DOCKER_HOST"] = "unix:///var/run/docker.sock"
    env.pop("DOCKER_CONTEXT", None)
    command = ["docker", "compose", "--project-name", project, "--env-file", str(env_path), "-f", str(ROOT / "compose.yaml")]
    browser = ["playwright-cli", "-s=" + project]

    def compose(*args, check=True, timeout=900):
        return subprocess.run(command + list(args), cwd=ROOT, env=env, capture_output=True, text=True,
                              check=check, timeout=timeout)

    config = {"sku": SKU, "admin": {"code": "ADMIN", "password": admin_new},
              "operator": {"code": "OP-SUR", "password": operator_password},
              "manager": {"code": "GER-SUR", "password": manager_password},
              "disposableBranch": "BAJA-PRUEBA",
              "screenshot": str(directory / "alertas-movil.png")}

    def stage(name):
        path = directory / f"{name}.js"
        descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, "w") as stream:
            stream.write(stage_script(name, STAGES[name], config))
        result = subprocess.run(browser + ["run-code", "--filename=" + str(path)], cwd=directory,
                                capture_output=True, text=True, check=False, timeout=180)
        with open(directory / "result.log", "a", encoding="utf-8") as stream:
            stream.write(result.stdout + result.stderr)
        # Raw transcripts echo the stage source, which contains test passwords; print only the structured result.
        detail = re.search(r'"detail"\s*:\s*"((?:[^"\\]|\\.)*)"', result.stdout)
        passed = re.search(r'"status"\s*:\s*"passed"', result.stdout) is not None
        print(("OK    " if passed else "FALLO ") + name + (": " + json.loads(f'"{detail.group(1)}"') if detail else ""), flush=True)
        if not passed:
            raise RuntimeError(f"Etapa fallida: {name}")

    def local_user(admin, base, code, name, role, branch, password):
        expect(admin.request("/users", "POST", {"code": code, "fullName": name, "role": role,
                                                "branchExternalId": branch, "temporaryPassword": temporary}), 201, "alta " + code)
        user = Client(base)
        expect(user.request("/auth/login", "POST", {"code": code, "password": temporary}), 200, "acceso " + code)
        expect(user.request("/auth/password", "POST", {"currentPassword": temporary, "newPassword": password}), 204, "cambio " + code)
        user = Client(base)
        expect(user.request("/auth/login", "POST", {"code": code, "password": password}), 200, "acceso definitivo " + code)
        return user

    try:
        compose("up", "--build", "--wait", "--wait-timeout", "240")
        base = f"http://localhost:{backend_port}"
        admin = Client(base)
        expect(admin.request("/auth/login", "POST", {"code": "ADMIN", "password": admin_password}), 200, "acceso inicial")
        expect(admin.request("/auth/password", "POST", {"currentPassword": admin_password, "newPassword": admin_new}), 204, "cambio inicial")
        admin = Client(base)
        expect(admin.request("/auth/login", "POST", {"code": "ADMIN", "password": admin_new}), 200, "acceso administrador")
        branch = expect(admin.request("/branches", "POST", {"code": "SUR", "name": "Sucursal Sur"}), 201, "alta de sucursal")["externalId"]
        # Without stock or documents, so the confirmation stage can deactivate it.
        expect(admin.request("/branches", "POST", {"code": config["disposableBranch"], "name": "Sucursal para baja de prueba"}),
               201, "alta de sucursal desechable")
        operator = local_user(admin, base, "OP-SUR", "Operador Sur", "OPERATOR", branch, operator_password)
        local_user(admin, base, "GER-SUR", "Gerente Sur", "MANAGER", branch, manager_password)
        # RF-0019: explicit minimum 5 at enablement; with zero stock the condition already holds.
        product = expect(admin.request("/products", "POST", {"sku": SKU, "name": "Helecho de prueba", "description": None,
                                                             "baseUnitName": "unidad", "baseDivisible": False,
                                                             "branches": [{"branchExternalId": branch, "minimumQuantity": "5"}]}),
                         201, "alta de producto")["externalId"]

        subprocess.run(browser + ["open", f"http://localhost:{frontend_port}"], cwd=directory, capture_output=True,
                       text=True, check=True, timeout=60)
        stage("operador-indicador-local")
        stage("gerente-aviso-persistente")
        # A real manual entry (RF-0016) raises stock to 6, strictly above the minimum, while the manager keeps the view open.
        expect(operator.request("/inventory/movements", "POST", {"kind": "MANUAL_ENTRY", "product": product, "unit": "unidad",
                                                                 "quantity": "6", "unitCost": "1000", "reason": "Reposición de prueba"}),
               201, "ingreso manual")
        stage("gerente-resolucion-automatica")
        # RF-0019-CA-02: raising the minimum to the current stock reopens the alert without another parameter.
        expect(admin.request(f"/products/{product}/branches/{branch}/minimum", "POST", {"minimumQuantity": "6"}), 200, "nuevo mínimo")
        stage("administrador-red-y-movil")
        stage("administrador-confirmacion")
        stage("operador-catalogo-tras-movimiento")
        print(f"Navegador OK: {len(STAGES)} escenarios de stock mínimo y alertas de reabastecimiento (RF-0019/RF-0020).")
        return 0
    except (AssertionError, OSError, RuntimeError, ValueError, KeyError, TypeError, subprocess.SubprocessError) as error:
        print(f"Comprobación FALLÓ: {error}", file=sys.stderr)
        print("Diagnósticos privados en result.log del directorio temporal; no publicarlos sin depuración.", file=sys.stderr)
        return 1
    finally:
        try:
            subprocess.run(browser + ["close"], cwd=directory, capture_output=True, timeout=30, check=False)
        except (OSError, subprocess.SubprocessError):
            pass
        # Only the new, uniquely named test project's disposable volume is removed.
        result = compose("down", "--volumes", "--remove-orphans", check=False, timeout=180)
        print("Directorio temporal de prueba (retirar tras inspección):", directory)
        if result.returncode:
            print(f"Limpieza pendiente del proyecto aislado {project}", file=sys.stderr)


if __name__ == "__main__":
    sys.exit(main())
