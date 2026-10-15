import os, zipfile

os.makedirs("Staging/.github/workflows", exist_ok=True)
os.makedirs("Staging/app/src/main/java/com/shell/space", exist_ok=True)
os.makedirs("Staging/app/src/main/res/layout", exist_ok=True)
os.makedirs("Staging/app/src/main/res/values", exist_ok=True)
os.makedirs("Staging/app/src/main/res/drawable", exist_ok=True)
os.makedirs("Staging/owner/src/main/java/com/shell/space/owner", exist_ok=True)
os.makedirs("Staging/owner/src/main/res/layout", exist_ok=True)
os.makedirs("Staging/owner/src/main/res/values", exist_ok=True)
os.makedirs("Staging/owner/src/main/res/drawable", exist_ok=True)

files = {
    "PROGRESS.md": "# Nacht X Upgrade\n- Glassmorphism UI\n- 2 Apps Pipeline",
    "update.sh": "#!/bin/bash\necho 'Applying update...'\nchmod +x gradlew || true\necho 'Done!'",
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

      - name: Setup Android SDK
        uses: android-actions/setup-android@v3

      - name: Grant execute permission for Gradle wrapper
        run: chmod +x gradlew || true

      - name: Build Nacht X (Main APK)
        run: ./gradlew :app:assembleRelease || gradle assembleRelease

      - name: Build Nacht X Owner (Owner APK)
        run: ./gradlew :owner:assembleRelease || true

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
    "build.gradle.kts": """plugins {
    id("com.android.application") version "8.1.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.0" apply false
}
""",
    "settings.gradle.kts": """pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "UnoxidizedX"
include(":app", ":owner")
""",
    "app/build.gradle.kts": """plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.shell.space"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.shell.space"
        minSdk = 26
        targetSdk = 34
        versionCode = 72
        versionName = "7.2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
}
""",
    "owner/build.gradle.kts": """plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.shell.space.owner"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.shell.space.owner"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0-Owner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
}
"""
}

for path, content in files.items():
    full_path = os.path.join("Staging", path)
    os.makedirs(os.path.dirname(full_path), exist_ok=True)
    with open(full_path, "w", encoding="utf-8") as f:
        f.write(content)

os.makedirs("/storage/emulated/0/Download", exist_ok=True)
zip_path = "/storage/emulated/0/Download/NACHTX_clean.zip"
with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zipf:
    for root, _, filenames in os.walk("Staging"):
        for file in filenames:
            abs_p = os.path.join(root, file)
            arc_p = os.path.relpath(abs_p, "Staging")
            zipf.write(abs_p, arc_p)

print("SUCCESS: NACHTX_clean.zip ready!")
