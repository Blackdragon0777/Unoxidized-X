plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

// Signing details come from the environment (GitHub Actions secrets). Nothing secret is stored in this repository.
val uxKeystore: String? = System.getenv("UX_KEYSTORE")

android {
    namespace = "com.unoxidized.x"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.unoxidized.x"
        minSdk = 24
        targetSdk = 34
        versionCode = 7
        versionName = "7.0"
    }
    signingConfigs {
        if (uxKeystore != null && file(uxKeystore).exists()) {
            create("ux") {
                storeFile = file(uxKeystore)
                storePassword = System.getenv("UX_STORE_PASS")
                keyAlias = System.getenv("UX_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "ux"
                keyPassword = System.getenv("UX_KEY_PASS")
            }
        }
    }
    buildTypes {
        getByName("debug") {
            signingConfigs.findByName("ux")?.let { signingConfig = it }
        }
    }
    sourceSets.getByName("main").java.srcDir(rootProject.file("shared"))
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
