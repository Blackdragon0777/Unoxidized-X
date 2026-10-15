import os, zipfile

os.makedirs("Staging/.github/workflows", exist_ok=True)
os.makedirs("Staging/gradle/wrapper", exist_ok=True)
os.makedirs("Staging/app/src/main/java/com/shell/space", exist_ok=True)
os.makedirs("Staging/owner/src/main/java/com/shell/space/owner", exist_ok=True)

files = {
    ".github/workflows/build_apks.yml": """name: Build Nacht X & Owner APKs

on:
  push:
    branches: [ main, master ]
  pull_request:
    branches: [ main, master ]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout Repository
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3

      - name: Grant execute permission for Gradle wrapper
        run: chmod +x gradlew || true

      - name: Build Nacht X (Main APK)
        run: |
          if [ -f "./gradlew" ]; then
            ./gradlew :app:assembleRelease
          else
            gradle :app:assembleRelease
          fi

      - name: Build Nacht X Owner (Owner APK)
        run: |
          if [ -f "./gradlew" ]; then
            ./gradlew :owner:assembleRelease
          else
            gradle :owner:assembleRelease
          fi

      - name: Upload Nacht X Artifact
        uses: actions/upload-artifact@v4
        with:
          name: Nacht-X-APK
          path: app/build/outputs/apk/release/app-release-unsigned.apk

      - name: Upload Nacht X Owner Artifact
        uses: actions/upload-artifact@v4
        with:
          name: Nacht-X-Owner-APK
          path: owner/build/outputs/apk/release/owner-release-unsigned.apk
""",
    "gradlew": """#!/usr/bin/env sh
exec gradle "$@"
""",
    "gradlew.bat": """@rem Gradle launcher script
gradle %*
""",
    "gradle/wrapper/gradle-wrapper.properties": """distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\\://services.gradle.org/distributions/gradle-8.2-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
"""
}

for path, content in files.items():
    full_path = os.path.join("Staging", path)
    os.makedirs(os.path.dirname(full_path), exist_ok=True)
    with open(full_path, "w", encoding="utf-8") as f:
        f.write(content)

os.makedirs("/storage/emulated/0/Download", exist_ok=True)
zip_path = "/storage/emulated/0/Download/NACHTX_wrapper_fix.zip"

with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zipf:
    for root, _, filenames in os.walk("Staging"):
        for file in filenames:
            abs_p = os.path.join(root, file)
            arc_p = os.path.relpath(abs_p, "Staging")
            zipf.write(abs_p, arc_p)

print("SUCCESS: NACHTX_wrapper_fix.zip generated!")
