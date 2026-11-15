import io
import tempfile
import unittest
from pathlib import Path
import zipfile

from prepare_bundled_runtime import extract, validate


class RuntimeExtractionTest(unittest.TestCase):
    def test_extracts_runtime_without_old_host_or_build_metadata(self):
        with tempfile.TemporaryDirectory() as temp, io.BytesIO() as buffer:
            with zipfile.ZipFile(buffer, "w") as archive:
                archive.writestr("classes.dex", b"old host")
                archive.writestr("assets/bundled_runtime/runtime-manifest.json", b"old paths")
                archive.writestr("assets/bundled_runtime/agent/go-service", b"agent")
            with zipfile.ZipFile(buffer) as archive:
                extract(archive, Path(temp))
            self.assertEqual(["agent/go-service"], [p.relative_to(temp).as_posix() for p in Path(temp).rglob("*") if p.is_file()])

    def test_rejects_paths_outside_runtime(self):
        with tempfile.TemporaryDirectory() as temp, io.BytesIO() as buffer:
            with zipfile.ZipFile(buffer, "w") as archive:
                archive.writestr("assets/bundled_runtime/agent/../../escape", b"unsafe")
            with zipfile.ZipFile(buffer) as archive, self.assertRaises(ValueError):
                extract(archive, Path(temp))

    def test_rejects_agent_only_runtime(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / "agent").mkdir()
            elf = b"\x7fELF\x02\x01" + bytes(12) + bytes([183, 0]) + b"/system/bin/linker64\0"
            for name in ("go-service", "maa-go-runner"):
                (root / "agent" / name).write_bytes(elf)
            with self.assertRaises(FileNotFoundError):
                validate(root)


if __name__ == "__main__":
    unittest.main()
