import pathlib
import sys
import zipfile


def verify(apk: pathlib.Path) -> None:
    with zipfile.ZipFile(apk) as archive:
        libraries = [entry for entry in archive.infolist() if entry.filename.startswith("lib/") and entry.filename.endswith("/libsigil-jni.so")]
        if not libraries:
            raise ValueError("APK has no libsigil-jni.so")
        for entry in libraries:
            name = entry.filename
            data = archive.read(entry)
            for marker in (b"00040000/0008d300", b"0000055d"):
                if marker not in data:
                    raise ValueError(f"{name} lacks patched Sigil marker {marker!r}")
            print(f"patched-sigil: {name} contains both markers")


if __name__ == "__main__":
    try:
        verify(pathlib.Path(sys.argv[1]))
    except (OSError, ValueError, zipfile.BadZipFile) as error:
        print(f"patched-sigil: {error}", file=sys.stderr)
        sys.exit(1)
