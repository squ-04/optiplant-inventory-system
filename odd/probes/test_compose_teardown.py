"""Scripts that start their own Compose project must leave no images or volumes behind."""

from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
DOWN_CALL = re.compile(r'compose\("down"[^)]*\)')


class ComposeTeardownTest(unittest.TestCase):
    def test_isolated_projects_remove_their_images_and_volumes(self):
        scripts = [path for path in sorted((ROOT / "scripts").glob("*.py"))
                   if '"--project-name"' in path.read_text(encoding="utf-8")]
        self.assertTrue(scripts, "no isolated Compose scripts found")
        for path in scripts:
            with self.subTest(script=path.name):
                # Each run builds `<unique project>-<service>` images; `down` keeps them unless told otherwise.
                calls = [call for call in DOWN_CALL.findall(path.read_text(encoding="utf-8"))
                         if '"--volumes"' in call]
                self.assertTrue(calls, "missing final `down --volumes`")
                for call in calls:
                    self.assertIn('"--rmi", "local"', call)


if __name__ == "__main__":
    unittest.main()
