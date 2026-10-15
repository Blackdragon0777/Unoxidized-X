import os, zipfile, base64

os.makedirs("Staging/.github/workflows", exist_ok=True)
os.makedirs("Staging/app/src/main/res/values", exist_ok=True)
os.makedirs("Staging/app/src/main/res/mipmap-hdpi", exist_ok=True)
os.makedirs("Staging/owner/src/main/res/values", exist_ok=True)
os.makedirs("Staging/owner/src/main/res/mipmap-hdpi", exist_ok=True)

# Minimal valid 1x1 PNG for icons
min_png = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==")

for app in ["app", "owner"]:
    with open(f"Staging/{app}/src/main/res/mipmap-hdpi/ic_launcher.png", "wb") as f:
        f.write(min_png)
    with open(f"Staging/{app}/src/main/res/mipmap-hdpi/ic_launcher_round.png", "wb") as f:
        f.write(min_png)

strings_xml = """<resources>
    <string name="app_name">Nacht X</string>
</resources>
"""
owner_strings_xml = """<resources>
    <string name="app_name">Nacht X Owner</string>
</resources>
"""

with open("Staging/app/src/main/res/values/strings.xml", "w") as f:
    f.write(strings_xml)
with open("Staging/owner/src/main/res/values/strings.xml", "w") as f:
    f.write(owner_strings_xml)

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

with open("Staging/.github/workflows/build_apks.yml", "w") as f:
    f.write(workflow)

os.makedirs("/storage/emulated/0/Download", exist_ok=True)
zip_path = "/storage/emulated/0/Download/NACHTX_manifest_fix.zip"

with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zipf:
    for root, _, filenames in os.walk("Staging"):
        for file in filenames:
            abs_p = os.path.join(root, file)
            arc_p = os.path.relpath(abs_p, "Staging")
            zipf.write(abs_p, arc_p)

print("SUCCESS: NACHTX_manifest_fix.zip created!")
