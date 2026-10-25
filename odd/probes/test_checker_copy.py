"""Independent regression for checker source isolation (risk item 5)."""
from pathlib import Path
import hashlib
import subprocess
import tempfile
import unittest
from unittest import mock
from scripts import check_application as checker


class CheckerCopyTest(unittest.TestCase):
    def test_only_sources_are_copied_without_mutation(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / 'frontend'
            source.mkdir()
            for name in ('node_modules', 'dist', '.vite', '.pnpm-store'):
                (source / name).mkdir()
                (source / name / 'foreign').write_bytes(b'host-state')
            for name in ('.env', '.env.local', '.env.production', 'package.json', 'pnpm-lock.yaml'):
                (source / name).write_text(name)

            def hashes():
                return {str(p.relative_to(source)): hashlib.sha256(p.read_bytes()).hexdigest()
                        for p in source.rglob('*') if p.is_file()}

            before = hashes()
            with mock.patch.object(checker, 'ROOT', root), mock.patch('sys.argv', ['check', 'frontend']), \
                    mock.patch.object(checker.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0)) as run:
                self.assertEqual(0, checker.main())
            command = run.call_args.args[0][-1].split('cd /work;', 1)[0]
            command = command.replace('/source', str(source)).replace('/work', str(root / 'work'))
            command = command.replace('/tmp/frontend-source.tar', str(root / 'source.tar'))
            result = subprocess.run(['sh', '-ec', command], capture_output=True, text=True)
            self.assertEqual((0, '', ''), (result.returncode, result.stdout, result.stderr))
            self.assertEqual(['package.json', 'pnpm-lock.yaml'], sorted(p.name for p in (root / 'work').iterdir()))
            for name in ('package.json', 'pnpm-lock.yaml'):
                self.assertEqual((source / name).read_bytes(), (root / 'work' / name).read_bytes())
            self.assertEqual(before, hashes())
