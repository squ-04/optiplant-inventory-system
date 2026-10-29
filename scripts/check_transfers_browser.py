"""Exercise RF-0032–RF-0034 transfers in a real browser against an isolated, disposable Compose stack."""
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
SKU = "TRANS-HELECHO"


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
    "destino-solicita": """
      await login(config.destination);
      await page.getByRole('link', { name: 'Transferencias', exact: true }).click();
      await page.getByRole('heading', { name: 'Transferencias', level: 2 }).waitFor();
      await page.getByText(/No hay transferencias que cumplan estos filtros/).waitFor();
      await page.getByRole('button', { name: 'Solicitar transferencia', exact: true }).click();
      await page.locator('#transfer-origin').selectOption({ label: 'NORTE · Sucursal Norte' });
      const line = page.locator('#transfer-editor fieldset.purchase-line').first();
      await line.locator('select').first().selectOption({ label: config.sku + ' Helecho de prueba' });
      await line.locator('select').nth(1).selectOption({ label: 'caja (12 unidad)' });
      await line.getByLabel('Cantidad').fill('2');
      await page.locator('#transfer-editor').getByRole('button', { name: 'Solicitar transferencia', exact: true }).click();
      await page.getByText(/^Transferencia TR-\\d+ solicitada a Sucursal Norte con 1 producto/).waitFor();
      await page.getByRole('heading', { name: /Transferencia TR-/ }).waitFor();
      await page.getByText('Sin preparar').waitFor();
      check(await page.getByRole('columnheader', { name: 'Disponible en origen' }).count() === 0,
        'El destino ve las existencias del origen');
      await logout();
      return 'el operador de destino solicita 2 cajas a Sucursal Norte; queda «Solicitada», sin preparar y sin ver el stock del origen';""",
    "origen-prepara": """
      await login(config.originOperator);
      await page.getByRole('link', { name: 'Transferencias', exact: true }).click();
      await page.getByRole('button', { name: 'Ver detalle de TR-000001', exact: true }).click();
      await page.getByRole('cell', { name: '30,000 unidad' }).waitFor();
      await page.getByRole('button', { name: 'Preparar envío', exact: true }).click();
      const quantity = page.getByLabel(config.sku + ' Helecho de prueba (caja)');
      await quantity.fill('3');
      await page.getByText('No puede superar lo solicitado: 2,000 caja.').waitFor();
      check(await page.getByRole('button', { name: 'Guardar preparación' }).isDisabled(), 'Se puede guardar más de lo solicitado');
      // The same unit rule as the server: a box is indivisible.
      await quantity.fill('1,5');
      await page.getByText('La unidad no admite fracciones: la cantidad debe ser entera.').waitFor();
      check(await page.getByRole('button', { name: 'Guardar preparación' }).isDisabled(), 'Se puede guardar una fracción de caja');
      await quantity.fill('1');
      await page.getByRole('button', { name: 'Guardar preparación', exact: true }).click();
      await page.getByText(/^Preparación de TR-000001 guardada/).waitFor();
      await page.getByText('Menos de lo solicitado').waitFor();
      check(await page.getByRole('button', { name: 'Aprobar transferencia' }).count() === 0, 'El operador ve la aprobación');
      await logout();
      return 'el operador de origen ve 30 unidades disponibles, no puede preparar 3 de 2 cajas ni 1,5 cajas y prepara 1 caja';""",
    "gerente-aprueba": """
      await login(config.originManager);
      await page.getByRole('link', { name: 'Transferencias', exact: true }).click();
      await page.getByRole('button', { name: 'Ver detalle de TR-000001', exact: true }).click();
      await page.getByRole('button', { name: 'Aprobar transferencia', exact: true }).click();
      await page.getByRole('alertdialog').getByRole('button', { name: 'Aprobar transferencia', exact: true }).click();
      await page.getByText('TR-000001 quedó aprobada. Queda pendiente el despacho del origen.').waitFor();
      await page.getByText(/Gerente Norte \\(GER-NORTE\\)/).first().waitFor();
      await page.getByRole('button', { name: 'Cancelar transferencia', exact: true }).waitFor();
      await page.setViewportSize({ width: 390, height: 844 });
      await page.waitForTimeout(300);
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
      check(overflow <= 0, 'Desplazamiento horizontal del documento en móvil: ' + overflow + ' px');
      await page.screenshot({ path: config.screenshot, fullPage: true });
      await page.setViewportSize({ width: 1280, height: 800 });
      await logout();
      return 'el gerente de origen aprueba; la aprobación muestra responsable y solo él conserva la cancelación; sin desbordamiento a 390 px';""",
    "destino-ve-aprobacion": """
      await login(config.destination);
      await page.getByRole('link', { name: 'Transferencias', exact: true }).click();
      await page.getByRole('button', { name: 'Ver detalle de TR-000001', exact: true }).click();
      await page.getByText('Menos de lo solicitado').waitFor();
      await page.getByText(/Gerente Norte \\(GER-NORTE\\)/).first().waitFor();
      check(await page.getByRole('button', { name: 'Cancelar transferencia' }).count() === 0,
        'El destino puede cancelar una transferencia aprobada');
      await logout();
      return 'el destino distingue 2 cajas solicitadas de 1 caja a enviar y ve la aprobación, sin acción de cancelar';""",
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
    project = "optiplant-transfers-" + secrets.token_hex(6)
    directory = Path(tempfile.mkdtemp(prefix="optiplant-transfers-"))
    backend_port, frontend_port = available_port(), available_port()
    while frontend_port == backend_port:
        frontend_port = available_port()
    admin_password, admin_new, temporary, origin_password, manager_password, destination_password = (
        secrets.token_urlsafe(30) for _ in range(6))
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

    config = {"sku": SKU, "originOperator": {"code": "OP-NORTE", "password": origin_password},
              "originManager": {"code": "GER-NORTE", "password": manager_password},
              "destination": {"code": "OP-SUR", "password": destination_password},
              "screenshot": str(directory / "transferencias-movil.png")}

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
        north = expect(admin.request("/branches", "POST", {"code": "NORTE", "name": "Sucursal Norte"}), 201, "alta origen")["externalId"]
        south = expect(admin.request("/branches", "POST", {"code": "SUR", "name": "Sucursal Sur"}), 201, "alta destino")["externalId"]
        clients = {}
        for code, name, role, branch, password in (("OP-NORTE", "Operador Norte", "OPERATOR", north, origin_password),
                                                   ("GER-NORTE", "Gerente Norte", "MANAGER", north, manager_password),
                                                   ("OP-SUR", "Operador Sur", "OPERATOR", south, destination_password)):
            expect(admin.request("/users", "POST", {"code": code, "fullName": name, "role": role, "branchExternalId": branch,
                                                    "temporaryPassword": temporary}), 201, "alta " + code)
            user = Client(base)
            expect(user.request("/auth/login", "POST", {"code": code, "password": temporary}), 200, "acceso " + code)
            expect(user.request("/auth/password", "POST", {"currentPassword": temporary, "newPassword": password}), 204, "cambio " + code)
            clients[code] = user
        product = expect(admin.request("/products", "POST", {"sku": SKU, "name": "Helecho de prueba", "description": None,
                                                             "baseUnitName": "unidad", "baseDivisible": False,
                                                             "branches": [{"branchExternalId": north, "minimumQuantity": "0"},
                                                                          {"branchExternalId": south, "minimumQuantity": "0"}]}),
                         201, "alta de producto")["externalId"]
        expect(admin.request(f"/products/{product}/units", "POST", {"name": "caja", "factor": "12", "divisible": False}),
               200, "alta de unidad caja")
        origin = Client(base)
        expect(origin.request("/auth/login", "POST", {"code": "OP-NORTE", "password": origin_password}), 200, "acceso operador origen")
        expect(origin.request("/inventory/movements", "POST", {"kind": "MANUAL_ENTRY", "product": product, "unit": "unidad",
                                                               "quantity": "30", "unitCost": "500", "reason": "Inventario inicial"}),
               201, "ingreso inicial en origen")

        subprocess.run(browser + ["open", f"http://localhost:{frontend_port}"], cwd=directory, capture_output=True,
                       text=True, check=True, timeout=60)
        for name in STAGES:
            stage(name)
        print(f"Navegador OK: {len(STAGES)} escenarios de transferencias (RF-0032–RF-0034).")
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
