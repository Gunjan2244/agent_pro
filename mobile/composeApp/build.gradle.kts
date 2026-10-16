import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    // Type-safe navigation routes, Supabase DTOs and the sync rows (docs/ARCHITECTURE.md, Patterns).
    alias(libs.plugins.kotlin.serialization)
    // Room KMP (core/data/AppDatabase): KSP generates the DAOs and the database constructor per target.
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    // Screenshot tests of the component gallery (docs/TESTING.md). Baselines live in src/androidHostTest/screenshots.
    alias(libs.plugins.roborazzi)
    // generateShared / verifyShared; also sets the Compose resources package (com.sledje.agentpro.resources).
    id("agentpro.shared")
}

kotlin {
    // LocalAppLocale (core/designsystem) and Room's AppDatabaseConstructor (core/data) are expect objects.
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }

    android {
        namespace = "com.sledje.agentpro"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        // Compose Multiplatform resources need Android resource processing in a KMP library.
        androidResources { enable = true }
        // Robolectric (Compose UI tests, Roborazzi) needs the merged resources, assets and manifest.
        withHostTest { isIncludeAndroidResources = true }
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    // iOS phase: declared now so the source-set structure is right; only buildable on macOS.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.vico.multiplatform)
            // App foundation (Phase 5): MVVM, type-safe navigation, Supabase, Room cache, DataStore.
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.navigation.compose)
            implementation(project.dependencies.platform(libs.supabase.bom))
            implementation(libs.supabase.auth)
            implementation(libs.supabase.postgrest)
            implementation(libs.supabase.realtime)
            implementation(libs.supabase.storage)
            implementation(libs.ktor.client.core)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.androidx.datastore.preferences)
            // Website hub (Phase 8): images (Coil 3 + its Ktor 3 fetcher) and the QR code (qrose).
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(libs.qrose)
            implementation(libs.compose.ui.backhandler)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            // MediaPicker: the photo picker's Activity result launcher (Phase 8).
            implementation(libs.androidx.activity.compose)
            // LocaleController: AppCompatDelegate per-app locales (DECISIONS #45).
            implementation(libs.androidx.appcompat)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.compose.ui.test)
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.androidx.compose.ui.test.manifest)
        }
    }
}

dependencies {
    // Room's KSP processor for every target (docs/ARCHITECTURE.md, Libraries). The iOS ones are configured
    // now but only run on macOS (iOS phase).
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}

room {
    // Exported schema JSON per database version (committed), for migrations of the read cache.
    schemaDirectory("$projectDir/schemas")
}

roborazzi {
    // Committed baseline images (record with -Proborazzi.test.record=true, verify with -Proborazzi.test.verify=true).
    outputDir.set(file("src/androidHostTest/screenshots"))
}

tasks.withType<Test>().configureEach {
    // Robolectric + Compose rendering; the machine has ~7 GB RAM (docs/ARCHITECTURE.md, Memory).
    maxHeapSize = "1536m"
}
