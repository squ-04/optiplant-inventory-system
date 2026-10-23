"""Verify an isolated Compose stack over public HTTP; never reuse a user volume."""

from decimal import Decimal
import http.cookiejar
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]


def available_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


class Client:
    def __init__(self, base):
        self.base = base
        self.cookies = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cookies))

    def request(self, path, method="GET", payload=None, headers=None):
        request_headers = {"Accept": "application/json", **(headers or {})}
        data = None
        if payload is not None:
            data = json.dumps(payload).encode()
            request_headers["Content-Type"] = "application/json"
        request = urllib.request.Request(self.base + path, data=data,
                                         headers=request_headers, method=method)
        try:
            response = self.opener.open(request, timeout=20)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            body = response.read()
            content_type = response.headers.get("Content-Type", "")
            value = json.loads(body) if body and "json" in content_type else body
            return response.status, value, response.headers

    def csrf(self):
        status, value, _ = self.request("/api/v1/auth/csrf")
        assert status == 200, f"CSRF: {status}"
        return {value["headerName"]: value["token"]}


def assert_status(response, expected, step):
    status, body, headers = response
    assert status == expected, f"{step}: esperado {expected}, recibido {status}"
    return body, headers


def main():
    project = "optiplant-check-" + secrets.token_hex(6)
    directory = Path(tempfile.mkdtemp(prefix="optiplant-check-"))
    env_path = directory / ".env"
    admin_password = secrets.token_urlsafe(36)
    new_password = secrets.token_urlsafe(36)
    backend_port, frontend_port = available_port(), available_port()
    while frontend_port == backend_port:
        frontend_port = available_port()
    values = {
        "POSTGRES_DB": "optiplant", "POSTGRES_USER": "optiplant_owner",
        "POSTGRES_PASSWORD": secrets.token_urlsafe(36),
        "APP_DB_PASSWORD": secrets.token_urlsafe(36),
        "APP_DB_MIGRATION_PASSWORD": secrets.token_urlsafe(36),
        "APP_ADMIN_CODE": "ADMIN", "APP_ADMIN_PASSWORD": admin_password,
        "APP_DEMO_DATA": "true", "BACKEND_PORT": str(backend_port),
        "FRONTEND_PORT": str(frontend_port),
    }
    descriptor = os.open(env_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w") as stream:
        stream.write("".join(f"{key}={value}\n" for key, value in values.items()))
    # Do not let ambient COMPOSE_* or .env values select another project or credential.
    env = {key: value for key, value in os.environ.items()
           if not key.startswith("COMPOSE_") and key not in values}
    env["DOCKER_HOST"] = "unix:///var/run/docker.sock"
    env.pop("DOCKER_CONTEXT", None)
    command = ["docker", "compose", "--project-name", project,
               "--env-file", str(env_path), "-f", str(ROOT / "compose.yaml")]

    def compose(*args, capture=False, check=True, timeout=900):
        return subprocess.run(command + list(args), cwd=ROOT, env=env,
                              capture_output=capture, text=True, check=check, timeout=timeout)

    def domain_counts():
        sql = """
            SELECT table_name || ':' || ((xpath('/row/c/text()',
              query_to_xml(format('SELECT count(*) AS c FROM %I.%I', table_schema, table_name),
              false, true, '')))[1])::text
            FROM information_schema.tables
            WHERE table_schema='public' AND table_type='BASE TABLE'
              AND lower(table_name) NOT LIKE 'spring_session%'
              AND table_name NOT IN ('identity_login_guard')
            ORDER BY table_name
        """
        return compose("exec", "-T", "postgres", "sh", "-ec",
                       'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "$1"',
                       "sh", sql, capture=True, timeout=30).stdout

    try:
        compose("up", "--build", "--wait", "--wait-timeout", "240")
        initial_counts = domain_counts()
        assert len(initial_counts.splitlines()) >= 32, "Esquema de dominio incompleto"
        api = Client(f"http://localhost:{backend_port}")
        assert_status(api.request("/actuator/health"), 200, "salud")
        api_docs, _ = assert_status(api.request("/v3/api-docs"), 200, "OpenAPI")
        assert "openapi" in api_docs
        assert_status(api.request("/swagger-ui/index.html"), 200, "Swagger UI")
        with urllib.request.urlopen(f"http://localhost:{frontend_port}", timeout=20) as response:
            assert response.status == 200 and b'id="root"' in response.read()
        assert_status(api.request("/api/v1/auth/me"), 401, "sesión anónima")
        assert_status(api.request("/api/v1/bootstrap"), 401, "recurso protegido")
        body, headers = assert_status(api.request("/api/v1/auth/login", "POST", {
            "code": "ADMIN", "password": admin_password,
        }), 403, "rechazo sin CSRF")
        assert "application/problem+json" in headers.get("Content-Type", "")
        assert body["status"] == 403
        origin = f"http://localhost:{frontend_port}"
        _, headers = assert_status(api.request("/api/v1/auth/me", "OPTIONS", headers={
            "Origin": origin, "Access-Control-Request-Method": "GET",
        }), 200, "CORS permitido")
        assert headers.get("Access-Control-Allow-Origin") == origin
        assert headers.get("Access-Control-Allow-Credentials") == "true"
        assert_status(api.request("/api/v1/auth/me", "OPTIONS", headers={
            "Origin": "https://invalid.example", "Access-Control-Request-Method": "GET",
        }), 403, "CORS rechazado")
        user, headers = assert_status(api.request("/api/v1/auth/login", "POST", {
            "code": "ADMIN", "password": admin_password,
        }, api.csrf()), 200, "primer acceso")
        assert user["mustChangePassword"] is True
        assert any("HttpOnly" in cookie for cookie in headers.get_all("Set-Cookie", []))
        assert any("SameSite=Lax" in cookie for cookie in headers.get_all("Set-Cookie", []))
        assert_status(api.request("/api/v1/bootstrap"), 403, "cambio obligatorio")
        assert_status(api.request("/api/v1/auth/password", "POST", {
            "currentPassword": admin_password, "newPassword": new_password,
        }, api.csrf()), 204, "cambio de contraseña")
        assert_status(api.request("/api/v1/auth/login", "POST", {
            "code": "ADMIN", "password": new_password,
        }, api.csrf()), 200, "acceso tras cambio")
        dto, _ = assert_status(api.request("/api/v1/bootstrap"), 200, "DTO REST")
        assert uuid.UUID(dto["externalId"]).version == 4
        assert isinstance(dto["occurredAt"], str) and "T" in dto["occurredAt"]
        assert isinstance(dto["amount"], str)
        Decimal(dto["amount"])
        assert "id" not in dto
        compose("restart", "backend", timeout=120)
        compose("up", "--wait", "--wait-timeout", "180", timeout=240)
        assert domain_counts() == initial_counts, "Reinicio duplicó o perdió registros de demostración"
        assert_status(api.request("/api/v1/auth/me"), 200, "sesión JDBC tras reinicio")
        assert_status(api.request("/api/v1/auth/logout", "POST", headers=api.csrf()), 204, "salida")
        assert_status(api.request("/api/v1/auth/me"), 401, "sesión revocada")
        compose("down", timeout=120)
        compose("up", "--wait", "--wait-timeout", "180", timeout=240)
        assert domain_counts() == initial_counts, "down/up sin -v alteró registros persistidos"
        assert_status(api.request("/api/v1/auth/login", "POST", {
            "code": "ADMIN", "password": new_password,
        }, api.csrf()), 200, "contraseña persistida tras down/up")
        print("Humo OK: Compose, salud, frontend, OpenAPI, cookies, CORS/CSRF, cambio inicial, DTO, sesión JDBC y persistencia/semillas.")
        return 0
    except (AssertionError, OSError, ValueError, KeyError, subprocess.SubprocessError) as error:
        print(f"Integración FALLÓ: {error}", file=sys.stderr)
        # Logs do not include environment/config output or submitted passwords.
        compose("logs", "--tail", "70", "backend", capture=False, check=False, timeout=30)
        return 1
    finally:
        # Only the new, uniquely named test project's disposable volume is removed.
        result = compose("down", "--volumes", "--remove-orphans", check=False, timeout=120)
        print(f"Scratch retenido para limpieza del padre: {directory}")
        if result.returncode:
            print(f"Limpieza pendiente del proyecto aislado {project}", file=sys.stderr)
            return 1


if __name__ == "__main__":
    sys.exit(main())
