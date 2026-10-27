"""Exercise RF-0014/RF-0015 inventory polling in a real browser against an isolated, disposable Compose stack."""
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

ROOT = Path(__file__).resolve().parents[1]
DEMO_BRANCH_LABEL = "DEMO-BOG · Demostración Bogotá"


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
    "consultar-otra-sucursal": """
      await page.getByLabel('Código de usuario', { exact: true }).fill(config.code);
      await page.getByLabel('Contraseña', { exact: true }).fill(config.password);
      await page.getByRole('button', { name: 'Iniciar sesión', exact: true }).click();
      await page.getByRole('heading', { name: 'Consulta de sucursal' }).waitFor();
      await page.getByLabel('Sucursal', { exact: true }).selectOption({ label: config.branch });
      await page.getByRole('button', { name: 'Consultar sucursal' }).click();
      await page.getByRole('heading', { name: 'Inventario de Demostración Bogotá (DEMO-BOG)' }).waitFor();
      const cells = await row().getByRole('cell').allInnerTexts();
      check(cells[2].startsWith('8,000') && cells[3].startsWith('8,000'), 'Existencias inesperadas: ' + cells.join(' | '));
      check(await page.getByText('estas existencias pertenecen a otra sucursal').count() === 1, 'Falta el aviso de solo lectura');
      check(!/costo|valor/i.test(await view().locator('table').innerText()), 'Se muestran costos o valoración');
      const polls = await count(6100);
      check(polls >= 2 && polls <= 4, 'Lecturas automáticas en 6,1 s: ' + polls);
      return 'existencias de DEMO-BOG sin costos; ' + polls + ' lecturas automáticas en 6,1 s';""",
    "cambio-visible-sin-pulsar": """
      const start = Date.now();
      await row().getByRole('cell').nth(2).filter({ hasText: /^5,000/ }).waitFor({ timeout: 10000 });
      return 'saldo actualizado visible tras ' + (Date.now() - start) + ' ms de espera en la página';""",
    "backend-detenido": """
      await alert().waitFor({ timeout: 10000 });
      const text = await alert().innerText();
      check(/pueden\\s+estar desactualizados/.test(text) && /no confirma ninguna operación/.test(text), 'Advertencia incompleta');
      check((await row().getByRole('cell').nth(2).innerText()).startsWith('5,000'), 'Se perdió la última lectura');
      return 'advertencia visible y última lectura conservada';""",
    "backend-recuperado": """
      await alert().waitFor({ state: 'detached', timeout: 15000 });
      await page.getByText('Se actualiza automáticamente').waitFor({ timeout: 10000 });
      return 'advertencia retirada tras la recuperación';""",
    "base-de-datos-detenida": """
      const start = Date.now();
      await alert().waitFor({ timeout: 15000 });
      check(await page.getByText('Se actualiza automáticamente').count() === 0, 'Estado en línea durante la caída');
      await page.waitForRequest(r => r.url().includes('/inventory'), { timeout: 8000 });
      return 'advertencia tras ' + (Date.now() - start) + ' ms; los reintentos continúan';""",
    "base-de-datos-recuperada": """
      await alert().waitFor({ state: 'detached', timeout: 60000 });
      await page.getByText('Se actualiza automáticamente').waitFor({ timeout: 10000 });
      return 'lecturas automáticas restablecidas';""",
    "pausa-fuera-de-la-vista": """
      await page.getByRole('link', { name: 'Catálogo', exact: true }).click();
      await page.waitForTimeout(500);
      const hidden = await count(5000);
      check(hidden === 0, 'Lecturas con la vista oculta: ' + hidden);
      const back = page.waitForRequest(r => r.url().includes('/inventory'), { timeout: 1500 });
      await page.getByRole('link', { name: 'Consultas', exact: true }).click();
      await back;
      return 'sin lecturas fuera de la vista; nueva lectura al volver';""",
}


