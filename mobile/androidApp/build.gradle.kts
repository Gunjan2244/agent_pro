import java.util.Properties

plugins {
    // AGP 9 compiles Kotlin itself (built-in Kotlin), so no org.jetbrains.kotlin.android plugin.
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    // Copies web/ + shared/ into generated assets (site/) for the in-app SitePreview.
    id("agentpro.site-preview")
}

// Play upload key (DECISIONS #135): read from the git-ignored mobile/local.properties
// (RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD).
// Without them the release build is left unsigned; values are never logged.
val localProps = Properties().also { props ->
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { props.load(it) }
}
val releaseStoreFile: String? = localProps.getProperty("RELEASE_STORE_FILE")?.takeIf { it.isNotBlank() }

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

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseStoreFile != null) signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity.compose)
    // MainActivity is an AppCompatActivity (per-app locales, DECISIONS #45).
    implementation(libs.androidx.appcompat)
}
