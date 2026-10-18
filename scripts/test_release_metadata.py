import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from release_metadata import package, validate


class ReleaseMetadataTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        (self.root / 'VERSION').write_text('0.1.0-alpha.1\n')
        (self.root / 'CHANGELOG.md').write_text('# Changelog\n\n## [Unreleased]\n\n## [0.1.0-alpha.1] - 2026-10-19\n\n### Added\n- A real change.\n')

    def test_version_needs_matching_release_notes(self):
        (self.root / 'VERSION').write_text('0.2.0')
        with self.assertRaisesRegex(ValueError, 'dated'):
            validate(self.root)

    def test_numeric_prerelease_cannot_have_leading_zero(self):
        (self.root / 'VERSION').write_text('0.1.0-alpha.01')
        with self.assertRaisesRegex(ValueError, 'semantic'):
            validate(self.root)

    def test_duplicate_history_is_rejected(self):
        p = self.root / 'CHANGELOG.md'
        p.write_text(p.read_text() + '\n## [0.1.0-alpha.1] - 2026-10-20\n- Duplicate.\n')
        with self.assertRaisesRegex(ValueError, 'unique'):
            validate(self.root)

    def test_invalid_date_is_rejected(self):
        p = self.root / 'CHANGELOG.md'
        p.write_text(p.read_text().replace('2026-10-19', '2026-02-30'))
        with self.assertRaises(ValueError):
            validate(self.root)

    def test_install_package_is_traceable_to_exact_bytes_and_commit(self):
        apk = self.root / 'input.apk'
        apk.write_bytes(b'apk-fixture-bytes')
        out = self.root / 'output'
        target = package(apk, 47, 'a' * 40, out, self.root)
        info = json.loads((out / 'build-info.json').read_text())
        self.assertEqual(target.read_bytes(), apk.read_bytes())
        self.assertEqual(info['sha256'], hashlib.sha256(apk.read_bytes()).hexdigest())
        self.assertEqual(info['versionCode'], 47)
        self.assertEqual(info['commit'], 'a' * 40)
        self.assertIn('A real change.', (out / 'release-notes.md').read_text())
        self.assertNotIn('Unreleased', (out / 'release-notes.md').read_text())
        self.assertIn(target.name, (out / 'SHA256SUMS.txt').read_text())


if __name__ == '__main__':
    unittest.main()