def stage_script(name, body, config):
    return """async page => {
      const config = CONFIG;
      const check = (condition, message) => { if (!condition) throw new Error(message); };
      const view = () => page.locator('section[aria-labelledby="visibility-title"]');
      const row = () => view().getByRole('row').filter({ hasText: 'DEMO-PLANT' });
      const alert = () => page.getByRole('alert').filter({ hasText: 'Sin comunicación con el servidor' });
      const count = async ms => {
        let seen = 0;
        const on = r => { if (r.url().includes('/inventory')) seen += 1; };
        page.on('request', on); await page.waitForTimeout(ms); page.off('request', on);
        return seen;
      };
      try { return { status: 'passed', stage: STAGE, detail: await (async () => { BODY })() }; }
      catch (error) { return { status: 'failed', stage: STAGE, detail: String(error).slice(0, 300) }; }
    }""".replace("CONFIG", json.dumps(config), 1).replace("STAGE", json.dumps(name)).replace("BODY", body, 1)


def main():
    project = "optiplant-inventory-" + secrets.token_hex(6)
    directory = Path(tempfile.mkdtemp(prefix="optiplant-inventory-"))
    backend_port, frontend_port = available_port(), available_port()
    while frontend_port == backend_port:
        frontend_port = available_port()
    admin_password, admin_new, temporary, operator_password = (secrets.token_urlsafe(30) for _ in range(4))
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

    def wait_backend():
        # `compose up --wait` would also recreate the frontend dev server and reload the page under test.
        for _ in range(120):
            try:
                with urllib.request.urlopen(f"http://localhost:{backend_port}/actuator/health", timeout=3) as response:
                    if response.status == 200:
                        return
            except OSError:
                pass
            time.sleep(1)
        raise RuntimeError("El backend no recuperó la salud a tiempo.")

    config = {"code": "OP-SUR", "password": operator_password, "branch": DEMO_BRANCH_LABEL}

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
        admin = Client(f"http://localhost:{backend_port}")
        expect(admin.request("/auth/login", "POST", {"code": "ADMIN", "password": admin_password}), 200, "acceso inicial")
        expect(admin.request("/auth/password", "POST", {"currentPassword": admin_password, "newPassword": admin_new}), 204, "cambio inicial")
        admin = Client(f"http://localhost:{backend_port}")
        expect(admin.request("/auth/login", "POST", {"code": "ADMIN", "password": admin_new}), 200, "acceso administrador")
        branch = expect(admin.request("/branches", "POST", {"code": "SUR", "name": "Sucursal Sur"}), 201, "alta de sucursal")
        expect(admin.request("/users", "POST", {"code": "OP-SUR", "fullName": "Operador Sur", "role": "OPERATOR",
                                                 "branchExternalId": branch["externalId"], "temporaryPassword": temporary}),
               201, "alta de operador")
        operator = Client(f"http://localhost:{backend_port}")
        expect(operator.request("/auth/login", "POST", {"code": "OP-SUR", "password": temporary}), 200, "acceso operador")
        expect(operator.request("/auth/password", "POST", {"currentPassword": temporary, "newPassword": operator_password}),
               204, "cambio inicial del operador")

        subprocess.run(browser + ["open", f"http://localhost:{frontend_port}"], cwd=directory, capture_output=True,
                       text=True, check=True, timeout=60)
        stage("consultar-otra-sucursal")
        # Movement commands (RF-0016/RF-0017) do not exist yet: a confirmed change is written directly in this disposable database.
        compose("exec", "-T", "postgres", "sh", "-ec", 'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "$1"', "sh",
                "UPDATE inventory_balance SET physical_qty=5,inventory_value=50 WHERE branch_id=(SELECT id FROM identity_branch "
                "WHERE code='DEMO-BOG') AND product_id=(SELECT id FROM catalog_product WHERE sku='DEMO-PLANT')", timeout=60)
        stage("cambio-visible-sin-pulsar")
        compose("stop", "backend", timeout=120)
        stage("backend-detenido")
        compose("start", "backend", timeout=120)
        wait_backend()
        stage("backend-recuperado")
        compose("stop", "postgres", timeout=120)
        stage("base-de-datos-detenida")
        compose("start", "postgres", timeout=120)
        stage("base-de-datos-recuperada")
        stage("pausa-fuera-de-la-vista")
        print(f"Navegador OK: {len(STAGES)} escenarios de inventario entre sucursales y actualización automática (RF-0014/RF-0015).")
        print("La espera observada no es la medición formal de RNF-REN-01 (pendiente).")
        return 0
    except (AssertionError, OSError, RuntimeError, ValueError, KeyError, subprocess.SubprocessError) as error:
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
