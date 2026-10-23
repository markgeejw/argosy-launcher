import importlib.util
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
VERSION_GUARD_SPEC = importlib.util.spec_from_file_location(
    "verify_kaegr_release_version", ROOT / "scripts/ci/verify_kaegr_release_version.py"
)
assert VERSION_GUARD_SPEC is not None
assert VERSION_GUARD_SPEC.loader is not None
version_guard = importlib.util.module_from_spec(VERSION_GUARD_SPEC)
VERSION_GUARD_SPEC.loader.exec_module(version_guard)


class ForkReleaseContractTest(unittest.TestCase):
    def test_fork_identity(self):
        gradle = (ROOT / "app/build.gradle.kts").read_text()
        self.assertIn('applicationId = "com.nendo.argosy.kaegr"', gradle)
        self.assertIn('namespace = "com.nendo.argosy"', gradle)
        self.assertIn('versionName = "2.24.0-kaegr.1"', gradle)
        debug_strings = (ROOT / "app/src/debug/res/values/strings.xml").read_text()
        self.assertIn("Argosy kaegr DBG", debug_strings)

    def test_self_update_uses_fork(self):
        source = (ROOT / "app/src/main/kotlin/com/nendo/argosy/data/remote/github/GitHubApi.kt").read_text()
        self.assertIn('repos/markgeejw/argosy-launcher/releases/latest', source)
        self.assertIn('repos/markgeejw/argosy-launcher/releases"', source)
        self.assertNotIn('repos/nendotools/argosy-launcher/releases', source)

    def test_release_fails_closed_and_checks_signature(self):
        path = ROOT / ".github/workflows/kaegr-release.yml"
        self.assertTrue(path.exists())
        source = path.read_text()
        for required in ["v*-kaegr.*", "KAEGR_KEYSTORE_BASE64", "KAEGR_KEYSTORE_PASSWORD", "KAEGR_KEY_ALIAS", "KAEGR_SIGNER_SHA256", "keytool -list", "apksigner", "aapt2", "if: always()", "-PquayPassDark"]:
            self.assertIn(required, source)
        for required in ["verify_kaegr_release_version.py", "--apk-badging"]:
            self.assertIn(required, source)
        self.assertNotIn("storePassword=android", source)
        gradle = (ROOT / "app/build.gradle.kts").read_text()
        self.assertNotIn('signingConfigs.getByName("debug")', gradle)

    def test_version_guard_accepts_the_current_fork_version(self):
        self.assertEqual(
            3_035_801,
            version_guard.validate("2.24.0-kaegr.1", 35_801, "v2.24.0-kaegr.1", 3_035_801),
        )

    def test_version_guard_rejects_a_tag_suffix_and_source_code_mismatch(self):
        with self.assertRaisesRegex(ValueError, "versionCode 35801 does not match expected 35802"):
            version_guard.validate("2.24.0-kaegr.2", 35_801, "v2.24.0-kaegr.2")

    def test_version_guard_rejects_an_unmapped_upstream_version(self):
        for upstream in ("2.24.1", "2.25.0", "3.0.0"):
            with self.subTest(upstream=upstream), self.assertRaises(ValueError):
                version_guard.validate(f"{upstream}-kaegr.1", 35_801, f"v{upstream}-kaegr.1")

    def test_version_guard_rejects_the_wrong_universal_apk_code(self):
        with self.assertRaisesRegex(ValueError, "APK versionCode 3035802 does not match expected 3035801"):
            version_guard.validate("2.24.0-kaegr.1", 35_801, "v2.24.0-kaegr.1", 3_035_802)


if __name__ == "__main__":
    unittest.main()
