"""Exercise RF-0030 price lists in a real browser against an isolated, disposable Compose stack."""
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
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
SKU = "PRECIO-HELECHO"


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
    "administrador-crea-listas-y-precios": """
      await login(config.admin);
      await page.getByRole('link', { name: 'Precios', exact: true }).click();
      await page.getByRole('heading', { name: 'Listas de precios', level: 2 }).waitFor();
      for (const [code, name] of [['DETAL', 'Detal'], ['MAYOR', 'Mayorista']]) {
        await page.getByRole('button', { name: 'Nueva lista', exact: true }).click();
        // The editor moves focus to its first field on the next frame; filling earlier would type into the wrong field.
        await page.waitForFunction(() => document.activeElement?.closest('#price-list-editor') !== null);
        await page.getByLabel('Código', { exact: true }).fill(code);
        await page.getByLabel('Nombre', { exact: true }).fill(name);
        await page.getByRole('button', { name: 'Crear lista', exact: true }).click();
        await page.getByText('Lista ' + code + ' creada.', { exact: true }).waitFor();
        await page.getByRole('heading', { name: 'Precios de ' + name }).waitFor();
        await page.getByText('Esta lista todavía no tiene precios. Defina el primero con el formulario.').waitFor();
      }
      const define = async (unit, price) => {
        await page.locator('#list-price-product').selectOption({ label: config.sku + ' · Helecho de prueba' });
        await page.locator('#list-price-unit').selectOption({ label: unit });
        await page.getByLabel('Precio por unidad (COP)').fill(price);
        await page.getByRole('button', { name: 'Guardar precio', exact: true }).click();
      };
      await define('caja', '99999,5');
      await page.getByText('Precio definido: Helecho de prueba, caja, 99.999,50 COP.').waitFor();
      await page.getByRole('button', { name: 'Ver precios de DETAL' }).click();
      await page.getByRole('heading', { name: 'Precios de Detal' }).waitFor();
      await define('caja', '120000');
      await page.getByText('Precio definido: Helecho de prueba, caja, 120.000,00 COP.').waitFor();
      // A dot reads as a thousands separator in the interface: the browser rejects it before sending.
      const sent = [];
      page.on('request', request => { if (request.url().endsWith('/prices')) sent.push(request.url()); });
      await define('unidad (base)', '1.5');
      await page.waitForTimeout(300);
      check(sent.length === 0, 'Se envió un precio con punto decimal');
      // The browser explains the field's own format instead of its generic message.
      const field = page.getByLabel('Precio por unidad (COP)');
      const hint = await field.getAttribute('title');
      check(await field.evaluate(element => element.validationMessage) === hint, 'Mensaje de formato genérico en el precio');
      // A price prefilled by «Cambiar precio», without typing, is saved at the first attempt: no stale hint blocks it.
      await page.getByRole('button', { name: 'Cambiar precio de ' + config.sku + ' en caja' }).click();
      await page.getByRole('button', { name: 'Guardar precio', exact: true }).click();
      await page.getByText('Precio actualizado: Helecho de prueba, caja, 120.000,00 COP.').waitFor();
      await define('unidad (base)', '11000');
      await page.getByText('Precio definido: Helecho de prueba, unidad, 11.000,00 COP.').waitFor();
      // Replacing keeps a single row per unit and prefills the current price.
      await page.getByRole('button', { name: 'Cambiar precio de ' + config.sku + ' en caja' }).click();
      check(await page.getByLabel('Precio por unidad (COP)').inputValue() === '120000', 'El precio actual no se precargó');
      await page.getByLabel('Precio por unidad (COP)').fill('125000');
      await page.getByRole('button', { name: 'Guardar precio', exact: true }).click();
      await page.getByText('Precio actualizado: Helecho de prueba, caja, 125.000,00 COP.').waitFor();
      const rows = await page.getByRole('region', { name: 'Tabla de precios de Detal' }).getByRole('row').allInnerTexts();
      check(rows.length === 3 && rows[1].includes('unidad') && rows[1].includes('11.000') && rows[2].includes('125.000'),
        'Filas inesperadas: ' + rows.join(' / '));
      await page.getByRole('button', { name: 'Ver precios de MAYOR' }).click();
      await page.getByRole('region', { name: 'Tabla de precios de Mayorista' }).waitFor();
      const other = await page.getByRole('region', { name: 'Tabla de precios de Mayorista' }).getByRole('row').allInnerTexts();
      check(other.length === 2 && other[1].includes('99.999,5'), 'La otra lista cambió: ' + other.join(' / '));
      return 'dos listas con precios distintos para la misma caja (125.000 y 99.999,5); un punto decimal no se envía '
        + 'y el campo explica su formato; el precio precargado se guarda al primer intento; el reemplazo no duplica filas';""",
    "administrador-desactiva-y-movil": """
      await page.getByRole('button', { name: 'Editar MAYOR', exact: true }).click();
      await page.getByLabel('Lista activa: disponible para nuevas ventas').uncheck();
      await page.getByRole('button', { name: 'Guardar cambios', exact: true }).click();
      await page.getByText('Lista MAYOR actualizada.', { exact: true }).waitFor();
      await page.getByText('Inactiva: no se ofrece en nuevas ventas.').waitFor();
      check((await page.getByRole('region', { name: 'Tabla de precios de Mayorista' }).getByRole('row').allInnerTexts()).length === 2,
        'La lista desactivada perdió sus precios');
      await page.setViewportSize({ width: 390, height: 844 });
      await page.waitForTimeout(300);
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
      check(overflow <= 0, 'Desplazamiento horizontal del documento en móvil: ' + overflow + ' px');
      await page.screenshot({ path: config.screenshot, fullPage: true });
      await page.setViewportSize({ width: 1280, height: 800 });
      await logout();
      return 'lista desactivada conservando sus precios; sin desbordamiento a 390 px';""",
    "operador-consulta-sin-mantener": """
      await login(config.operator);
      await page.getByRole('link', { name: 'Precios', exact: true }).click();
      await page.getByRole('heading', { name: 'Precios de Detal' }).waitFor();
      const lists = await page.getByRole('region', { name: 'Tabla de listas de precios' }).getByRole('row').allInnerTexts();
      check(lists.length === 2 && lists[1].includes('DETAL'), 'Listas visibles inesperadas: ' + lists.join(' / '));
      const rows = await page.getByRole('region', { name: 'Tabla de precios de Detal' }).getByRole('row').allInnerTexts();
      check(rows.some(r => r.includes('125.000')), 'Precio no visible para el operador');
      check(await page.getByRole('button', { name: /Nueva lista|Editar|Cambiar precio|Guardar precio/ }).count() === 0,
        'El operador ve acciones de mantenimiento');
      await logout();
      return 'el operador ve solo la lista activa con sus precios y ninguna acción de mantenimiento';""",
}


