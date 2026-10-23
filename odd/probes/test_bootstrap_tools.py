"""Public CLI regression checks for local environment provisioning."""

import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class EnvironmentTest(unittest.TestCase):
    def run_init(self, directory):
        return subprocess.run(
            [sys.executable, str(ROOT / "scripts/init_environment.py"), "--root", directory],
            capture_output=True, text=True,
        )

    def test_individual_secrets_private_mode_and_idempotence(self):
        with tempfile.TemporaryDirectory() as directory:
            result = self.run_init(directory)
            self.assertEqual(0, result.returncode, result.stderr)
            target = Path(directory) / ".env"
            original = target.read_bytes()
            self.assertEqual(0o600, target.stat().st_mode & 0o777)
            values = dict(line.split("=", 1) for line in original.decode().splitlines())
            self.assertGreaterEqual(len(values["APP_ADMIN_PASSWORD"]), 32)
            self.assertNotEqual(values["APP_ADMIN_PASSWORD"], values["POSTGRES_PASSWORD"])
            self.assertNotEqual(values["APP_DB_PASSWORD"], values["POSTGRES_PASSWORD"])
            self.assertNotIn(values["APP_ADMIN_PASSWORD"], result.stdout + result.stderr)
            self.assertEqual(0, self.run_init(directory).returncode)
            self.assertEqual(original, target.read_bytes())

    def test_existing_user_configuration_is_never_replaced(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / ".env"
            target.write_text("CUSTOM=user-owned\n")
            result = self.run_init(directory)
            self.assertEqual(0, result.returncode)
            self.assertEqual("CUSTOM=user-owned\n", target.read_text())

    def test_unknown_option_is_rejected_without_mutation(self):
        with tempfile.TemporaryDirectory() as directory:
            result = subprocess.run(
                [sys.executable, str(ROOT / "scripts/init_environment.py"),
                 "--root", directory, "--replace"], capture_output=True, text=True,
            )
            self.assertEqual(2, result.returncode)
            self.assertFalse((Path(directory) / ".env").exists())


class ApplicationRunnerTest(unittest.TestCase):
    def test_documentary_snapshot_and_partial_application_are_distinguished(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "scripts").mkdir()
            shutil.copy2(ROOT / "scripts/check_application.py", root / "scripts/check_application.py")
            command = [sys.executable, "scripts/check_application.py", "frontend"]
            result = subprocess.run(command, cwd=root, capture_output=True, text=True)
            self.assertEqual(0, result.returncode)
            self.assertIn("snapshot documental", result.stdout)
            (root / "backend").mkdir()
            (root / "backend/pom.xml").write_text("<project/>")
            result = subprocess.run(command, cwd=root, capture_output=True, text=True)
            self.assertEqual(1, result.returncode)
            self.assertIn("Falta frontend/package.json", result.stderr)

    def test_frontend_executes_pinned_runner_and_propagates_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "scripts").mkdir()
            (root / "frontend").mkdir()
            (root / "frontend/package.json").write_text("{}")
            shutil.copy2(ROOT / "scripts/check_application.py", root / "scripts/check_application.py")
            executable = root / "docker"
            executable.write_text('#!/bin/sh\nprintf "%s\\n" "$DOCKER_HOST" "${DOCKER_CONTEXT:-unset}" "$@" > "$RUNNER_LOG"\nexit 7\n')
            executable.chmod(0o755)
            log = root / "runner.log"
            env = dict(os.environ, PATH=str(root) + os.pathsep + os.environ["PATH"], RUNNER_LOG=str(log),
                       DOCKER_HOST="ssh://unauthorized.invalid", DOCKER_CONTEXT="remote")
            result = subprocess.run([sys.executable, "scripts/check_application.py", "frontend"],
                                    cwd=root, env=env, capture_output=True, text=True)
            self.assertEqual(7, result.returncode)
            invocation = log.read_text()
            self.assertTrue(invocation.startswith("unix:///var/run/docker.sock\nunset\n"))
            self.assertNotIn("unauthorized.invalid", invocation)
            self.assertIn("node:24.22.1-bookworm-slim", invocation)
            self.assertIn("pnpm install --frozen-lockfile; pnpm test; pnpm build", invocation)


if __name__ == "__main__":
    unittest.main()
