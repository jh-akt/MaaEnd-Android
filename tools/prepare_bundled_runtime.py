"""Restore the complete, pinned 16 KiB Android runtime archive."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "MaaFramework-Android/tools"))
from verify_android_elf import verify_apk as verify_all_apk_elf, verify_runtime
MARKER = ".prepared-runtime.json"
REQUIRED = (
    "agent/go-service", "agent/maa-go-runner", "maafw/libMaaFramework.so",
    "maafw/libMaaAgentClient.so", "maafw/libMaaAgentServer.so",
    "maafw/libMaaAndroidNativeControlUnit.so", "maafw/libMaaUtils.so",
    "maafw/libonnxruntime.so", "maafw/libopencv_world4.so", "maafw/libfastdeploy_ppocr.so",
    "maafw/libncnn.so",
)


def sha256(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def validate(root):
    for name in REQUIRED:
        path = root / name
        data = path.read_bytes()
        if data[:6] != b"\x7fELF\x02\x01" or int.from_bytes(data[18:20], "little") != 183:
            raise ValueError(f"Not an Android arm64 ELF: {name}")
        if name.startswith("agent/") and b"/system/bin/linker64\0" not in data:
            raise ValueError(f"Not an Android executable: {name}")
    verify_runtime(root)


def extract(archive, destination):
    for entry in archive.infolist():
        if entry.is_dir():
            continue
        name = entry.filename
        path = PurePosixPath(name)
        if path.is_absolute() or ".." in path.parts or "\\" in name:
            raise ValueError(f"Unsafe runtime path: {name}")
        # Publish only the runtime; metadata is recreated from the source lock.
        if path.parts[0] not in {"agent", "maafw"}:
            continue
        target = destination.joinpath(*path.parts)
        target.parent.mkdir(parents=True, exist_ok=True)
        with archive.open(entry) as source, target.open("wb") as output:
            shutil.copyfileobj(source, output)
        if path.parts[0] == "agent":
            target.chmod(0o755)


def prepare(apk=None):
    lock = json.loads((ROOT / "runtime.lock.json").read_text())
    cache = ROOT / ".cache"
    cache.mkdir(exist_ok=True)
    if apk is None:
        apk = cache / lock["filename"]
        if not apk.is_file():
            partial = apk.with_suffix(apk.suffix + ".part")
            subprocess.run(["curl", "--fail", "--location", "--retry", "3", "--continue-at", "-",
                            "--output", str(partial), lock["url"]], check=True)
            if partial.stat().st_size != lock["size"] or sha256(partial) != lock["sha256"]:
                raise ValueError("Runtime source checksum mismatch; partial download retained")
            partial.replace(apk)
    apk = Path(apk)
    if apk.stat().st_size != lock["size"] or sha256(apk) != lock["sha256"]:
        raise ValueError("Runtime source does not match runtime.lock.json")
    target = ROOT / "build/preparedRuntime"
    target.parent.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="runtime-", dir=cache) as temp:
        staged = Path(temp) / "runtime"
        staged.mkdir()
        with zipfile.ZipFile(apk) as archive:
            extract(archive, staged)
        validate(staged)
        (staged / MARKER).write_text(json.dumps(lock, indent=2) + "\n")
        (staged / "runtime-manifest.json").write_text(json.dumps(lock, indent=2) + "\n")
        if target.exists():
            if (target / MARKER).is_file():
                shutil.rmtree(target)
            else:
                target.rmdir()  # Accept Gradle's empty output directory, preserve other data.
        shutil.move(str(staged), str(target))
    print("Prepared the verified MaaEnd Android runtime, including native libraries")


def verify_apk(apk):
    count = verify_all_apk_elf(apk)
    with zipfile.ZipFile(apk) as archive:
        for name in REQUIRED:
            data = archive.read("assets/bundled_runtime/" + name)
            if data[:6] != b"\x7fELF\x02\x01" or int.from_bytes(data[18:20], "little") != 183:
                raise ValueError(f"Invalid packaged runtime: {name}")
            if name.startswith("maafw/"):
                native = archive.read("lib/arm64-v8a/" + Path(name).name)
                if native[:4] != b"\x7fELF":
                    raise ValueError(f"Invalid packaged native library: {name}")
    print(f"Verified APK agents, required libraries and all {count} ELF files for 16 KiB pages")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", "--apk", dest="apk", type=Path, help="Existing archive matching runtime.lock.json")
    parser.add_argument("--verify-apk", type=Path, help="Validate a newly built host APK")
    args = parser.parse_args()
    verify_apk(args.verify_apk) if args.verify_apk else prepare(args.apk)
