"""Exercise the shared checks and real Git hooks using only local scratch repos."""

import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class SharedChecksTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.repo = self.base / "repo"
        self.repo.mkdir()
        self.env = dict(os.environ, HOME=str(self.base), TMPDIR=str(self.base),
                        PYTHONDONTWRITEBYTECODE="1", GIT_CONFIG_NOSYSTEM="1",
                        GIT_CONFIG_GLOBAL=os.devnull)
        for name in list(self.env):
            if name.startswith("GIT_") and name not in {"GIT_CONFIG_NOSYSTEM", "GIT_CONFIG_GLOBAL"}:
                del self.env[name]
        self.git("init", "-b", "main")
        self.git("config", "user.name", "Test")
        self.git("config", "user.email", "test@example.invalid")
        for name in ("scripts/run_checks.py", "scripts/pre_push.py", "scripts/check_repository.py",
                     ".githooks/pre-push"):
            target = self.repo / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT / name, target)
        hook = self.repo / ".githooks/pre-push"
        hook.chmod(0o755)
        probes = self.repo / "odd/probes"
        probes.mkdir(parents=True)
        (probes / "test_sample.py").write_text(
            "import unittest\nclass Sample(unittest.TestCase):\n def test_ok(self): self.assertTrue(True)\n"
        )

    def git(self, *args, check=True, hooks=False):
        hook_path = str(self.repo / ".githooks") if hooks else os.devnull
        return subprocess.run(["git", "-c", f"core.hooksPath={hook_path}", *args],
                              cwd=self.repo, env=self.env, capture_output=True, text=True, check=check)

    def commit(self):
        self.git("add", "-A")
        self.git("commit", "-m", "test: snapshot")
        return self.git("rev-parse", "HEAD").stdout.strip()

    def run_checks(self, *args):
        return subprocess.run([sys.executable, "scripts/run_checks.py", *args],
                              cwd=self.repo, env=self.env, capture_output=True, text=True)

    def test_shared_groups_pass_and_report_both_failures(self):
        result = self.run_checks()
        self.assertEqual(0, result.returncode, result.stderr)
        (self.repo / "bad.json").write_text("{invalid")
        (self.repo / "odd/probes/test_sample.py").write_text(
            "import unittest\nclass Sample(unittest.TestCase):\n def test_bad(self): self.fail('documentary failure')\n"
        )
        before = (self.repo / "bad.json").read_bytes()
        result = self.run_checks()
        self.assertEqual(1, result.returncode)
        self.assertIn("bad.json", result.stdout + result.stderr)
        self.assertIn("documentary failure", result.stdout + result.stderr)
        self.assertEqual(before, (self.repo / "bad.json").read_bytes())

    def test_existing_group_can_run_independently(self):
        (self.repo / "bad.json").write_text("{invalid")
        self.assertEqual(0, self.run_checks("documentation").returncode)
        self.assertEqual(1, self.run_checks("repository").returncode)

    def test_real_push_rejects_committed_error_even_with_uncommitted_fix(self):
        good = self.commit()
        remote = self.base / "remote.git"
        self.git("init", "--bare", str(remote))
        self.git("remote", "add", "origin", str(remote))
        self.assertEqual(0, self.git("push", "origin", "main", hooks=True).returncode)
        bad = self.repo / "bad.json"
        bad.write_text("{invalid")
        self.commit()
        bad.write_text("{}")
        head = self.git("rev-parse", "HEAD").stdout
        result = self.git("push", "origin", "main", hooks=True, check=False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("bad.json", result.stdout + result.stderr)
        self.assertEqual("{}", bad.read_text())
        self.assertEqual(head, self.git("rev-parse", "HEAD").stdout)
        self.assertEqual(good, self.git("--git-dir", str(remote), "rev-parse", "main").stdout.strip())
        self.commit()
        self.assertEqual(0, self.git("push", "origin", "main", hooks=True).returncode)

    def test_noncurrent_branch_push_and_deletion(self):
        good = self.commit()
        self.git("branch", "good", good)
        (self.repo / "bad.json").write_text("{invalid")
        self.commit()
        remote = self.base / "remote.git"
        self.git("init", "--bare", str(remote))
        self.git("remote", "add", "origin", str(remote))
        self.assertEqual(0, self.git("push", "origin", "good", hooks=True).returncode)
        result = self.git("push", "origin", "main", "good:another", hooks=True, check=False)
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(0, self.git("push", "origin", ":good", hooks=True).returncode)

    def test_export_ignore_cannot_hide_committed_invalid_json(self):
        good = self.commit()
        remote = self.base / "remote.git"
        self.git("init", "--bare", str(remote))
        self.git("remote", "add", "origin", str(remote))
        self.git("push", "origin", "main", hooks=True)
        (self.repo / "bad.json").write_text("{invalid")
        (self.repo / ".gitattributes").write_text("bad.json export-ignore\n")
        bad = self.commit()
        self.assertEqual(1, self.run_checks("repository").returncode)
        result = self.git("push", "origin", "main", hooks=True, check=False)
        self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("bad.json", result.stdout + result.stderr)
        self.assertEqual(good, self.git("--git-dir", str(remote), "rev-parse", "main").stdout.strip())
        self.assertEqual(bad, self.git("rev-parse", "HEAD").stdout.strip())
        self.assertEqual("{invalid", (self.repo / "bad.json").read_text())

    def test_hook_empty_deletion_duplicate_and_malformed_input(self):
        oid = self.commit()
        zero = "0" * 40

        def invoke(data):
            return subprocess.run([str(self.repo / ".githooks/pre-push")], cwd=self.repo,
                                  env=self.env, input=data, capture_output=True, text=True)

        for data in ("", f"(delete) {zero} refs/heads/old {oid}\n"):
            self.assertEqual(0, invoke(data).returncode)
        result = invoke(f"refs/heads/main {oid} refs/heads/main {zero}\n"
                        f"refs/heads/other {oid} refs/heads/other {zero}\n")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(1, result.stdout.count("pre-push: comprobando"))
        result = invoke("invalid\n")
        self.assertEqual(1, result.returncode)
        self.assertEqual("Push cancelado: no se pudo verificar el contenido: entrada pre-push inválida\n",
                         result.stderr)

    def test_export_subst_does_not_transform_checked_content(self):
        (self.repo / "sample.txt").write_text("$Format:%H$")
        (self.repo / ".gitattributes").write_text("sample.txt export-subst\n")
        (self.repo / "odd/probes/test_sample.py").write_text(
            "from pathlib import Path\nimport unittest\nclass Sample(unittest.TestCase):\n"
            " def test_raw(self): self.assertEqual('$Format:' + '%H$', Path('sample.txt').read_text())\n"
        )
        self.commit()
        remote = self.base / "remote.git"
        self.git("init", "--bare", str(remote))
        result = self.git("push", str(remote), "main", hooks=True, check=False)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual("$Format:%H$", (self.repo / "sample.txt").read_text())

    @unittest.skipUnless(shutil.which("docker"), "Docker CLI required for plugin discovery")
    def test_hook_discovers_plugins_without_reusing_docker_credentials(self):
        for explicit_config in (False, True):
            with self.subTest(explicit_config=explicit_config):
                config = self.base / ("custom-docker" if explicit_config else ".docker")
                plugins = config / "cli-plugins"
                plugins.mkdir(parents=True, exist_ok=True)
                (config / "config.json").write_text(
                    '{"auths":{"private.example":{"auth":"DO_NOT_COPY"}},'
                    '"currentContext":"DO_NOT_REUSE"}'
                )
                plugin = plugins / "docker-optiplantprobe"
                plugin.write_text(
                    "#!/bin/sh\n"
                    'if [ "$1" = docker-cli-plugin-metadata ]; then\n'
                    "  printf '%s\\n' '{\"SchemaVersion\":\"0.1.0\",\"Vendor\":\"OptiPlant\","
                    "\"Version\":\"1.0.0\",\"ShortDescription\":\"Discovery probe\"}'\n"
                    "else\n  printf 'plugin-discovered\\n'\nfi\n"
                )
                plugin.chmod(0o755)
                if explicit_config:
                    self.env["DOCKER_CONFIG"] = str(config)
                else:
                    self.env.pop("DOCKER_CONFIG", None)
                (self.repo / "odd/probes/test_sample.py").write_text(
                    "import json, os, subprocess, unittest\nfrom pathlib import Path\n"
                    "class Sample(unittest.TestCase):\n"
                    " def test_plugin(self):\n"
                    "  result = subprocess.run(['docker', 'optiplantprobe'], capture_output=True, text=True)\n"
                    "  self.assertEqual(0, result.returncode, result.stderr)\n"
                    "  self.assertEqual('plugin-discovered\\n', result.stdout)\n"
                    "  home = Path(os.environ['HOME'])\n"
                    "  config = Path(os.environ.get('DOCKER_CONFIG', str(home / '.docker')))\n"
                    "  self.assertTrue(config.is_relative_to(home))\n"
                    "  settings = json.loads((config / 'config.json').read_text())\n"
                    "  self.assertEqual({'cliPluginsExtraDirs'}, set(settings))\n"
                    f"  self.assertEqual([{str(plugins.resolve())!r}], settings['cliPluginsExtraDirs'])\n"
                    "  self.assertFalse((config / 'contexts').exists())\n"
                )
                original = (config / "config.json").read_bytes()
                oid = self.commit()
                result = subprocess.run(
                    [str(self.repo / ".githooks/pre-push")], cwd=self.repo,
                    env=self.env, input=f"refs/heads/main {oid} refs/heads/main {'0' * 40}\n",
                    capture_output=True, text=True,
                )
                self.assertEqual(0, result.returncode, result.stdout + result.stderr)
                self.assertEqual(original, (config / "config.json").read_bytes())


if __name__ == "__main__":
    unittest.main()
