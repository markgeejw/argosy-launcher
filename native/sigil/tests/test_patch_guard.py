import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[3]


class SigilPatchGuardTest(unittest.TestCase):
    def configure(self, *, change_source=False, break_patch=False, change_output=False, nested_git=False, change_after_configure=None, generator=None):
        with tempfile.TemporaryDirectory(prefix="argosy-sigil-patch-") as directory:
            root = pathlib.Path(directory)
            integration = root / "native/sigil"
            integration.mkdir(parents=True)
            for name in ("ApplyPatch.cmake", "3ds-extdata.patch"):
                shutil.copyfile(ROOT / "native/sigil" / name, integration / name)
            source = root / "sigil/src"
            source.mkdir(parents=True)
            for name in ("save_profiles.c", "save_layout.c", "sync_profiles.c"):
                shutil.copyfile(ROOT / "sigil/src" / name, source / name)
            if change_source:
                with (source / "save_profiles.c").open("a") as stream:
                    stream.write("\nchanged source\n")
            if break_patch:
                (integration / "3ds-extdata.patch").write_text("not a patch\n")
            if change_output:
                patch = integration / "3ds-extdata.patch"
                patch.write_text(patch.read_text().replace('"0000055d"', '"0000055c"'))
            build = root / "build"
            if nested_git:
                subprocess.run(["git", "init", "--quiet", str(root)], check=True)
                subprocess.run(["git", "init", "--quiet", str(root / "sigil")], check=True)
                build = root / "sigil/bindings/android/.cxx/Debug/arm64-v8a"
            (root / "dummy.c").write_text("int dummy;\n")
            (root / "CMakeLists.txt").write_text(
                "cmake_minimum_required(VERSION 3.22.1)\n"
                "project(patch_guard C)\n"
                "add_library(sigil STATIC dummy.c)\n"
                "include(native/sigil/ApplyPatch.cmake)\n"
                "argosy_patch_sigil()\n"
            )
            result = subprocess.run(
                ["cmake", "-S", str(root), "-B", str(build)] + (["-G", generator] if generator else []),
                text=True, capture_output=True,
            )
            if result.returncode == 0:
                patched = (build / "argosy-sigil-patched/src/save_profiles.c").read_text()
                self.assertIn('{ "00040000/00055e00", "0000055d" }', patched)
                self.assertNotIn("native_ids", (source / "save_profiles.c").read_text())
                if change_after_configure:
                    with (source / change_after_configure).open("a") as stream:
                        stream.write("\nchanged pinned source\n")
                    result = subprocess.run(["cmake", "--build", str(build)], text=True, capture_output=True)
            return result

    def test_patch_applies_to_a_fresh_source_copy_without_modifying_it(self):
        result = self.configure()
        self.assertEqual(0, result.returncode, result.stderr)

    def test_patch_applies_inside_a_nested_git_worktree(self):
        result = self.configure(nested_git=True)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_unexpected_patched_output_fails_closed(self):
        result = self.configure(change_output=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("patched source hash mismatch", result.stderr)

    def test_changed_pinned_source_fails_closed(self):
        result = self.configure(change_source=True)
        self.assertNotEqual(0, result.returncode)
        self.assertRegex(result.stderr, r"differs from pinned\s+794d99fe")

    def test_incremental_build_rechecks_each_pinned_source(self):
        for name in ("save_profiles.c", "save_layout.c", "sync_profiles.c"):
            with self.subTest(name=name):
                result = self.configure(change_after_configure=name)
                self.assertNotEqual(0, result.returncode)
                self.assertRegex(result.stderr, r"differs from pinned\s+794d99fe")

    @unittest.skipUnless(shutil.which("ninja"), "Ninja unavailable")
    def test_ninja_incremental_build_has_no_duplicate_dependency_rules(self):
        result = self.configure(nested_git=True, change_after_configure="save_layout.c", generator="Ninja")
        self.assertNotEqual(0, result.returncode)
        self.assertRegex(result.stderr, r"differs from pinned\s+794d99fe")
        self.assertNotIn("multiple rules generate", result.stderr)

    def test_invalid_patch_fails_closed(self):
        result = self.configure(break_patch=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Sigil patch failed", result.stderr)


if __name__ == "__main__":
    unittest.main()
