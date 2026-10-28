"""Exercise RF-0021–RF-0026 purchases and valuation in a real browser against an isolated, disposable Compose stack."""
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
SKU = "COMPRA-MACETA"


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
    "administrador-proveedores": """
      await login(config.admin);
      await page.getByRole('link', { name: 'Compras', exact: true }).click();
      await page.getByRole('heading', { name: 'Compras de la red', level: 2 }).waitFor();
      await page.getByRole('tab', { name: 'Proveedores' }).click();
      await page.getByRole('button', { name: 'Nuevo proveedor', exact: true }).click();
      await page.waitForFunction(() => document.activeElement?.closest('#supplier-editor') !== null);
      await page.getByLabel('Código', { exact: true }).fill('PRV-VALLE');
      await page.getByLabel('Nombre', { exact: true }).fill('Viveros del Valle');
      await page.getByRole('button', { name: 'Crear proveedor', exact: true }).click();
      await page.getByText('Proveedor PRV-VALLE creado.', { exact: true }).waitFor();
      // RF-0021-CA-02: a duplicated code is rejected in the view and the list keeps one supplier with that code.
      await page.getByRole('button', { name: 'Nuevo proveedor', exact: true }).click();
      await page.waitForFunction(() => document.activeElement?.closest('#supplier-editor') !== null);
      await page.getByLabel('Código', { exact: true }).fill('PRV-VALLE');
      await page.getByLabel('Nombre', { exact: true }).fill('Duplicado');
      await page.getByRole('button', { name: 'Crear proveedor', exact: true }).click();
      await page.locator('#supplier-error').filter({ hasText: 'Ya existe un proveedor con ese código' }).waitFor();
      check(await page.getByRole('row').filter({ hasText: 'PRV-VALLE' }).count() === 1, 'El código duplicado alteró el catálogo');
      await logout();
      return 'proveedor creado desde la interfaz; código duplicado rechazado sin alterar la lista';""",
    "operador-orden-y-recepcion": """
      await login(config.operator);
      await page.getByRole('link', { name: 'Compras', exact: true }).click();
      await page.getByRole('button', { name: 'Nueva orden de compra', exact: true }).click();
      await page.waitForFunction(() => document.activeElement?.closest('#purchase-editor') !== null);
      await page.locator('#purchase-supplier').selectOption({ label: 'PRV-VALLE · Viveros del Valle' });
      await page.getByLabel('Plazo de pago en días').fill('0');
      await page.getByLabel('Producto 1', { exact: true }).selectOption({ label: config.sku + ' Maceta de prueba' });
      await page.getByLabel('Unidad', { exact: true }).selectOption({ label: 'caja (12 unidad)' });
      await page.getByLabel('Cantidad', { exact: true }).fill('2');
      await page.getByLabel('Precio unitario por caja').fill('120');
      await page.getByLabel('Descuento %').fill('10');
      await page.locator('.purchase-total').filter({ hasText: '216,00 COP' }).waitFor();
      await page.setViewportSize({ width: 390, height: 844 });
      await page.waitForTimeout(300);
      const editorOverflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
      check(editorOverflow <= 0, 'Desplazamiento horizontal con el formulario en móvil: ' + editorOverflow + ' px');
      await page.screenshot({ path: config.screenshotEditor, fullPage: true });
      await page.setViewportSize({ width: 1280, height: 800 });
      await page.getByRole('button', { name: 'Crear borrador', exact: true }).click();
      await page.getByText(/Borrador OC-\\d+ creado con 1 producto por 216,00 COP/).waitFor();
      const detail = page.locator('#purchase-detail');
      await detail.getByText('Borrador', { exact: true }).first().waitFor();
      // Sending to receipt and receiving both go through the confirmation dialog.
      await detail.getByRole('button', { name: 'Enviar a recepción', exact: true }).click();
      await page.getByRole('alertdialog').getByRole('button', { name: 'Enviar a recepción', exact: true }).click();
      await page.getByText(/OC-\\d+ quedó pendiente de recepción\\./).waitFor();
      const sent = [];
      page.on('request', request => { if (request.url().endsWith('/receive')) sent.push(request.url()); });
      await detail.getByRole('button', { name: 'Confirmar recepción', exact: true }).click();
      await page.getByRole('alertdialog').waitFor();
      await page.keyboard.press('Escape');
      await page.getByRole('alertdialog').waitFor({ state: 'detached' });
      check(sent.length === 0, 'Escape envió la recepción');
      await detail.getByRole('button', { name: 'Confirmar recepción', exact: true }).click();
      await page.getByRole('alertdialog').getByRole('button', { name: 'Confirmar recepción', exact: true }).click();
      await page.getByText(/Recepción de OC-\\d+ confirmada: el inventario de Sucursal Sur aumentó por las 1 líneas\\./).waitFor();
      check(sent.length === 1, 'Se esperaba una sola recepción; enviadas: ' + sent.length);
      const cost = await detail.getByRole('row').filter({ hasText: config.sku }).getByRole('cell').allInnerTexts();
      // INC-003/D04: the operator keeps the conditions it registered, but not the valuation cost.
      check(cost.some(c => c.startsWith('216,00')) && !cost.some(c => c.startsWith('9,000000')),
        'Línea recibida inesperada: ' + cost.join(' | '));
      check(await detail.getByRole('columnheader', { name: 'Costo por unidad base' }).count() === 0, 'El operador ve el costo de valoración');
      check(await detail.getByRole('button', { name: 'Confirmar recepción' }).count() === 0, 'La recepción sigue disponible');
      // RF-0024-CA-01: the entry exists in the movement history, without a manual movement.
      await page.getByRole('link', { name: 'Movimientos', exact: true }).click();
      const receipt = page.getByRole('row').filter({ hasText: 'Recepción de compra' }).first();
      await receipt.waitFor({ timeout: 10000 });
      const entry = await receipt.getByRole('cell').allInnerTexts();
      check(entry.some(c => c.startsWith('+2,000 caja')) && entry.some(c => c.includes('Operador Sur')), 'Ingreso inesperado: ' + entry.join(' | '));
      await logout();
      return 'borrador con neto 216,00, enviado y recibido con confirmación; sin costo de valoración para el operador e ingreso +2,000 caja en el historial';""",
    "gerente-historico": """
      await login(config.manager);
      await page.getByRole('link', { name: 'Compras', exact: true }).click();
      check(await page.getByRole('button', { name: 'Nueva orden de compra' }).count() === 0, 'El gerente puede crear órdenes');
      await page.getByLabel('Producto', { exact: true }).selectOption({ label: config.sku + ' Maceta de prueba' });
      await page.getByRole('button', { name: 'Consultar', exact: true }).click();
      await page.locator('.purchase-summary').filter({ hasText: 'Compras recibidas1' }).waitFor();
      const row = await page.getByRole('row').filter({ hasText: 'Viveros del Valle' }).first().getByRole('cell').allInnerTexts();
      check(row.some(c => c === 'Recibida'), 'Estado inesperado: ' + row.join(' | '));
      await page.getByRole('button', { name: /^Ver detalle de OC-/ }).first().click();
      check(await page.locator('#purchase-detail').getByRole('button', { name: 'Confirmar recepción' }).count() === 0, 'El gerente puede recibir');
      const costRow = page.locator('#purchase-detail').getByRole('row').filter({ hasText: config.sku }).first();
      await costRow.waitFor({ timeout: 10000 });
      const line = await costRow.getByRole('cell').allInnerTexts();
      check(line.some(c => c.startsWith('9,000000')), 'El gerente no ve el costo por unidad base: ' + line.join(' | '));
      await logout();
      return 'histórico por producto con la compra recibida y su costo de 9 por unidad base; sin acciones de registro ni recepción';""",
    "administrador-red-movil": """
      await login(config.admin);
      await page.getByRole('link', { name: 'Compras', exact: true }).click();
      await page.getByRole('columnheader', { name: 'Sucursal' }).waitFor();
      await page.getByRole('row').filter({ hasText: 'Sucursal Sur' }).filter({ hasText: 'Pendiente de recepción' }).waitFor();
      await page.setViewportSize({ width: 390, height: 844 });
      await page.waitForTimeout(300);
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
      check(overflow <= 0, 'Desplazamiento horizontal del documento en móvil: ' + overflow + ' px');
      await page.screenshot({ path: config.screenshot, fullPage: true });
      await logout();
      return 'histórico de la red con columna de sucursal y la orden pendiente creada por API; sin desbordamiento a 390 px';""",
    "operador-cancelar-reemplazar-editar": """
      await login(config.operator);
      check(await page.getByRole('link', { name: 'Valoración', exact: true }).count() === 0, 'El operador ve la valoración');
      await page.getByRole('link', { name: 'Compras', exact: true }).click();
      const pendingRow = page.getByRole('row').filter({ hasText: 'Pendiente de recepción' }).first();
      await pendingRow.waitFor();
      await pendingRow.getByRole('button', { name: /^Ver detalle de OC-/ }).click();
      const detail = page.locator('#purchase-detail');
      // RF-0022: a pending order is not edited; it is cancelled with an optional reason.
      check(await detail.getByRole('button', { name: 'Editar borrador' }).count() === 0, 'Una orden pendiente ofrece edición');
      await detail.getByRole('button', { name: 'Cancelar orden', exact: true }).click();
      await detail.getByLabel('Motivo (opcional)').fill('Precio equivocado');
      await detail.getByRole('button', { name: 'Volver sin cancelar', exact: true }).click();
      check(await detail.getByRole('button', { name: 'Cancelar orden', exact: true }).count() === 1, 'Volver no restauró las acciones');
      await detail.getByRole('button', { name: 'Cancelar orden', exact: true }).click();
      await detail.getByLabel('Motivo (opcional)').fill('Precio equivocado');
      await detail.getByRole('button', { name: 'Confirmar cancelación', exact: true }).click();
      await page.getByText(/OC-\\d+ quedó cancelada\\. Las existencias y el costo promedio no cambiaron\\./).waitFor();
      await detail.getByText('Motivo: Precio equivocado').waitFor();
      const cancelled = (await detail.locator('#purchase-detail-title').innerText()).replace('Orden ', '');
      // RF-0022-CA-02: the replacement is a new draft prefilled from the cancelled order.
      await detail.getByRole('button', { name: 'Crear orden de reemplazo', exact: true }).click();
      await page.waitForFunction(() => document.activeElement?.closest('#purchase-editor') !== null);
      await page.locator('#purchase-editor legend').first().filter({ hasText: 'Orden de reemplazo de ' + cancelled }).waitFor();
      check(await page.getByLabel('Cantidad', { exact: true }).inputValue() === '5', 'La cantidad no se copió');
      await page.getByLabel('Precio unitario por unidad').fill('12');
      await page.getByRole('button', { name: 'Crear borrador', exact: true }).click();
      await page.getByText(new RegExp('Borrador OC-\\\\d+ creado en reemplazo de ' + cancelled)).waitFor();
      await detail.getByText('Reemplaza a').waitFor();
      // Only the draft is edited, and the edit keeps it a draft.
      await detail.getByRole('button', { name: 'Editar borrador', exact: true }).click();
      await page.waitForFunction(() => document.activeElement?.closest('#purchase-editor') !== null);
      await page.getByLabel('Cantidad', { exact: true }).fill('6');
      await page.getByRole('button', { name: 'Guardar borrador', exact: true }).click();
      await page.getByText(/Borrador OC-\\d+ actualizado: 1 producto por 72,00 COP\\. Sigue en borrador/).waitFor();
      await page.getByRole('row').filter({ hasText: 'Reemplazada por' }).filter({ hasText: 'Cancelada' }).first().waitFor();
      await page.setViewportSize({ width: 390, height: 844 });
      await page.waitForTimeout(300);
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
      check(overflow <= 0, 'Desplazamiento horizontal con el detalle en móvil: ' + overflow + ' px');
      await page.screenshot({ path: config.screenshotLifecycle, fullPage: true });
      await page.setViewportSize({ width: 1280, height: 800 });
      await logout();
      return 'pendiente cancelada con motivo; reemplazo prefijado y vinculado; borrador editado a 72,00 COP; sin desbordamiento a 390 px';""",
    "gerente-valoracion": """
      await login(config.manager);
      await page.getByRole('link', { name: 'Valoración', exact: true }).click();
      await page.getByRole('heading', { name: 'Valoración del inventario', level: 2 }).waitFor();
      const row = page.getByRole('row').filter({ hasText: config.sku });
      await row.waitFor({ timeout: 10000 });
      // RF-0026: only the received purchase is valued; the cancelled order and the draft changed nothing.
      const cells = await row.getByRole('cell').allInnerTexts();
      check(cells.some(c => c.startsWith('24,000 unidad')) && cells.some(c => c.startsWith('9,000000')) && cells.some(c => c.startsWith('216,00')),
        'Valoración inesperada: ' + cells.join(' | '));
      await page.locator('.purchase-summary').filter({ hasText: '216,00 COP' }).waitFor();
      await page.setViewportSize({ width: 390, height: 844 });
      await page.waitForTimeout(300);
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
      check(overflow <= 0, 'Desplazamiento horizontal de la valoración en móvil: ' + overflow + ' px');
      await page.screenshot({ path: config.screenshotValuation, fullPage: true });
      await page.setViewportSize({ width: 1280, height: 800 });
      await logout();
      return 'valoración de la sucursal: 24,000 unidad a promedio 9,000000 y valor 216,00 COP; sin desbordamiento a 390 px';""",
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
      catch (error) { return { status: 'failed', stage: STAGE, detail: String(error).slice(0, 300) }; }
    }""".replace("CONFIG", json.dumps(config), 1).replace("STAGE", json.dumps(name)).replace("BODY", body, 1)


def main():
    project = "optiplant-purchases-" + secrets.token_hex(6)
    directory = Path(tempfile.mkdtemp(prefix="optiplant-purchases-"))
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
              "screenshot": str(directory / "compras-red-movil.png"),
              "screenshotEditor": str(directory / "compras-orden-movil.png"),
              "screenshotLifecycle": str(directory / "compras-reemplazo-movil.png"),
              "screenshotValuation": str(directory / "valoracion-movil.png")}
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
        operator = local_user(admin, base, "OP-SUR", "Operador Sur", "OPERATOR", branch, operator_password)
        local_user(admin, base, "GER-SUR", "Gerente Sur", "MANAGER", branch, manager_password)
        product = expect(admin.request("/products", "POST", {"sku": SKU, "name": "Maceta de prueba", "description": None,
                                                             "baseUnitName": "unidad", "baseDivisible": False,
                                                             "branches": [{"branchExternalId": branch, "minimumQuantity": "0"}]}),
                         201, "alta de producto")["externalId"]
        expect(admin.request(f"/products/{product}/units", "POST", {"name": "caja", "factor": "12", "divisible": False}),
               200, "unidad caja")

        subprocess.run(browser + ["open", f"http://localhost:{frontend_port}"], cwd=directory, capture_output=True,
                       text=True, check=True, timeout=60)
        stage("administrador-proveedores")
        stage("operador-orden-y-recepcion")
        stage("gerente-historico")
        # A second order left pending through the API, so the network history shows another state.
        supplier = next(item for item in expect(operator.request("/purchases/suppliers"), 200, "proveedores")
                        if item["code"] == "PRV-VALLE")["externalId"]
        order = expect(operator.request("/purchases/orders", "POST", {"supplier": supplier, "paymentTermDays": 15, "lines": [
            {"product": product, "unit": "unidad", "quantity": "5", "unitPrice": "10", "discountPercent": "0"}]}), 201, "orden")
        expect(operator.request(f"/purchases/orders/{order['externalId']}/submit", "POST", {}), 200, "envío a recepción")
        stage("administrador-red-movil")
        stage("operador-cancelar-reemplazar-editar")
        stage("gerente-valoracion")
        print(f"Navegador OK: {len(STAGES)} escenarios de compras y valoración (RF-0021–RF-0026).")
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
