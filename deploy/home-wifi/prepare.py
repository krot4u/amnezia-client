#!/usr/bin/env python3
"""Apply the reviewed feature patch to a clean official release; fail closed on drift."""
import argparse
import hashlib
import logging
from pathlib import Path
import re
import subprocess


def prepare(source, patch, version_code):
    if not 1_000_000 <= version_code < 2_100_000_000:
        raise ValueError("Version code must be between 1000000 and 2099999999")
    if subprocess.check_output(["git", "status", "--porcelain", "--untracked-files=no"], cwd=source).strip():
        raise ValueError("Build source must be clean before applying feature patch")
    subprocess.run(["git", "apply", "--check", str(patch)], cwd=source, check=True)
    subprocess.run(["git", "apply", str(patch)], cwd=source, check=True)
    cmake = source / "CMakeLists.txt"
    data = cmake.read_text()
    updated, count = re.subn(r"set\(APP_ANDROID_VERSION_CODE \d+\)",
                             f"set(APP_ANDROID_VERSION_CODE {version_code})", data)
    if count != 1:
        raise ValueError("Upstream Android version-code contract changed")
    cmake.write_text(updated)
    logging.info("Feature applied; patch sha256=%s", hashlib.sha256(patch.read_bytes()).hexdigest())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("--patch", type=Path, default=Path(__file__).with_name("feature.patch"))
    parser.add_argument("--version-code", type=int, required=True)
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
    prepare(args.source.resolve(), args.patch.resolve(), args.version_code)


if __name__ == "__main__":
    main()
