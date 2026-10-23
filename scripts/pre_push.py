"""Validate each outgoing tip in isolation, never uncommitted working-tree fixes."""

import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def materialize(commit, snapshot):
    """Read raw blobs: export-ignore, export-subst and checkout filters cannot hide data."""
    records = subprocess.check_output(
        ["git", "ls-tree", "-rz", "--full-tree", commit], cwd=ROOT,
    )
    links = []
    for record in records.split(b"\0"):
        if not record:
            continue
        metadata, raw_name = record.split(b"\t", 1)
        mode, kind, oid = metadata.decode("ascii").split()
        target = snapshot / os.fsdecode(raw_name)
        if not target.resolve().is_relative_to(snapshot):
            raise ValueError("ruta fuera del snapshot")
        if kind != "blob" or mode not in {"100644", "100755", "120000"}:
            raise ValueError("tipo de archivo no soportado; no se omite contenido de submódulos")
        content = subprocess.check_output(["git", "cat-file", "blob", oid], cwd=ROOT)
        target.parent.mkdir(parents=True, exist_ok=True)
        if mode == "120000":
            links.append((target, os.fsdecode(content)))
        else:
            target.write_bytes(content)
            target.chmod(0o755 if mode == "100755" else 0o644)
    for target, link in links:
        if not (target.parent / link).resolve().is_relative_to(snapshot):
            raise ValueError("enlace simbólico fuera del snapshot")
        target.symlink_to(link)
    # Recheck after all links exist, including indirect link chains.
    for target, _ in links:
        if not target.resolve().is_relative_to(snapshot):
            raise ValueError("enlace simbólico fuera del snapshot")


def main():
    if sys.version_info < (3, 10):
        print("pre-push requiere Python 3.10 o posterior.", file=sys.stderr)
        return 1
    try:
        tips = []
        for line in sys.stdin:
            fields = line.split()
            if len(fields) != 4 or not re.fullmatch(r"[0-9a-fA-F]{40}|[0-9a-fA-F]{64}", fields[1]):
                raise ValueError("entrada pre-push inválida")
            oid = fields[1]
            if set(oid) != {"0"}:
                commit = subprocess.check_output(
                    ["git", "rev-parse", "--verify", f"{oid}^{{commit}}"], cwd=ROOT, text=True,
                ).strip()
                if commit not in tips:
                    tips.append(commit)
        if not tips:
            return 0  # Ref deletions and no-op pushes have no candidate to validate.
        local_vars = subprocess.check_output(
            ["git", "rev-parse", "--local-env-vars"], cwd=ROOT, text=True,
        ).splitlines()
        env = {key: value for key, value in os.environ.items() if key not in local_vars}
        env["PYTHONDONTWRITEBYTECODE"] = "1"
        failed = False
        for commit in tips:
            print(f"pre-push: comprobando {commit[:12]} en una copia aislada.", flush=True)
            with tempfile.TemporaryDirectory(prefix="optiplant-pre-push-") as directory:
                base = Path(directory).resolve()
                snapshot = base / "repo"
                snapshot.mkdir()
                materialize(commit, snapshot)
                # The existing integrity check enumerates versioned paths with Git.
                for arguments in (["init", "--quiet", "--template="], ["add", "--force", "--all"]):
                    subprocess.run(["git", "-c", f"core.hooksPath={os.devnull}", *arguments],
                                   cwd=snapshot, env=env, check=True, capture_output=True)
                for name in ("home", "tmp"):
                    (base / name).mkdir()
                check_env = dict(env, HOME=str(base / "home"), TMPDIR=str(base / "tmp"))
                result = subprocess.run(
                    [sys.executable, str(ROOT / "scripts/run_checks.py"), "--root", str(snapshot)],
                    cwd=snapshot, env=check_env, stdin=subprocess.DEVNULL,
                )
                failed |= result.returncode != 0
        if failed:
            print("Push cancelado: corrige las comprobaciones y crea el commit correspondiente.", file=sys.stderr)
        return int(failed)
    except (OSError, ValueError, RuntimeError, subprocess.CalledProcessError) as error:
        print(f"Push cancelado: no se pudo verificar el contenido: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
