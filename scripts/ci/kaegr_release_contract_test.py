import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]


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
        self.assertNotIn("storePassword=android", source)


if __name__ == "__main__":
    unittest.main()
