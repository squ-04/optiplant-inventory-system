"""Run reproducible application checks using Docker, never host Java or Node."""

import argparse
import os
from pathlib import Path
import subprocess
import sys
import uuid

ROOT = Path(__file__).resolve().parents[1]


def local_environment():
    env = dict(os.environ, DOCKER_HOST="unix:///var/run/docker.sock")
    env.pop("DOCKER_CONTEXT", None)
    return env


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("group", choices=["backend", "frontend", "integration"])
    args = parser.parse_args()
    image = None
    marker = {"backend": "backend/pom.xml", "frontend": "frontend/package.json",
              "integration": "compose.yaml"}[args.group]
    if not (ROOT / marker).is_file():
        if any((ROOT / name).exists() for name in
               ("backend/pom.xml", "frontend/package.json", "compose.yaml", "compose.yml",
                "docker-compose.yaml", "docker-compose.yml")):
            print(f"Falta {marker}; preparación técnica incompleta.", file=sys.stderr)
            return 1
        print("No aplica: snapshot documental anterior a las aplicaciones.")
        return 0
    if args.group == "integration":
        command = [sys.executable, "scripts/smoke_stack.py"]
    elif args.group == "frontend":
        command = [
            "docker", "run", "--rm", "--mount", f"type=bind,src={ROOT / 'frontend'},dst=/source,readonly",
            "node:24.22.1-bookworm-slim", "sh", "-ec",
            "cp -a /source /work; cd /work; npm install --global pnpm@11.24.0; "
            "pnpm install --frozen-lockfile; pnpm test; pnpm build",
        ]
    else:
        image = "optiplant-backend-verify:" + uuid.uuid4().hex
        build = subprocess.run(
            ["docker", "build", "--target", "build", "--tag", image, str(ROOT / "backend")],
            cwd=ROOT, env=local_environment(),
            check=False,
        )
        if build.returncode:
            return build.returncode
        command = [
            "docker", "run", "--rm", "--add-host", "host.docker.internal:host-gateway",
            "--mount", f"type=bind,src={ROOT / 'backend'},dst=/source,readonly",
            "--mount", "type=bind,src=/var/run/docker.sock,dst=/var/run/docker.sock",
            "-e", "TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal",
            image, "sh", "-ec",
            "cp -a /source /work; cd /work; mvn --batch-mode --no-transfer-progress verify",
        ]
    try:
        # Only the explicitly local socket is authorized; never follow an ambient remote context.
        return subprocess.run(command, cwd=ROOT, env=local_environment(), check=False).returncode
    except OSError as error:
        print(f"Comprobación no disponible: {error}", file=sys.stderr)
        return 1
    finally:
        if image:
            subprocess.run(["docker", "image", "rm", image], env=local_environment(),
                           stdout=subprocess.DEVNULL, check=False)


if __name__ == "__main__":
    sys.exit(main())
