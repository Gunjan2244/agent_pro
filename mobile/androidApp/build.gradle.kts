plugins {
    // AGP 9 compiles Kotlin itself (built-in Kotlin), so no org.jetbrains.kotlin.android plugin.
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    // Copies web/ + shared/ into generated assets (site/) for the in-app SitePreview.
    id("agentpro.site-preview")
}

android {
    namespace = "com.sledje.agentpro.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.sledje.agentpro"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity.compose)
    // MainActivity is an AppCompatActivity (per-app locales, DECISIONS #45).
    implementation(libs.androidx.appcompat)
}
