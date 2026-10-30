"""Exercise RF-0050–RF-0055 in a real browser against an isolated, disposable Compose stack."""
from datetime import datetime, timedelta, timezone
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys
import tempfile
import zipfile

from check_restock_browser import Client, ROOT, available_port, expect

# Each stage returns {status, stage, detail}; details never include credentials.
STAGES = {
    "gerente-descarga-su-sucursal": """
      await login(config.manager);
      await open();
      check(await form().getByLabel('Sucursal', { exact: true }).count() === 0, 'El gerente no elige sucursal');
      await page.getByText('Las fechas se aplican a la fecha de registro de cada movimiento.').waitFor();
      await page.getByRole('radio', { name: /^Ventas/ }).check();
      await page.getByText('Las fechas se aplican a la fecha de confirmación de cada venta.').waitFor();
      const sales = await download();
      check(/^ventas_\\d{4}-\\d{2}-\\d{2}_\\d{4}-\\d{2}-\\d{2}\\.xlsx$/.test(sales.suggestedFilename()), 'Nombre: ' + sales.suggestedFilename());
      await sales.saveAs(config.shots + '/ventas.xlsx');
      await page.getByText(/^Se descargó ventas_.* con 3 filas\\.$/).waitFor();
      await page.screenshot({ path: config.shots + '/gerente.png', fullPage: true });

      await page.getByRole('radio', { name: /^Transferencias/ }).check();
      await form().getByLabel('Estado', { exact: true }).selectOption('IN_TRANSIT');
      const transfers = await download();
      await transfers.saveAs(config.shots + '/transferencias.xlsx');
      await page.getByText(/ con 1 fila\\.$/).waitFor();

      await form().getByLabel('Desde', { exact: true }).fill('2020-01-01');
      await form().getByLabel('Hasta', { exact: true }).fill('2020-01-31');
      await (await download()).saveAs(config.shots + '/vacio.xlsx');
      await page.getByText(/sin filas: no hay registros para esos filtros\\.$/).waitFor();

      await form().getByLabel('Hasta', { exact: true }).fill('2021-01-01');
      await page.getByRole('button', { name: 'Descargar Excel', exact: true }).click();
      await page.getByText('El período de un reporte no puede superar 12 meses. Reduzca el rango de fechas.').waitFor();
      await logout();
      return 'Gerente: descarga, filtro por estado, archivo vacío y período rechazado';
    """,
    "administrador-red-y-movil": """
      await login(config.admin);
      await open();
      const branch = form().getByLabel('Sucursal', { exact: true });
      await branch.getByRole('option', { name: 'SUR · Sucursal Sur' }).waitFor({ state: 'attached' });
      check(await branch.inputValue() === '', 'La red es la opción inicial del administrador');
      await (await download()).saveAs(config.shots + '/movimientos-red.xlsx');
      await page.getByText(/^Se descargó movimientos_/).waitFor();
      await page.screenshot({ path: config.shots + '/administrador.png', fullPage: true });
      await page.setViewportSize({ width: 375, height: 800 });
      check(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), 'Desplazamiento horizontal en móvil');
      await form().scrollIntoViewIfNeeded();
      await page.screenshot({ path: config.shots + '/movil.png' });
      await page.setViewportSize({ width: 1280, height: 800 });
      await logout();
      return 'Administrador: toda la red y vista móvil sin desplazamiento';
    """,
    "operador-sin-reportes": """
      await login(config.operator);
      check(await page.getByRole('link', { name: 'Reportes', exact: true }).count() === 0, 'El operador ve Reportes');
      return 'Operador: sin acceso a la vista';
    """,
}


def stage_script(name, body, config):
    return """async page => {
      const config = CONFIG;
      const check = (condition, message) => { if (!condition) throw new Error(message); };
      const open = async () => {
        await page.getByRole('link', { name: 'Reportes', exact: true }).click();
        await page.getByRole('heading', { name: 'Reportes', exact: true }).waitFor();
      };
      const form = () => page.getByRole('form', { name: 'Filtros del reporte' });
      const download = async () => {
        const pending = page.waitForEvent('download');
        await page.getByRole('button', { name: 'Descargar Excel', exact: true }).click();
        return pending;
      };
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
    project = "optiplant-reports-" + secrets.token_hex(6)
    directory = Path(tempfile.mkdtemp(prefix="optiplant-reports-"))
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
              "operator": {"code": "OP-SUR", "password": password},
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

    def workbook(name, present, absent):
        # The downloaded file must be a real workbook: a ZIP with the OOXML parts, not a renamed text file.
        with zipfile.ZipFile(directory / name) as file:
            assert "xl/workbook.xml" in file.namelist(), f"{name} no es un libro XLSX"
            text = file.read("xl/sharedStrings.xml").decode("utf-8")
        for expected in present:
            assert expected in text, f"Falta en {name}: {expected}"
        for unexpected in absent:
            assert unexpected not in text, f"Sobra en {name}: {unexpected}"
        print("OK    contenido de " + name, flush=True)

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
        for name in STAGES:
            stage(name)
        workbook("ventas.xlsx", ["Número de venta", "Helecho", "Sustrato"], ["Sucursal Norte"])
        workbook("transferencias.xlsx", ["Número de transferencia", "En tránsito", "Fecha de solicitud de la transferencia"], [])
        workbook("vacio.xlsx", ["Sin resultados: no hay registros para los filtros indicados."], [])
        workbook("movimientos-red.xlsx", ["Toda la red", "Sucursal Norte", "Sucursal Sur"], ["Costo"])
        print(f"Navegador OK: {len(STAGES)} escenarios de reportes (RF-0050–RF-0055).")
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
