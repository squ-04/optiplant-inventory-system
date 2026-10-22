"""Regression checks for the repository policy, independent of Git history."""

import importlib.util
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location(
    "check_repository", Path(__file__).resolve().parents[2] / "scripts/check_repository.py"
)
CHECKS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CHECKS)


class RepositoryChecksTest(unittest.TestCase):
    def test_sensitive_and_generated_paths_are_rejected(self):
        for name in (".env", "frontend/.env.local", "secrets/password.txt", "key.p12",
                     ".DS_Store", "frontend/node_modules/package/index.js"):
            with self.subTest(name=name):
                self.assertTrue(CHECKS.path_errors(name))

    def test_templates_lockfiles_wrapper_and_migrations_are_allowed(self):
        for name in (".env.example", "frontend/.env.test.example", "pnpm-lock.yaml",
                     "backend/.mvn/wrapper/maven-wrapper.jar", "backend/db/V1__init.sql"):
            with self.subTest(name=name):
                self.assertEqual([], CHECKS.path_errors(name))

    def test_application_requires_expanding_ci(self):
        for name in CHECKS.APPLICATION_MARKERS:
            with self.subTest(name=name):
                self.assertTrue(CHECKS.path_errors(name))

    def test_json_and_excalidraw_are_parsed_without_rewriting(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in ("diagram.excalidraw", "config.json"):
                file = root / name
                file.write_text('{"valid": true}', encoding="utf-8")
                before = file.read_bytes()
                self.assertEqual([], CHECKS.check_files([name], root))
                self.assertEqual(before, file.read_bytes())
                file.write_text("{invalid", encoding="utf-8")
                self.assertEqual(1, len(CHECKS.check_files([name], root)))


if __name__ == "__main__":
    unittest.main()
