"""Exercise strict JSON validation through the public checker CLI."""

import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class RepositoryCLITest(unittest.TestCase):
    def test_json_validation_preserves_files_and_reports_exit_status(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            (repo / "scripts").mkdir()
            shutil.copy2(ROOT / "scripts/check_repository.py", repo / "scripts/check_repository.py")
            env = dict(os.environ, PYTHONDONTWRITEBYTECODE="1", HOME=directory, TMPDIR=directory)
            subprocess.run(["git", "init", "-b", "main"], cwd=repo, env=env,
                           capture_output=True, check=True)
            file = repo / "probe.json"
            cases = ((b'{"n":1}', 0), (b'{invalid', 1), (b'{"n":NaN}', 1),
                     (b'{"n":Infinity}', 1), (b'{"n":-Infinity}', 1))
            for content, expected in cases:
                with self.subTest(content=content):
                    file.write_bytes(content)
                    result = subprocess.run(
                        [sys.executable, "scripts/check_repository.py"], cwd=repo,
                        env=env, capture_output=True, text=True,
                    )
                    self.assertEqual(content, file.read_bytes())
                    self.assertEqual(expected, result.returncode)
                    if expected:
                        self.assertEqual("", result.stdout)
                        self.assertTrue(result.stderr.startswith("probe.json: JSON inválido o ilegible: "))
                    else:
                        self.assertEqual("", result.stderr)
                        self.assertEqual(
                            "Integridad: 2 archivos comprobados; sin compilación de aplicaciones.\n",
                            result.stdout,
                        )


if __name__ == "__main__":
    unittest.main()
