package com.sledje.agentpro.buildlogic

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.sledje.agentpro.buildlogic.codegen.CatalogKeysLock
import com.sledje.agentpro.buildlogic.codegen.Naming
import com.sledje.agentpro.buildlogic.codegen.PrivacyNotices
import com.sledje.agentpro.buildlogic.codegen.SharedDefinitions
import com.sledje.agentpro.buildlogic.tasks.CopySitePreviewAssetsTask
import com.sledje.agentpro.buildlogic.tasks.GenerateSharedCodeTask
import com.sledje.agentpro.buildlogic.tasks.UpdateCatalogKeysLockTask
import com.sledje.agentpro.buildlogic.tasks.VerifyReleaseReadyTask
import com.sledje.agentpro.buildlogic.tasks.VerifySharedTask
import com.sledje.agentpro.buildlogic.tasks.WriteCatalogSyncTask
import com.sledje.agentpro.buildlogic.tasks.WritePrivacyNoticesTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** Paths shared by both plugins. `mobile/` is the Gradle root; the repo root is its parent. */
private class RepoPaths(project: Project) {
    val mobile: Directory = project.isolated.rootProject.projectDirectory
    val repo: Directory = mobile.dir("..")
    val shared: Directory = repo.dir("shared")
    val web: Directory = repo.dir("web")
    val migrations: Directory = repo.dir("supabase/migrations")
    val keysLock = mobile.file("build-logic/${CatalogKeysLock.FILE_NAME}")
}

/**
 * `agentpro.shared` — applied to :composeApp. Registers:
 * - `generateSharedCode`: Catalog.kt, Tokens.kt, SiteSchema.kt, AppConfig.kt + Compose resources dir;
 *   wired as a commonMain source dir and as the commonMain Compose resources dir, so it runs before compilation;
 * - `writeCatalogSync`, `updateCatalogKeysLock`, `writePrivacyNotices` (write into the source tree; not compile dependencies);
 * - `generateShared` (lifecycle: all of the above) and `verifyShared` (wired into `check`);
 * - `verifyReleaseReady` (release gate for `{UPPER_CASE}` i18n placeholders; not part of `check`).
 */
class SharedDefinitionsPlugin : Plugin<Project> {
    override fun apply(project: Project) = with(project) {
        val paths = RepoPaths(this)
        val srcResources = layout.projectDirectory.dir("src/commonMain/composeResources")
        val genRoot = layout.buildDirectory.dir("generated/agentpro")

        val generateCode = tasks.register("generateSharedCode", GenerateSharedCodeTask::class.java) {
            sharedDir.set(paths.shared)
            sharedFiles.from(SharedDefinitions.FILES.map { paths.shared.file(it) })
            localProperties.from(paths.mobile.file("local.properties"))
            handWrittenResourcesDir.set(srcResources)
            handWrittenResources.from(srcResources)
            kotlinOutputDir.set(genRoot.map { it.dir("kotlin/commonMain") })
            resourcesOutputDir.set(genRoot.map { it.dir("composeResources/commonMain") })
        }
        val writeCatalogSync = tasks.register("writeCatalogSync", WriteCatalogSyncTask::class.java) {
            sharedDir.set(paths.shared)
            migrationsDir.set(paths.migrations)
            lockFile.set(paths.keysLock)
        }
        val writePrivacyNotices = tasks.register("writePrivacyNotices", WritePrivacyNoticesTask::class.java) {
            sharedDir.set(paths.shared)
        }
        val updateLock = tasks.register("updateCatalogKeysLock", UpdateCatalogKeysLockTask::class.java) {
            sharedDir.set(paths.shared)
            lockFile.set(paths.keysLock)
        }
        tasks.register("generateShared") {
            group = "agentpro"
            description = "Generates code, resources, the catalog_sync migration and the keys lock from shared/."
            dependsOn(generateCode, writeCatalogSync, updateLock, writePrivacyNotices)
        }
        registerVerifyReleaseReady(this, paths)
        writeCatalogSync.configure { mustRunAfter(updateLock) }

        val sourceRoots = listOf(layout.projectDirectory.dir("src"), paths.mobile.dir("androidApp/src"))
        val verify = tasks.register("verifyShared", VerifySharedTask::class.java) {
            sharedDir.set(paths.shared)
            composeResourcesDir.set(srcResources)
            migrationsDir.set(paths.migrations)
            lockFile.set(paths.keysLock)
            displayRoot.set(paths.repo)
            kotlinSourceRoots.from(sourceRoots)
            inputFiles.from(SharedDefinitions.FILES.map { paths.shared.file(it) })
            inputFiles.from(sourceRoots, paths.migrations, paths.keysLock, paths.shared.file(PrivacyNotices.WEB_JSON))
            reportFile.set(genRoot.map { it.file("verifyShared/report.txt") })
            mustRunAfter(writeCatalogSync, updateLock, writePrivacyNotices)
        }
        plugins.withType(LifecycleBasePlugin::class.java) {
            tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) { dependsOn(verify) }
        }

        pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            extensions.getByType(KotlinMultiplatformExtension::class.java).sourceSets.named("commonMain") {
                kotlin.srcDir(generateCode.flatMap { it.kotlinOutputDir })
            }
        }
        pluginManager.withPlugin("org.jetbrains.compose") {
            val resources = (extensions.getByType(ComposeExtension::class.java) as org.gradle.api.plugins.ExtensionAware)
                .extensions.getByType(ResourcesExtension::class.java)
            resources.publicResClass = true
            resources.packageOfResClass = Naming.RES_PACKAGE
            resources.generateResClass = ResourcesExtension.ResourceClassGeneration.Always
            // Replaces src/commonMain/composeResources: the generated dir holds a copy of it plus strings_catalog.xml.
            resources.customDirectory("commonMain", generateCode.flatMap { it.resourcesOutputDir })
        }
        // Belt and braces: the Compose resource tasks read the generated dir.
        tasks.matching { it.name.endsWith("ResourcesForCommonMain") || it.name == "prepareKotlinIdeaImport" }
            .configureEach { dependsOn(generateCode) }
    }
}

/**
 * `agentpro.site-preview` — applied to :androidApp. Copies `web/` (when it exists) and `shared/` into a
 * generated assets dir (`site/web`, `site/shared`) that SitePreview's WebView loads from
 * `file:///android_asset/site/`. Also registers this project's part of `generateShared`, and a local
 * `verifyReleaseReady` that every `pre*ReleaseBuild` task depends on (debug builds don't).
 */
class SitePreviewAssetsPlugin : Plugin<Project> {
    override fun apply(project: Project) = with(project) {
        val paths = RepoPaths(this)
        val copy = tasks.register("copySitePreviewAssets", CopySitePreviewAssetsTask::class.java) {
            webDir.set(paths.web)
            sharedDir.set(paths.shared)
            webFiles.from(paths.web)
            sharedFiles.from(paths.shared)
            outputDir.set(layout.buildDirectory.dir("generated/agentpro/sitePreviewAssets"))
        }
        tasks.register("generateShared") {
            group = "agentpro"
            description = "Copies web/ and shared/ into Android assets for the in-app site preview."
            dependsOn(copy)
        }
        // Release gate: every release variant's pre-build depends on verifyReleaseReady (debug builds don't).
        val releaseGate = registerVerifyReleaseReady(this, paths)
        tasks.matching { it.name.startsWith("pre") && it.name.endsWith("ReleaseBuild") }
            .configureEach { dependsOn(releaseGate) }
        pluginManager.withPlugin("com.android.application") {
            extensions.getByType(ApplicationAndroidComponentsExtension::class.java).onVariants { variant ->
                variant.sources.assets?.addGeneratedSourceDirectory(copy, CopySitePreviewAssetsTask::outputDir)
            }
        }
    }
}

/** `verifyReleaseReady` (see [VerifyReleaseReadyTask]); registered in both modules so each can gate on it locally. */
private fun registerVerifyReleaseReady(project: Project, paths: RepoPaths) =
    project.tasks.register("verifyReleaseReady", VerifyReleaseReadyTask::class.java) {
        sharedDir.set(paths.shared)
        inputFiles.from(paths.shared.file(SharedDefinitions.I18N_EN), paths.shared.file(SharedDefinitions.I18N_HI))
        reportFile.set(project.layout.buildDirectory.file("generated/agentpro/verifyReleaseReady/report.txt"))
    }
