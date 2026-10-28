"""Exercise RF-0027–RF-0029 and RF-0031 sales in a real browser against an isolated, disposable Compose stack."""
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
SKU = "VENTA-HELECHO"


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
    "operador-confirma-venta": """
      await login(config.operator);
      await page.getByRole('link', { name: 'Ventas', exact: true }).click();
      await page.getByRole('heading', { name: 'Ventas', level: 2 }).waitFor();
      await page.getByText('No hay ventas confirmadas. Use «Nueva venta» para registrar la primera.').waitFor();
      await page.getByRole('button', { name: 'Nueva venta', exact: true }).click();
      await page.locator('#sale-price-list').selectOption({ label: 'DETAL · Detal' });
      const line = page.locator('#sale-editor fieldset.purchase-line').first();
      await line.locator('select').first().selectOption({ label: config.sku + ' Helecho de prueba' });
      await line.getByLabel('Cantidad').fill('3');
      await line.getByLabel('Descuento %').fill('10');
      await line.getByText('Neto 900,00').waitFor();
      await line.getByText('2.700,00').waitFor();
      await line.getByText('Disponible: 10,000 unidad.').waitFor();
      // A second line of the same product in boxes exceeds the available stock: the form warns before confirming.
      await page.getByRole('button', { name: 'Agregar producto', exact: true }).click();
      const box = page.locator('#sale-editor fieldset.purchase-line').nth(1);
      await box.locator('select').first().selectOption({ label: config.sku + ' Helecho de prueba' });
      await box.locator('select').nth(1).selectOption({ label: 'caja (12 unidad)' });
      await box.getByLabel('Cantidad').fill('1');
      await page.getByText(/superan las existencias mostradas/).first().waitFor();
      await page.getByRole('button', { name: 'Quitar producto 2' }).click();
      await page.getByText('2.700,00 COP').waitFor();
      await page.getByRole('button', { name: 'Confirmar venta', exact: true }).click();
      await page.getByRole('alertdialog').getByRole('button', { name: 'Confirmar venta', exact: true }).click();
      await page.getByText(/^Venta VT-\\d+ confirmada por 2\\.700,00 COP/).waitFor();
      await page.getByRole('heading', { name: /Comprobante de venta VT-/ }).waitFor();
      const rows = await page.getByRole('region', { name: /Productos de VT-/ }).getByRole('row').allInnerTexts();
      check(rows.length === 2 && rows[1].includes('1.000') && rows[1].includes('10 %') && rows[1].includes('900')
        && rows[1].includes('2.700,00'), 'Comprobante inesperado: ' + rows.join(' / '));
      return 'venta de 3 unidades a 1.000 con 10 % (neto 900, total 2.700,00); aviso de existencias insuficientes antes de '
        + 'confirmar; comprobante con precio de lista, descuento y neto';""",
    "operador-rechazo-sin-efectos": """
      await page.getByRole('button', { name: 'Nueva venta', exact: true }).click();
      const line = page.locator('#sale-editor fieldset.purchase-line').first();
      await line.locator('select').first().selectOption({ label: config.sku + ' Helecho de prueba' });
      await line.getByText('Disponible: 7,000 unidad.').waitFor();
      await line.getByLabel('Cantidad').fill('8');
      await page.getByRole('button', { name: 'Confirmar venta', exact: true }).click();
      await page.getByRole('alertdialog').getByRole('button', { name: 'Confirmar venta', exact: true }).click();
      await page.locator('#sale-error').getByText(/Existencias insuficientes .* No se confirmó la venta/).waitFor();
      check(await line.getByLabel('Cantidad').inputValue() === '8', 'El borrador no se conservó tras el rechazo');
      check((await page.getByRole('region', { name: 'Comprobantes de venta' }).getByRole('row').count()) === 2,
        'El rechazo generó un comprobante');
      await page.getByRole('button', { name: 'Descartar borrador', exact: true }).click();
      await page.getByRole('button', { name: 'Nueva venta', exact: true }).waitFor();
      check(await page.locator('#sale-editor').count() === 0, 'Descartar el borrador no cerró el formulario');
      await logout();
      return 'una venta de 8 con 7 disponibles se rechaza sin comprobante y conserva el borrador';""",
    "operador-abre-venta": """
      await login(config.operator);
      await page.getByRole('link', { name: 'Ventas', exact: true }).click();
      await page.getByRole('button', { name: 'Nueva venta', exact: true }).click();
      await page.locator('#sale-price-list').selectOption({ label: 'DETAL · Detal' });
      const line = page.locator('#sale-editor fieldset.purchase-line').first();
      await line.locator('select').first().selectOption({ label: config.sku + ' Helecho de prueba' });
      await line.getByLabel('Cantidad').fill('1');
      await page.getByText('1.000,00 COP').waitFor();
      return 'formulario abierto con 1 unidad a 1.000,00 COP';""",
    "precio-cambiado-antes-de-confirmar": """
      // H1: the administrator changed the price to 1.200 while the form was open; the dialog never shows the old total.
      await page.getByRole('button', { name: 'Confirmar venta', exact: true }).click();
      await page.locator('#sale-error').getByText(/cambiaron desde que se consultaron \\(línea 1\\)/).waitFor();
      check(await page.getByRole('alertdialog').count() === 0, 'Se abrió el diálogo con un precio desactualizado');
      await page.getByText('1.200,00 COP').waitFor();
      await page.getByRole('button', { name: 'Confirmar venta', exact: true }).click();
      await page.getByRole('alertdialog').getByText(/total neto estimado de 1\\.200,00 COP/).waitFor();
      await page.getByRole('alertdialog').getByRole('button', { name: 'Confirmar venta', exact: true }).click();
      const done = page.getByText(/^Venta VT-\\d+ confirmada por 1\\.200,00 COP/);
      await done.waitFor();
      check(!(await done.innerText()).includes('difiere'), 'Aviso de total distinto sin cambio de precio');
      // H2 setup: a second list chosen in a new sale, deactivated by the administrator before confirming.
      await page.getByRole('button', { name: 'Nueva venta', exact: true }).click();
      await page.locator('#sale-price-list').selectOption({ label: 'MAYOR · Mayorista' });
      const line = page.locator('#sale-editor fieldset.purchase-line').first();
      await line.locator('select').first().selectOption({ label: config.sku + ' Helecho de prueba' });
      await line.getByLabel('Cantidad').fill('1');
      await page.getByText('900,00 COP').waitFor();
      return 'precio cambiado con el formulario abierto: confirmación detenida y venta por el total vigente de 1.200,00 COP';""",
    "lista-desactivada-antes-de-confirmar": """
      await page.getByRole('button', { name: 'Confirmar venta', exact: true }).click();
      await page.locator('#sale-error').getByText(/ya no está disponible: fue desactivada/).waitFor();
      check(await page.getByRole('alertdialog').count() === 0, 'Se abrió el diálogo con una lista desactivada');
      await page.waitForFunction(() => ![...document.querySelectorAll('#sale-price-list option')].some(option => option.textContent.includes('MAYOR')));
      const line = page.locator('#sale-editor fieldset.purchase-line').first();
      check(await line.getByLabel('Cantidad').inputValue() === '1', 'El borrador no se conservó');
      await page.getByRole('button', { name: 'Descartar borrador', exact: true }).click();
      await page.getByRole('button', { name: 'Nueva venta', exact: true }).waitFor();
      check(await page.locator('#sale-editor').count() === 0, 'Descartar el borrador no cerró el formulario');
      await logout();
      return 'lista desactivada antes de confirmar: aviso de lista no disponible, retirada del selector y borrador conservado';""",
    "gerente-consulta-comprobante": """
      await login(config.manager);
      await page.getByRole('link', { name: 'Ventas', exact: true }).click();
      await page.getByRole('heading', { name: /Comprobantes de venta de Sucursal Sur/ }).waitFor();
      check(await page.getByRole('button', { name: 'Nueva venta' }).count() === 0, 'El gerente ve la acción de vender');
      await page.getByRole('button', { name: 'Ver comprobante de VT-000001', exact: true }).click();
      await page.getByText('Operador Sur (OP-SUR)').waitFor();
      await page.getByText('2.700,00 COP').waitFor();
      await page.setViewportSize({ width: 390, height: 844 });
      await page.waitForTimeout(300);
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
      check(overflow <= 0, 'Desplazamiento horizontal del documento en móvil: ' + overflow + ' px');
      await page.screenshot({ path: config.screenshot, fullPage: true });
      await page.setViewportSize({ width: 1280, height: 800 });
      await logout();
      return 'el gerente consulta el comprobante sin acción de vender; sin desbordamiento a 390 px';""",
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
    project = "optiplant-sales-" + secrets.token_hex(6)
    directory = Path(tempfile.mkdtemp(prefix="optiplant-sales-"))
    backend_port, frontend_port = available_port(), available_port()
    while frontend_port == backend_port:
        frontend_port = available_port()
    admin_password, admin_new, temporary, operator_password, manager_password = (secrets.token_urlsafe(30) for _ in range(5))
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
              "manager": {"code": "GER-SUR", "password": manager_password},
              "screenshot": str(directory / "ventas-movil.png")}

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
        expect(admin.request("/users", "POST", {"code": "GER-SUR", "fullName": "Gerente Sur", "role": "MANAGER",
                                                "branchExternalId": branch, "temporaryPassword": temporary}), 201, "alta gerente")
        manager = Client(base)
        expect(manager.request("/auth/login", "POST", {"code": "GER-SUR", "password": temporary}), 200, "acceso gerente")
        expect(manager.request("/auth/password", "POST", {"currentPassword": temporary, "newPassword": manager_password}),
               204, "cambio gerente")
        product = expect(admin.request("/products", "POST", {"sku": SKU, "name": "Helecho de prueba", "description": None,
                                                             "baseUnitName": "unidad", "baseDivisible": False,
                                                             "branches": [{"branchExternalId": branch, "minimumQuantity": "0"}]}),
                         201, "alta de producto")["externalId"]
        expect(admin.request(f"/products/{product}/units", "POST", {"name": "caja", "factor": "12", "divisible": False}),
               200, "alta de unidad caja")
        price_list = expect(admin.request("/catalog/price-lists", "POST", {"code": "DETAL", "name": "Detal"}), 201, "alta de lista")
        for unit, price in (("unidad", "1000"), ("caja", "11000")):
            expect(admin.request(f"/catalog/price-lists/{price_list['externalId']}/prices", "POST",
                                 {"product": product, "unit": unit, "price": price}), 200, "precio " + unit)
        wholesale = expect(admin.request("/catalog/price-lists", "POST", {"code": "MAYOR", "name": "Mayorista"}), 201, "alta de lista mayorista")
        expect(admin.request(f"/catalog/price-lists/{wholesale['externalId']}/prices", "POST",
                             {"product": product, "unit": "unidad", "price": "900"}), 200, "precio mayorista")
        # What the administrator does between two browser stages, while the operator keeps the sale form open.
        between = {
            "operador-abre-venta": lambda: expect(admin.request(f"/catalog/price-lists/{price_list['externalId']}/prices", "POST",
                                                                {"product": product, "unit": "unidad", "price": "1200"}), 200, "cambio de precio"),
            "precio-cambiado-antes-de-confirmar": lambda: expect(admin.request(
                f"/catalog/price-lists/{wholesale['externalId']}/update", "POST",
                {"code": "MAYOR", "name": "Mayorista", "active": False}), 200, "desactivación de lista"),
        }
        operator = Client(base)
        expect(operator.request("/auth/login", "POST", {"code": "OP-SUR", "password": operator_password}), 200, "acceso operador")
        expect(operator.request("/inventory/movements", "POST", {"kind": "MANUAL_ENTRY", "product": product, "unit": "unidad",
                                                                 "quantity": "10", "unitCost": "500", "reason": "Inventario inicial"}),
               201, "ingreso inicial")

        subprocess.run(browser + ["open", f"http://localhost:{frontend_port}"], cwd=directory, capture_output=True,
                       text=True, check=True, timeout=60)
        for name in STAGES:
            stage(name)
            if name in between:
                between[name]()
        print(f"Navegador OK: {len(STAGES)} escenarios de ventas (RF-0027–RF-0029, RF-0031).")
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