def stage_script(name, body, config):
    return """async page => {
      const config = CONFIG;
      const check = (condition, message) => { if (!condition) throw new Error(message); };
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
      catch (error) {
        await page.screenshot({ path: config.screenshot.replace('.png', '-fallo.png'), fullPage: true }).catch(() => {});
        return { status: 'failed', stage: STAGE, detail: String(error).slice(0, 300) };
      }
    }""".replace("CONFIG", json.dumps(config), 1).replace("STAGE", json.dumps(name)).replace("BODY", body, 1)


def main():
    project = "optiplant-prices-" + secrets.token_hex(6)
    directory = Path(tempfile.mkdtemp(prefix="optiplant-prices-"))
    backend_port, frontend_port = available_port(), available_port()
    while frontend_port == backend_port:
        frontend_port = available_port()
    admin_password, admin_new, temporary, operator_password = (secrets.token_urlsafe(30) for _ in range(4))
    values = {
        "POSTGRES_DB": "optiplant", "POSTGRES_USER": "optiplant_owner",
        "POSTGRES_PASSWORD": secrets.token_urlsafe(36), "APP_DB_PASSWORD": secrets.token_urlsafe(36),
        "APP_DB_MIGRATION_PASSWORD": secrets.token_urlsafe(36), "APP_ADMIN_CODE": "ADMIN",
        "APP_ADMIN_PASSWORD": admin_password, "APP_DEMO_DATA": "false",
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
              "screenshot": str(directory / "precios-movil.png")}

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

    try:
        compose("up", "--build", "--wait", "--wait-timeout", "240")
        base = f"http://localhost:{backend_port}"
        admin = Client(base)
        expect(admin.request("/auth/login", "POST", {"code": "ADMIN", "password": admin_password}), 200, "acceso inicial")
        expect(admin.request("/auth/password", "POST", {"currentPassword": admin_password, "newPassword": admin_new}), 204, "cambio inicial")
        admin = Client(base)
        expect(admin.request("/auth/login", "POST", {"code": "ADMIN", "password": admin_new}), 200, "acceso administrador")
        branch = expect(admin.request("/branches", "POST", {"code": "SUR", "name": "Sucursal Sur"}), 201, "alta de sucursal")["externalId"]
        expect(admin.request("/users", "POST", {"code": "OP-SUR", "fullName": "Operador Sur", "role": "OPERATOR",
                                                "branchExternalId": branch, "temporaryPassword": temporary}), 201, "alta operador")
        operator = Client(base)
        expect(operator.request("/auth/login", "POST", {"code": "OP-SUR", "password": temporary}), 200, "acceso operador")
        expect(operator.request("/auth/password", "POST", {"currentPassword": temporary, "newPassword": operator_password}),
               204, "cambio operador")
        product = expect(admin.request("/products", "POST", {"sku": SKU, "name": "Helecho de prueba", "description": None,
                                                             "baseUnitName": "unidad", "baseDivisible": False, "branches": []}),
                         201, "alta de producto")["externalId"]
        expect(admin.request(f"/products/{product}/units", "POST", {"name": "caja", "factor": "12", "divisible": False}),
               200, "alta de unidad caja")

        subprocess.run(browser + ["open", f"http://localhost:{frontend_port}"], cwd=directory, capture_output=True,
                       text=True, check=True, timeout=60)
        for name in STAGES:
            stage(name)
        print(f"Navegador OK: {len(STAGES)} escenarios de listas de precios (RF-0030).")
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
        # Only the new, uniquely named test project's disposable volume and locally built images are removed.
        result = compose("down", "--volumes", "--rmi", "local", "--remove-orphans", check=False, timeout=180)
        print("Directorio temporal de prueba (retirar tras inspección):", directory)
        if result.returncode:
            print(f"Limpieza pendiente del proyecto aislado {project}", file=sys.stderr)


if __name__ == "__main__":
    sys.exit(main())
