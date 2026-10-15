import os, zipfile

os.makedirs("Staging/.github/workflows", exist_ok=True)
os.makedirs("Staging/gradle/wrapper", exist_ok=True)

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
      - name: Checkout Repository
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3
        with:
          gradle-version: '8.2'

      - name: Grant execute permission for Gradle wrapper
        run: chmod +x gradlew || true

      - name: Build Nacht X (Main APK)
        run: gradle :app:assembleRelease --no-daemon

      - name: Build Nacht X Owner (Owner APK)
        run: gradle :owner:assembleRelease --no-daemon

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
"""

gradle_wrapper_props = """distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\\://services.gradle.org/distributions/gradle-8.2-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
"""

with open("Staging/.github/workflows/build_apks.yml", "w") as f:
    f.write(workflow)
with open("Staging/gradle/wrapper/gradle-wrapper.properties", "w") as f:
    f.write(gradle_wrapper_props)

os.makedirs("/storage/emulated/0/Download", exist_ok=True)
zip_path = "/storage/emulated/0/Download/NACHTX_final.zip"

with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zipf:
    for root, _, filenames in os.walk("Staging"):
        for file in filenames:
            abs_p = os.path.join(root, file)
            arc_p = os.path.relpath(abs_p, "Staging")
            zipf.write(abs_p, arc_p)

print("SUCCESS: NACHTX_final.zip created!")
