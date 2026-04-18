import argparse
import json
import os
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_MAAEND_ROOT = PROJECT_ROOT.parent / "MaaEnd"
ANDROID_RUNTIME_DIR = PROJECT_ROOT / "runtime"


def run(cmd: list[str], *, cwd: Path | None = None, env: dict[str, str] | None = None) -> None:
    process = subprocess.run(
        cmd,
        cwd=cwd,
        env=env,
        text=True,
        check=False,
    )
    if process.returncode != 0:
        raise RuntimeError(f"command failed ({process.returncode}): {' '.join(cmd)}")


def stage_go_service(go_exe: str, source_dir: Path, output_dir: Path) -> Path:
    output_dir.mkdir(parents=True, exist_ok=True)
    output_path = output_dir / "go-service"
    env = {
        **os.environ,
        "GOOS": "android",
        "GOARCH": "arm64",
        "CGO_ENABLED": "0",
    }
    run(
        [go_exe, "build", "-o", str(output_path), "."],
        cwd=source_dir,
        env=env,
    )
    return output_path


def stage_maafw(source_dir: Path, output_dir: Path) -> Path:
    if not source_dir.exists():
        raise FileNotFoundError(f"maafw source dir not found: {source_dir}")
    if output_dir.exists():
        shutil.rmtree(output_dir)
    shutil.copytree(source_dir, output_dir)
    return output_dir


def main() -> int:
    parser = argparse.ArgumentParser(description="Stage MaaEnd Android runtime artifacts")
    parser.add_argument(
        "--maaend-root",
        type=Path,
        default=DEFAULT_MAAEND_ROOT,
        help="Path to the MaaEnd repository to reuse assets and agent/go-service from",
    )
    parser.add_argument("--go-exe", default="go", help="Go executable path")
    parser.add_argument("--maafw-dir", type=Path, help="Vendored MaaFramework Android runtime directory")
    parser.add_argument("--output", type=Path, default=ANDROID_RUNTIME_DIR, help="Android runtime output directory")
    parser.add_argument("--skip-go", action="store_true", help="Skip cross-compiling agent/go-service")
    parser.add_argument("--clear", action="store_true", help="Clear staged runtime directories before copying")
    args = parser.parse_args()

    output_dir: Path = args.output
    output_dir.mkdir(parents=True, exist_ok=True)
    maaend_root: Path = args.maaend_root
    go_service_dir = maaend_root / "agent" / "go-service"

    if not args.skip_go and not go_service_dir.exists():
        raise FileNotFoundError(f"go-service source dir not found: {go_service_dir}")

    if args.clear:
        for child in ("agent", "maafw"):
            path = output_dir / child
            if path.exists():
                shutil.rmtree(path)

    manifest: dict[str, object] = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "output_dir": str(output_dir),
        "maaend_root": str(maaend_root),
    }

    if not args.skip_go:
        go_binary = stage_go_service(args.go_exe, go_service_dir, output_dir / "agent")
        manifest["go_service"] = str(go_binary)

    if args.maafw_dir:
        maafw_dir = stage_maafw(args.maafw_dir, output_dir / "maafw")
        manifest["maafw"] = str(maafw_dir)

    manifest_path = output_dir / "runtime-manifest.json"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"staged Android runtime manifest -> {manifest_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
