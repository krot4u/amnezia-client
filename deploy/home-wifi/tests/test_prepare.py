"""The release gate must refuse an incompatible or dirty upstream without overwriting it."""
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

PREPARE = Path(__file__).resolve().parents[1] / 'prepare.py'


class PrepareReleaseTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='amnezia-home-prepare-')
        self.addCleanup(self.tmp.cleanup)
        self.source = Path(self.tmp.name)
        self.cmake = self.source / 'CMakeLists.txt'
        self.cmake.write_text('set(APP_ANDROID_VERSION_CODE 1)\n')
        self.git('init', '-q')
        self.git('add', 'CMakeLists.txt')
        self.git('-c', 'user.name=Test', '-c', 'user.email=test@localhost', 'commit', '-qm', 'fixture')

    def git(self, *args):
        return subprocess.run(['git', *args], cwd=self.source, check=True, capture_output=True)

    def prepare(self):
        return subprocess.run([sys.executable, str(PREPARE), str(self.source), '--version-code', '1000000'],
                              capture_output=True, text=True)

    def test_incompatible_upstream_is_not_modified(self):
        before = self.cmake.read_bytes()
        self.assertNotEqual(self.prepare().returncode, 0)
        self.assertEqual(self.cmake.read_bytes(), before)
        self.assertFalse(list(self.source.rglob('HomeWifiService.kt')))

    def test_dirty_upstream_is_not_modified(self):
        self.cmake.write_text('local changes\n')
        result = self.prepare()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('must be clean', result.stderr)
        self.assertEqual(self.cmake.read_text(), 'local changes\n')


if __name__ == '__main__':
    unittest.main()
