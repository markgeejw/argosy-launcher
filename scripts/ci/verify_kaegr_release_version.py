import argparse
import pathlib
import re

FORK_VERSION = re.compile(r"^(\d+)\.(\d+)\.(\d+)-kaegr\.([1-9]\d?)$")
VERSION_NAME = re.compile(r'^\s*versionName = "([^"]+)"', re.MULTILINE)
VERSION_CODE = re.compile(r"^\s*versionCode = (\d+)", re.MULTILINE)
APK_VERSION_CODE = re.compile(r"\bversionCode='(\d+)'")
UPSTREAM_VERSION_CODE = 358
UNIVERSAL_VERSION_CODE_PREFIX = 3_000_000


def expected_base_version_code(version_name: str) -> int:
    match = FORK_VERSION.fullmatch(version_name)
    if match is None:
        raise ValueError(f"versionName must match <major>.<minor>.<patch>-kaegr.<1-99>: {version_name}")
    return UPSTREAM_VERSION_CODE * 100 + int(match.group(4))


def validate(version_name: str, version_code: int, tag: str, apk_version_code: int | None = None) -> int:
    if tag != f"v{version_name}":
        raise ValueError(f"tag {tag} does not match versionName {version_name}")
    expected_base = expected_base_version_code(version_name)
    if version_code != expected_base:
        raise ValueError(f"versionCode {version_code} does not match expected {expected_base}")
    expected_universal = UNIVERSAL_VERSION_CODE_PREFIX + expected_base
    if apk_version_code is not None and apk_version_code != expected_universal:
        raise ValueError(f"APK versionCode {apk_version_code} does not match expected {expected_universal}")
    return expected_universal


def source_version(source: pathlib.Path) -> tuple[str, int]:
    text = source.read_text()
    version_name = VERSION_NAME.search(text)
    version_code = VERSION_CODE.search(text)
    if version_name is None or version_code is None:
        raise ValueError(f"could not read versionName and versionCode from {source}")
    return version_name.group(1), int(version_code.group(1))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=pathlib.Path, required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--apk-badging", type=pathlib.Path)
    args = parser.parse_args()

    version_name, version_code = source_version(args.source)
    apk_version_code = None
    if args.apk_badging is not None:
        match = APK_VERSION_CODE.search(args.apk_badging.read_text())
        if match is None:
            raise ValueError(f"could not read APK versionCode from {args.apk_badging}")
        apk_version_code = int(match.group(1))
    expected_universal = validate(version_name, version_code, args.tag, apk_version_code)
    print(expected_universal)


if __name__ == "__main__":
    main()
