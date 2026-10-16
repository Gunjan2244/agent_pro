plugins {
    `kotlin-dsl`
}

group = "com.sledje.agentpro.buildlogic"

kotlin {
    jvmToolchain(21)
}

dependencies {
    // JSON tree API only (no serialization compiler plugin, so the kotlin-dsl Kotlin version is untouched).
    implementation(libs.kotlinx.serialization.json)

    // Compile-time only: the real plugins come from the root build script's classpath.
    compileOnly(libs.build.logic.kotlin.gradle.plugin)
    compileOnly(libs.build.logic.compose.gradle.plugin)
    compileOnly(libs.build.logic.android.gradle.api)

    testImplementation(kotlin("test-junit"))
}

tasks.test {
    useJUnit()
}

gradlePlugin {
    plugins {
        register("agentproShared") {
            id = "agentpro.shared"
            implementationClass = "com.sledje.agentpro.buildlogic.SharedDefinitionsPlugin"
        }
        register("agentproSitePreview") {
            id = "agentpro.site-preview"
            implementationClass = "com.sledje.agentpro.buildlogic.SitePreviewAssetsPlugin"
        }
    }
}
