"""Create individual local credentials once; never replace existing configuration."""

import argparse
import os
from pathlib import Path
import secrets

ROOT = Path(__file__).resolve().parents[1]


def initialize(root):
    target = root / ".env"
    try:
        descriptor = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    except FileExistsError:
        print("Configuración existente conservada; no se han cambiado credenciales.")
        return
    values = {
        "POSTGRES_DB": "optiplant",
        "POSTGRES_USER": "optiplant_owner",
        "POSTGRES_PASSWORD": secrets.token_urlsafe(36),
        "APP_DB_PASSWORD": secrets.token_urlsafe(36),
        "APP_DB_MIGRATION_PASSWORD": secrets.token_urlsafe(36),
        "APP_ADMIN_CODE": "ADMIN",
        "APP_ADMIN_PASSWORD": secrets.token_urlsafe(36),
        "APP_DEMO_DATA": "true",
        "BACKEND_PORT": "8080",
        "FRONTEND_PORT": "5173",
    }
    with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
        stream.write("".join(f"{key}={value}\n" for key, value in values.items()))
    print("Creado .env privado con credenciales individuales; no se muestran en logs.")
    print("Consulta APP_ADMIN_CODE y APP_ADMIN_PASSWORD localmente para el primer acceso.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    args = parser.parse_args()
    initialize(args.root)


if __name__ == "__main__":
    main()
