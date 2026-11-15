import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch


SCRIPT = Path(__file__).with_name("prepare_android_runtime.py")
SPEC = importlib.util.spec_from_file_location("legacy_prepare_runtime", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class RuntimeManifestTest(unittest.TestCase):
    def test_manifest_paths_are_relative_to_runtime(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            project = base / "project"
            (project / "agent/go-service").mkdir(parents=True)
            runtime = base / "runtime"
            native = base / "native"
            native.mkdir()
            (native / "libMaaFramework.so").write_bytes(b"library")

            def stage(name, output):
                output.mkdir(parents=True, exist_ok=True)
                target = output / name
                target.write_bytes(b"agent")
                return target

            argv = [str(SCRIPT), "--maaend-root", str(project), "--output", str(runtime), "--maafw-dir", str(native)]
            with patch.object(sys, "argv", argv), \
                    patch.object(MODULE, "stage_runner", side_effect=lambda go, source, output: stage("maa-go-runner", output)), \
                    patch.object(MODULE, "stage_go_service", side_effect=lambda go, source, output: stage("go-service", output)):
                self.assertEqual(0, MODULE.main())
            text = (runtime / "runtime-manifest.json").read_text()
            manifest = json.loads(text)
            self.assertNotIn(directory, text)
            self.assertEqual("agent/maa-go-runner", manifest["runner"])
            self.assertEqual("agent/go-service", manifest["go_service"])
            self.assertEqual("maafw", manifest["maafw"])
            self.assertEqual("project", manifest["project"])


if __name__ == "__main__":
    unittest.main()
