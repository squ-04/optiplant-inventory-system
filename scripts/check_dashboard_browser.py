"""Exercise RF-0046–RF-0049 in a real browser against an isolated, disposable Compose stack."""
from datetime import datetime, timedelta, timezone
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys
import tempfile

from check_restock_browser import Client, ROOT, available_port, expect

# Each stage returns {status, stage, detail}; details never include credentials.
STAGES = {
    "gerente-indicadores-de-su-sucursal": """
      await login(config.manager);
      await open();
      await tab('Demanda');
      const demand = page.locator('section[aria-labelledby="dashboard-demand"]');
      await demand.getByRole('heading', { name: 'Productos vendidos en unidad' }).waitFor();
      const high = await demand.getByRole('region', { name: 'Alta demanda en unidad' }).innerText();
      const low = await demand.getByRole('region', { name: 'Baja demanda en unidad' }).innerText();
      check(high.includes('Helecho') && high.includes('3,000 unidad'), 'Alta demanda: ' + high);
      check(low.includes('Maceta') && !low.includes('Helecho'), 'Baja demanda: ' + low);
      await demand.getByRole('region', { name: 'Alta demanda en kg' }).waitFor();
      await demand.getByRole('heading', { name: /^Sin ventas en/ }).waitFor();
      check((await demand.getByRole('region', { name: 'Productos sin ventas en el mes' }).innerText()).includes('Abono'), 'Falta el producto sin ventas');
      await demand.getByLabel('Mes', { exact: true }).fill('2099-01');
      await demand.getByRole('button', { name: 'Consultar demanda' }).click();
      await demand.getByText('El mes consultado no puede ser posterior al mes en curso.').waitFor();
      await page.screenshot({ path: config.shots + '/demanda.png', fullPage: true });

      await tab('Transferencias');
      const transfers = page.locator('section[aria-labelledby="dashboard-transfers"]');
      await transfers.getByText('Sale hacia Sucursal Norte (NORTE)').first().waitFor();
      const region = transfers.getByRole('region', { name: 'Transferencias activas de la sucursal' });
      const rows = await region.innerText();
      check(await region.evaluate(node => node.scrollWidth <= node.clientWidth), 'La tabla de impacto se desplaza en escritorio');
      for (const expected of ['Solicitada', 'En tránsito', 'Recibida con discrepancia', 'Alerta de faltante abierta', '4,000 unidad', '7,000 unidad', '3,000 unidad', 'No aplica']) {
        check(rows.includes(expected), 'Falta en transferencias: ' + expected);
      }
      await page.screenshot({ path: config.shots + '/transferencias.png', fullPage: true });

      await tab('Próximos a agotarse');
      const restock = page.locator('section[aria-labelledby="dashboard-restock"]');
      const low2 = restock.getByRole('row').filter({ hasText: 'Sustrato' });
      await low2.waitFor();
      check((await low2.innerText()).includes('Por debajo de su mínimo'), 'Situación del producto: ' + await low2.innerText());
      check(await restock.getByRole('row').filter({ hasText: 'Helecho' }).count() === 0, 'Un producto sobre su mínimo aparece listado');
      await page.screenshot({ path: config.shots + '/agotarse.png', fullPage: true });

      check(await page.getByRole('tab', { name: 'Comparativa' }).count() === 0, 'El gerente ve la comparativa de red');
      await logout();
      return 'demanda, transferencias e indicador de reabastecimiento con datos reales; sin comparativa para el gerente';""",
    "administrador-comparativa-y-movil": """
      await login(config.admin);
      await open();
      await page.getByText('Seleccione una sucursal para ver sus ventas mensuales').waitFor();
      await tab('Comparativa');
      const comparison = page.locator('section[aria-labelledby="dashboard-comparison"]');
      const table = comparison.getByRole('region', { name: 'Comparativa entre sucursales' });
      await table.getByText('Sucursal Sur (SUR)').waitFor();
      const south = await table.getByRole('row').filter({ hasText: 'Sucursal Sur (SUR)' }).innerText();
      const north = await table.getByRole('row').filter({ hasText: 'Sucursal Norte (NORTE)' }).innerText();
      check(south.includes('3 ventas') && south.includes('COP'), 'Fila Sur: ' + south);
      check(north.includes('Sin ventas') && north.includes('0,00 COP'), 'Fila Norte: ' + north);
      check(north.includes('No calculable') || south.includes('No calculable'), 'Falta «No calculable»');
      // The bars animate in; the drawn width is only measurable once they settle.
      await page.waitForTimeout(2000);
      const bars = await comparison.locator('figure .recharts-bar-rectangle path').count();
      check(bars === 1, 'Barras dibujadas: ' + bars);
      await page.screenshot({ path: config.shots + '/comparativa.png', fullPage: true });
      for (const width of [390, 320]) {
        await page.setViewportSize({ width, height: 800 });
        await page.waitForTimeout(300);
        const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
        check(overflow <= 0, 'Desborde horizontal a ' + width + ' px: ' + overflow);
      }
      await page.screenshot({ path: config.shots + '/comparativa-movil.png', fullPage: true });
      await page.setViewportSize({ width: 1440, height: 900 });
      await page.locator('#dashboard-branch').selectOption({ label: 'SUR · Sucursal Sur' });
      await tab('Demanda');
      await page.getByRole('region', { name: 'Alta demanda en unidad' }).waitFor();
      await logout();
      return 'la comparativa distingue cero y «No calculable», sin desborde a 390 y 320 px; el administrador consulta la sucursal elegida';""",
}


