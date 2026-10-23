import importlib.util
import os
import pathlib
import re
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/kaegr-release.yml"
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
        self.assertIn('versionName = "2.24.0-kaegr.2"', gradle)
        debug_strings = (ROOT / "app/src/debug/res/values/strings.xml").read_text()
        self.assertIn("Argosy kaegr DBG", debug_strings)

    def test_self_update_uses_fork(self):
        source = (ROOT / "app/src/main/kotlin/com/nendo/argosy/data/remote/github/GitHubApi.kt").read_text()
        self.assertIn('repos/markgeejw/argosy-launcher/releases/latest', source)
        self.assertIn('repos/markgeejw/argosy-launcher/releases"', source)
        self.assertNotIn('repos/nendotools/argosy-launcher/releases', source)

    def test_release_fails_closed_and_checks_signature(self):
        self.assertTrue(WORKFLOW.exists())
        source = WORKFLOW.read_text()
        for required in ["v*-kaegr.*", "workflow_dispatch:", "release_tag:", "KAEGR_KEYSTORE_BASE64", "KAEGR_KEYSTORE_PASSWORD", "KAEGR_KEY_ALIAS", "KAEGR_SIGNER_SHA256", "keytool -list", "apksigner", "aapt2", "if: always()", "-PquayPassDark", "--draft --prerelease --verify-tag"]:
            self.assertIn(required, source)
        for required in ["verify_kaegr_release_version.py", "--apk-badging", "identity-signer:", "identity-signature:", "identity-badging:", "identity-version:", "identity-package:"]:
            self.assertIn(required, source)
        self.assertNotIn("storePassword=android", source)
        gradle = (ROOT / "app/build.gradle.kts").read_text()
        self.assertNotIn('signingConfigs.getByName("debug")', gradle)

    def test_release_runs_lint_and_each_unit_variant_before_writing_signing_material(self):
        source = WORKFLOW.read_text()
        lint_start = source.index("      - name: Run full lint")
        debug_test_start = source.index("      - name: Run debug unit tests")
        release_test_start = source.index("      - name: Run release unit tests")
        signing_start = source.index("      - name: Write signing config")
        build_start = source.index("      - name: Build signed release")
        self.assertLess(lint_start, debug_test_start)
        self.assertLess(debug_test_start, release_test_start)
        self.assertLess(release_test_start, signing_start)
        self.assertLess(signing_start, build_start)
        for command in ["./gradlew lint", "./gradlew testDebugUnitTest", "./gradlew testReleaseUnitTest", "./gradlew assembleRelease"]:
            self.assertIn(command, source)
        self.assertEqual(1, source.count("./gradlew testDebugUnitTest"))
        self.assertEqual(1, source.count("./gradlew testReleaseUnitTest"))
        self.assertEqual(4, source.count("-Pkotlin.compiler.execution.strategy=in-process"))
        self.assertEqual(4, source.count("-Xmx12g -XX:+UseParallelGC -XX:MaxMetaspaceSize=1g"))

    def test_release_selects_tag_for_push_or_dispatch_and_checks_out_that_tag(self):
        source = WORKFLOW.read_text()
        self.assertIn("RELEASE_TAG: ${{ inputs.release_tag || github.ref_name }}", source)
        self.assertIn("ref: refs/tags/${{ inputs.release_tag || github.ref_name }}", source)
        self.assertIn('git rev-parse "refs/tags/$RELEASE_TAG^{}"', source)
        self.assertIn('git rev-parse HEAD', source)
        self.assertLess(source.index("Verify checked out release tag"), source.index("Write signing config"))
        self.assertIn('gh release create "$RELEASE_TAG" "$apk"', source)
        self.assertIn("--repo markgeejw/argosy-launcher", source)

    def test_identity_reports_are_public_artifacts_and_draft_waits_for_the_guard(self):
        source = WORKFLOW.read_text()
        self.assertIn("Upload APK identity reports", source)
        self.assertIn("${{ runner.temp }}/apk-signature.txt", source)
        self.assertIn("${{ runner.temp }}/apk-badging.txt", source)
        artifact_start = source.index("      - name: Upload APK identity reports")
        draft_start = source.index("      - name: Prepare draft prerelease")
        self.assertLess(artifact_start, draft_start)
        self.assertNotIn("if: always()", source[draft_start:])

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

    def identity_guard_result(self, *, expected="0" * 64, actual="0" * 64, apksigner_mode="ok", package="com.nendo.argosy.kaegr", version="2.24.0-kaegr.2", code="3035802"):
        source = WORKFLOW.read_text()
        match = re.search(
            r"      - name: Verify signed APK identity\n.*?        run: \|\n(?P<body>.*?)(?=\n      - name: Upload APK identity reports)",
            source,
            re.DOTALL,
        )
        self.assertIsNotNone(match)
        body = "\n".join(line.removeprefix("          ") for line in match.group("body").splitlines())
        with tempfile.TemporaryDirectory() as temp:
            temp_path = pathlib.Path(temp)
            tools = temp_path / "android" / "build-tools" / "35.0.0"
            tools.mkdir(parents=True)
            (tools / "apksigner").write_text(
                "#!/bin/sh\n"
                "if [ \"$MOCK_APKSIGNER_MODE\" = nonzero ]; then echo verifier-failed >&2; exit 9; fi\n"
                "if [ \"$MOCK_APKSIGNER_MODE\" = missing ]; then echo verified; exit 0; fi\n"
                "echo \"certificate SHA-256 digest: $MOCK_ACTUAL\"\n"
            )
            (tools / "aapt2").write_text(
                "#!/bin/sh\n"
                "echo \"package: name='$MOCK_PACKAGE' versionCode='$MOCK_CODE' versionName='$MOCK_VERSION'\"\n"
            )
            for tool in (tools / "apksigner", tools / "aapt2"):
                tool.chmod(0o755)
            script = temp_path / "identity-guard.sh"
            script.write_text("#!/bin/sh\nset -e\n" + body + "\n")
            env = os.environ | {
                "ANDROID_HOME": str(temp_path / "android"),
                "RUNNER_TEMP": str(temp_path),
                "EXPECTED_SIGNER_SHA256": expected,
                "MOCK_ACTUAL": actual,
                "MOCK_APKSIGNER_MODE": apksigner_mode,
                "MOCK_PACKAGE": package,
                "MOCK_VERSION": version,
                "MOCK_CODE": code,
                "RELEASE_TAG": "v2.24.0-kaegr.2",
            }
            return subprocess.run(["sh", str(script)], cwd=ROOT, env=env, text=True, capture_output=True)

    def test_identity_guard_accepts_expected_apk(self):
        result = self.identity_guard_result()
        self.assertEqual(0, result.returncode, result.stderr)

    def test_identity_guard_rejects_missing_signer_digest(self):
        result = self.identity_guard_result(apksigner_mode="missing")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("identity-signer: apksigner report has no certificate", result.stderr)

    def test_identity_guard_rejects_malformed_configured_pin(self):
        result = self.identity_guard_result(expected="not-a-digest")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("identity-signer: configured signer digest is missing or malformed", result.stderr)

    def test_identity_guard_rejects_malformed_actual_digest(self):
        result = self.identity_guard_result(actual="not-a-digest")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("identity-signer: apksigner reported a malformed certificate", result.stderr)

    def test_identity_guard_rejects_signer_mismatch(self):
        result = self.identity_guard_result(actual="1" * 64)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("identity-signer: certificate SHA-256 digest does not match", result.stderr)

    def test_identity_guard_reports_apksigner_failure(self):
        result = self.identity_guard_result(apksigner_mode="nonzero")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("identity-signature: apksigner failed", result.stderr)
        self.assertIn("verifier-failed", result.stderr)

    def test_identity_guard_rejects_wrong_package_version_and_code(self):
        for values, label in [
            ({"package": "com.example.other"}, "identity-package:"),
            ({"version": "2.24.0-kaegr.1"}, "identity-version:"),
            ({"code": "3035801"}, "identity-version:"),
        ]:
            with self.subTest(values=values):
                result = self.identity_guard_result(**values)
                self.assertNotEqual(0, result.returncode)
                self.assertIn(label, result.stderr)


if __name__ == "__main__":
    unittest.main()
