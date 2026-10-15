import os, zipfile

os.makedirs("Staging/.github/workflows", exist_ok=True)
os.makedirs("Staging/app", exist_ok=True)
os.makedirs("Staging/owner", exist_ok=True)

workflow = """name: Build Nacht X & Owner APKs

on:
  push:
    branches: [ main, master ]
  pull_request:
    branches: [ main, master ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
      - uses: android-actions/setup-android@v3
      - run: chmod +x gradlew || true
      - run: ./gradlew :app:assembleRelease
      - run: ./gradlew :owner:assembleRelease
      - uses: actions/upload-artifact@v4
        with:
          name: Nacht-X-APK
          path: app/build/outputs/apk/release/app-release-unsigned.apk
      - uses: actions/upload-artifact@v4
        with:
          name: Nacht-X-Owner-APK
          path: owner/build/outputs/apk/release/owner-release-unsigned.apk
"""

update_sh = """#!/bin/bash
echo "Purging old workflows..."
rm -rf .github/workflows/*
echo "Done!"
"""

build_kts = """plugins {
    id("com.android.application") version "8.1.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.0" apply false
}
"""

settings_kts = """rootProject.name = "UnoxidizedX"
include(":app", ":owner")
"""

with open("Staging/.github/workflows/build_apks.yml", "w") as f: f.write(workflow)
with open("Staging/update.sh", "w") as f: f.write(update_sh)
with open("Staging/build.gradle.kts", "w") as f: f.write(build_kts)
with open("Staging/settings.gradle.kts", "w") as f: f.write(settings_kts)

os.makedirs("/storage/emulated/0/Download", exist_ok=True)
zip_path = "/storage/emulated/0/Download/NACHTX_fix.zip"

with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zipf:
    for root, _, filenames in os.walk("Staging"):
        for file in filenames:
            abs_p = os.path.join(root, file)
            arc_p = os.path.relpath(abs_p, "Staging")
            zipf.write(abs_p, arc_p)

print("SUCCESS: NACHTX_fix.zip created!")