def stage_script(name, body, config):
    return """async page => {
      const config = CONFIG;
      const check = (condition, message) => { if (!condition) throw new Error(message); };
      const open = async () => {
        await page.getByRole('link', { name: 'Dashboard', exact: true }).click();
        await page.getByRole('heading', { name: 'Dashboard', exact: true }).waitFor();
      };
      const tab = name => page.getByRole('tab', { name, exact: true }).click();
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
    project = "optiplant-dashboard-" + secrets.token_hex(6)
    directory = Path(tempfile.mkdtemp(prefix="optiplant-dashboard-"))
    backend_port, frontend_port = available_port(), available_port()
    while frontend_port == backend_port:
        frontend_port = available_port()
    admin_password, admin_new, temporary, password = (secrets.token_urlsafe(30) for _ in range(4))
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

    config = {"admin": {"code": "ADMIN", "password": admin_new}, "manager": {"code": "GER-SUR", "password": password},
              "shots": str(directory)}

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

    def local_user(admin, base, code, name, role, branch):
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
        south = expect(admin.request("/branches", "POST", {"code": "SUR", "name": "Sucursal Sur"}), 201, "alta Sur")["externalId"]
        north = expect(admin.request("/branches", "POST", {"code": "NORTE", "name": "Sucursal Norte"}), 201, "alta Norte")["externalId"]
        expect(admin.request("/logistics/routes", "POST", {"code": "SUR-NORTE", "origin": south, "destination": north,
                                                           "priority": "HIGH"}), 201, "ruta")
        operator = local_user(admin, base, "OP-SUR", "Operador Sur", "OPERATOR", south)
        manager = local_user(admin, base, "GER-SUR", "Gerente Sur", "MANAGER", south)
        receiver = local_user(admin, base, "OP-NORTE", "Operador Norte", "OPERATOR", north)
        price_list = expect(admin.request("/catalog/price-lists", "POST", {"code": "DETAL", "name": "Detal"}), 201, "lista")["externalId"]

        def product(sku, name, unit, minimum, stock):
            created = expect(admin.request("/products", "POST", {
                "sku": sku, "name": name, "description": None, "baseUnitName": unit, "baseDivisible": False,
                "branches": [{"branchExternalId": south, "minimumQuantity": minimum}, {"branchExternalId": north, "minimumQuantity": "0"}],
            }), 201, "alta " + sku)["externalId"]
            expect(admin.request(f"/catalog/price-lists/{price_list}/prices", "POST", {"product": created, "unit": unit, "price": "1500"}),
                   200, "precio " + sku)
            expect(operator.request("/inventory/movements", "POST", {"kind": "MANUAL_ENTRY", "product": created, "unit": unit,
                                                                     "quantity": stock, "unitCost": "1000", "reason": "Inventario inicial"}),
                   201, "ingreso " + sku)
            return created

        def sell(item, unit, quantity):
            expect(operator.request("/sales", "POST", {"priceList": price_list, "lines": [
                {"product": item, "unit": unit, "quantity": quantity, "discountPercent": "10"}]}), 201, "venta")

        helecho = product("HEL-01", "Helecho", "unidad", "2", "100")
        maceta = product("MAC-01", "Maceta", "unidad", "2", "20")
        sustrato = product("SUS-01", "Sustrato", "kg", "50", "9")
        product("ABO-01", "Abono", "unidad", "1", "10")
        sell(helecho, "unidad", "3")
        sell(maceta, "unidad", "2")
        sell(sustrato, "kg", "1")

        def transfer(quantity):
            created = expect(receiver.request("/transfers", "POST", {"origin": south, "lines": [
                {"product": helecho, "unit": "unidad", "quantity": quantity}]}), 201, "solicitud")["externalId"]
            return created

        def dispatched(quantity):
            created = transfer(quantity)
            lines = {"lines": [{"lineNo": 1, "quantity": quantity}]}
            expect(operator.request(f"/transfers/{created}/prepare", "POST", lines), 200, "preparación")
            expect(manager.request(f"/transfers/{created}/approve", "POST", {}), 200, "aprobación")
            now = datetime.now(timezone.utc)
            expect(operator.request(f"/transfers/{created}/dispatch", "POST", {
                "carrier": "Transportes Andinos", "departedAt": now.isoformat(),
                "estimatedArrivalAt": (now + timedelta(hours=4)).isoformat()}), 200, "despacho")
            return created

        transfer("4")
        dispatched("5")
        partial = dispatched("10")
        expect(receiver.request(f"/transfers/{partial}/receive", "POST", {"lines": [{"lineNo": 1, "quantity": "7"}]}), 200, "recepción parcial")

        subprocess.run(browser + ["open", f"http://localhost:{frontend_port}"], cwd=directory, capture_output=True,
                       text=True, check=True, timeout=60)
        stage("gerente-indicadores-de-su-sucursal")
        stage("administrador-comparativa-y-movil")
        print(f"Navegador OK: {len(STAGES)} escenarios del dashboard (RF-0046–RF-0049).")
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
