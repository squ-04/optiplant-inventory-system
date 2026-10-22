"""Check versionable files without claiming application build coverage."""

import json
from pathlib import Path, PurePosixPath
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
TEMPLATE_SUFFIXES = (".example", ".sample", ".template")
PRIVATE_SUFFIXES = {".pem", ".key", ".p12", ".pfx", ".jks", ".keystore"}
APPLICATION_MARKERS = {
    "backend/pom.xml", "frontend/package.json", "compose.yml", "compose.yaml",
    "docker-compose.yml", "docker-compose.yaml",
}


def path_errors(name):
    """Return policy violations; this is not a content-based secret scanner."""
    path = PurePosixPath(name)
    errors = []
    if (path.name == ".env" or path.name.startswith(".env.")) and not path.name.endswith(TEMPLATE_SUFFIXES):
        errors.append("configuración de entorno no permitida")
    if path.suffix.lower() in PRIVATE_SUFFIXES or "secrets" in path.parts:
        errors.append("posible material de credenciales")
    if path.name == ".DS_Store" or "node_modules" in path.parts or "__pycache__" in path.parts:
        errors.append("archivo local o generado")
    if name in APPLICATION_MARKERS:
        errors.append("ampliar el CI con compilación/pruebas reales antes de incorporar la aplicación o Compose")
    return errors


def reject_nonstandard_constant(value):
    raise ValueError(f"constante no permitida en JSON: {value}")


def check_files(names, root=ROOT):
    errors = []
    for name in names:
        errors.extend(f"{name}: {message}" for message in path_errors(name))
        if PurePosixPath(name).suffix in {".json", ".excalidraw"}:
            try:
                json.loads(
                    (root / name).read_text(encoding="utf-8"),
                    parse_constant=reject_nonstandard_constant,
                )
            except (ValueError, OSError) as error:
                errors.append(f"{name}: JSON inválido o ilegible: {error}")
    return errors


def main():
    # Include untracked, non-ignored files so local bootstrap checks are useful.
    result = subprocess.run(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
        cwd=ROOT, check=True, stdout=subprocess.PIPE,
    )
    names = sorted(set(result.stdout.decode("utf-8").strip("\0").split("\0")) - {""})
    errors = check_files(names)
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(f"Integridad: {len(names)} archivos comprobados; sin compilación de aplicaciones.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
