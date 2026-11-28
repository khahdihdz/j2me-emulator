#!/usr/bin/env python3
"""Verify a pristine J2ME Loader checkout before building it.

Usage:
    python3 tools/check-engine-pristine.py [engine_dir]

engine_dir defaults to vendor/j2me-loader (where the
engine-pristine.yml workflow checks out upstream tag 1.8.2).

Read-only: this script only reads files and prints a report. It writes
nothing and makes no network requests. Exit code 0 when every required
file is present, 1 when any required file is missing.
"""

import re
import sys
from pathlib import Path

REQUIRED_FILES = [
    "app/build.gradle",
    "build.gradle",
    "settings.gradle",
    "gradle.properties",
    "gradle/wrapper/gradle-wrapper.properties",
    "gradlew",
    "gradle/wrapper/gradle-wrapper.jar",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/ru/playsoftware/j2meloader/config/Config.java",
    "app/src/main/java/ru/playsoftware/j2meloader/util/Constants.java",
    "app/src/main/java/javax/microedition/shell/MicroActivity.java",
    "app/src/main/java/ru/woesss/j2me/installer/AppInstaller.java",
    "app/src/main/cpp/Android.mk",
]

REQUIRED_DIRS = [
    "dexlib",
    "app/src/main/cpp",
    "app/src/main/java/ru/playsoftware/j2meloader",
    "app/src/main/java/javax/microedition",
]

# (file, pattern, label) — values printed from the engine's own files.
VALUE_PATTERNS = [
    ("build.gradle", r"COMPILE_SDK\s*=\s*(\d+)", "COMPILE_SDK"),
    ("build.gradle", r"MIN_SDK\s*=\s*(\d+)", "MIN_SDK"),
    ("build.gradle", r"TARGET_SDK\s*=\s*(\d+)", "TARGET_SDK"),
    ("build.gradle", r"NDK_VERSION\s*=\s*'([^']+)'", "NDK_VERSION"),
    ("build.gradle", r"id\s+'com\.android\.application'\s+version\s+'([^']+)'",
     "AGP"),
    ("app/build.gradle", r"versionCode\s+(\d+)", "app versionCode"),
    ("app/build.gradle", r'versionName\s+"([^"]+)"', "app versionName"),
    ("gradle/wrapper/gradle-wrapper.properties",
     r"gradle-([0-9.]+)-bin\.zip", "Gradle"),
    ("app/src/main/java/ru/playsoftware/j2meloader/util/Constants.java",
     r'ACTION_EDIT\s*=\s*"([^"]+)"', "Constants.ACTION_EDIT"),
    ("app/src/main/java/ru/playsoftware/j2meloader/util/Constants.java",
     r'KEY_MIDLET_NAME\s*=\s*"([^"]+)"', "Constants.KEY_MIDLET_NAME"),
    ("app/src/main/java/ru/playsoftware/j2meloader/util/Constants.java",
     r'KEY_START_ARGUMENTS\s*=\s*"([^"]+)"',
     "Constants.KEY_START_ARGUMENTS"),
]


def main(argv):
    engine = Path(argv[1] if len(argv) > 1 else "vendor/j2me-loader")
    print(f"Engine dir: {engine}")
    missing = []

    for rel in REQUIRED_FILES:
        path = engine / rel
        if path.is_file():
            print(f"  OK   {rel}")
        else:
            print(f"  MISSING {rel}")
            missing.append(rel)

    for rel in REQUIRED_DIRS:
        path = engine / rel
        if path.is_dir():
            print(f"  OK   {rel}/")
        else:
            print(f"  MISSING {rel}/")
            missing.append(rel + "/")

    print("Values read from engine files:")
    for rel, pattern, label in VALUE_PATTERNS:
        path = engine / rel
        if not path.is_file():
            print(f"  --   {label}: (file missing)")
            continue
        match = re.search(pattern, path.read_text(errors="replace"))
        if match:
            print(f"  {label} = {match.group(1)}")
        else:
            print(f"  ??   {label}: pattern not found in {rel}")

    flavours = []
    app_gradle = engine / "app/build.gradle"
    if app_gradle.is_file():
        text = app_gradle.read_text(errors="replace")
        flavours_block = re.search(
            r"productFlavors\s*\{(.*?)\n    \}", text, re.DOTALL)
        if flavours_block:
            flavours = re.findall(r"^\s{8}(\w+)\s*\{",
                                  flavours_block.group(1), re.MULTILINE)
        print(f"  flavours = {flavours if flavours else '(not parsed)'}")

    if missing:
        print(f"FAIL: {len(missing)} required path(s) missing.")
        return 1
    print("PASS: all required engine files present.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
