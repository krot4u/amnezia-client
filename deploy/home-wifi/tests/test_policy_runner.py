"""Gradle can cache multiple Kotlin versions; the test runner must use one coherent compiler."""
from pathlib import Path
import tempfile
import unittest
from run_policy import compiler_jars


class CompilerSelectionTest(unittest.TestCase):
    def test_uses_one_compiler_with_its_matching_runtime(self):
        with tempfile.TemporaryDirectory() as folder:
            base = Path(folder)
            for version in ['1.9.24', '2.1.0']:
                for artifact in ['kotlin-compiler-embeddable', 'kotlin-stdlib', 'kotlin-script-runtime']:
                    jar = base / 'org.jetbrains.kotlin' / artifact / version / 'hash' / f'{artifact}-{version}.jar'
                    jar.parent.mkdir(parents=True)
                    jar.touch()
            selected = compiler_jars(base)
            self.assertEqual(len(selected), 3)
            self.assertTrue(all('/2.1.0/' in path for path in selected))
            self.assertFalse(any('/1.9.24/' in path for path in selected))


if __name__ == '__main__':
    unittest.main()
