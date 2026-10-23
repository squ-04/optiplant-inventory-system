"""Single command registry shared by local checks, pre-push and GitHub Actions."""

import argparse
from concurrent.futures import ThreadPoolExecutor
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
CHECKS = {
    "repository": ("Integridad del repositorio", [sys.executable, "scripts/check_repository.py"]),
    "documentation": (
        "Consistencia documental",
        [sys.executable, "-m", "unittest", "discover", "-s", "odd/probes", "-p", "test_*.py", "-v"],
    ),
}


def run_group(name, root):
    label, command = CHECKS[name]
    env = dict(os.environ, PYTHONDONTWRITEBYTECODE="1")
    try:
        result = subprocess.run(command, cwd=root, env=env, stdin=subprocess.DEVNULL,
                                capture_output=True, text=True, errors="replace")
        return label, result.returncode, result.stdout, result.stderr
    except OSError as error:
        return label, 1, "", f"No se pudo ejecutar la comprobación: {error}\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("group", choices=["all", *CHECKS], nargs="?", default="all")
    parser.add_argument("--root", type=Path, default=ROOT,
                        help="Raíz que se comprobará; el hook utiliza una copia temporal del commit.")
    args = parser.parse_args()
    names = list(CHECKS) if args.group == "all" else [args.group]
    # Collect every independent result, even when another group fails.
    with ThreadPoolExecutor(max_workers=len(names)) as executor:
        futures = [executor.submit(run_group, name, args.root) for name in names]
        failed = False
        for future in futures:
            label, code, stdout, stderr = future.result()
            print(f"=== {label}: {'OK' if code == 0 else 'FALLO'} ===", flush=True)
            print(stdout, end="", flush=True)
            print(stderr, end="", file=sys.stderr, flush=True)
            failed |= code != 0
    return int(failed)


if __name__ == "__main__":
    sys.exit(main())
