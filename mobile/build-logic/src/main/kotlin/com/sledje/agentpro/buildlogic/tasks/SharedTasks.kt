package com.sledje.agentpro.buildlogic.tasks

import com.sledje.agentpro.buildlogic.codegen.AppConfigKotlinGenerator
import com.sledje.agentpro.buildlogic.codegen.CatalogKeysLock
import com.sledje.agentpro.buildlogic.codegen.CatalogKotlinGenerator
import com.sledje.agentpro.buildlogic.codegen.CatalogSyncGenerator
import com.sledje.agentpro.buildlogic.codegen.PrivacyNotices
import com.sledje.agentpro.buildlogic.codegen.PrivacyNoticesKotlinGenerator
import com.sledje.agentpro.buildlogic.codegen.SharedDefinitions
import com.sledje.agentpro.buildlogic.codegen.SharedDefinitionsException
import com.sledje.agentpro.buildlogic.codegen.SiteSchemaKotlinGenerator
import com.sledje.agentpro.buildlogic.codegen.SiteSeedKotlinGenerator
import com.sledje.agentpro.buildlogic.codegen.StringsXmlGenerator
import com.sledje.agentpro.buildlogic.codegen.TemplatesKotlinGenerator
import com.sledje.agentpro.buildlogic.codegen.TokensCssGenerator
import com.sledje.agentpro.buildlogic.codegen.TokensKotlinGenerator
import com.sledje.agentpro.buildlogic.verify.SharedVerifier
import com.sledje.agentpro.buildlogic.verify.VerifyInputs
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.Properties
import javax.inject.Inject

private fun loadDefinitions(sharedDir: File): SharedDefinitions = try {
    SharedDefinitions.load(sharedDir)
} catch (e: SharedDefinitionsException) {
    throw GradleException(e.message!!)
}

private fun File.writeIfChanged(text: String) {
    if (!isFile || readText() != text) {
        parentFile.mkdirs()
        writeText(text)
    }
}

/**
 * Generates Catalog.kt, Tokens.kt, SiteSchema.kt, Templates.kt, SiteSeed.kt (when shared/site-seed.json exists), PrivacyNotices.kt,
 * AppConfig.kt and the Compose resources directory
 * (a copy of src/commonMain/composeResources plus values{,-hi}/strings_catalog.xml).
 * Runs before Kotlin compilation and resource processing of composeApp. Not build-cached because
 * AppConfig.kt holds values from local.properties.
 */
@DisableCachingByDefault(because = "Output contains local.properties values; cheap to regenerate")
abstract class GenerateSharedCodeTask : DefaultTask() {
    @get:Internal abstract val sharedDir: DirectoryProperty

    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sharedFiles: ConfigurableFileCollection

    /** mobile/local.properties (optional). Its values are never logged. */
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE)
    abstract val localProperties: ConfigurableFileCollection

    /** Hand-written src/commonMain/composeResources, copied into [resourcesOutputDir]. */
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) @get:IgnoreEmptyDirectories
    abstract val handWrittenResources: ConfigurableFileCollection

    @get:Internal abstract val handWrittenResourcesDir: DirectoryProperty

    @get:OutputDirectory abstract val kotlinOutputDir: DirectoryProperty
    @get:OutputDirectory abstract val resourcesOutputDir: DirectoryProperty

    @get:Inject abstract val fs: FileSystemOperations

    init {
        group = "agentpro"
        description = "Generates Kotlin and Compose resources from shared/*.json (part of generateShared)."
    }

    @TaskAction
    fun generate() {
        val defs = loadDefinitions(sharedDir.get().asFile)
        val kotlinDir = kotlinOutputDir.get().asFile
        fs.delete { delete(kotlinDir) }
        val catalogDir = File(kotlinDir, "com/sledje/agentpro/core/catalog")
        File(catalogDir, "Catalog.kt").writeIfChanged(CatalogKotlinGenerator(defs).generate())
        File(catalogDir, "SiteSchema.kt").writeIfChanged(SiteSchemaKotlinGenerator(defs).generate())
        File(catalogDir, "Templates.kt").writeIfChanged(TemplatesKotlinGenerator(defs).generate())
        if (defs.siteSeed != null) File(catalogDir, "SiteSeed.kt").writeIfChanged(SiteSeedKotlinGenerator(defs).generate())
        if (PrivacyNotices(defs).present) {
            File(catalogDir, "PrivacyNotices.kt").writeIfChanged(PrivacyNoticesKotlinGenerator(defs).generate())
        }
        File(kotlinDir, "com/sledje/agentpro/core/designsystem/Tokens.kt").writeIfChanged(TokensKotlinGenerator(defs).generate())

        val props = Properties()
        localProperties.files.firstOrNull { it.isFile }?.reader(Charsets.UTF_8)?.use { props.load(it) }
        val values = props.stringPropertyNames().associateWith { props.getProperty(it) }
        File(kotlinDir, "com/sledje/agentpro/app/AppConfig.kt").writeIfChanged(AppConfigKotlinGenerator(values).generate())
        val missing = AppConfigKotlinGenerator.KEYS.filter { values[it].isNullOrBlank() }
        if (missing.isNotEmpty()) logger.info("AppConfig: not set in local.properties (empty strings used): ${missing.joinToString()}")

        val resDir = resourcesOutputDir.get().asFile
        fs.sync {
            from(handWrittenResourcesDir)
            into(resDir)
        }
        File(resDir, "values/strings_catalog.xml").writeIfChanged(StringsXmlGenerator.generate(defs.i18nEn, "en"))
        File(resDir, "values-hi/strings_catalog.xml").writeIfChanged(StringsXmlGenerator.generate(defs.i18nHi, "hi"))
    }
}

/**
 * Writes supabase/migrations/<ts>_catalog_sync.sql when the catalog hash changed, but only once schema
 * migrations exist. Writes into the source tree, so it is untracked and not part of compilation.
 */
@DisableCachingByDefault(because = "Writes into the source tree")
abstract class WriteCatalogSyncTask : DefaultTask() {
    @get:Internal abstract val sharedDir: DirectoryProperty
    @get:Internal abstract val migrationsDir: DirectoryProperty
    /** The keys lockfile: its `privacyNoticeHashes` keep released notice versions resolvable in SQL. */
    @get:Internal abstract val lockFile: RegularFileProperty

    init {
        group = "agentpro"
        description = "Writes supabase/migrations/<ts>_catalog_sync.sql when shared/ changed (part of generateShared)."
        doNotTrackState("Writes into supabase/migrations only when the catalog hash changes")
    }

    @TaskAction
    fun write() {
        val defs = loadDefinitions(sharedDir.get().asFile)
        val dir = migrationsDir.get().asFile
        val noticeLock = CatalogKeysLock.readNoticeHashes(lockFile.get().asFile)
        when (val plan = CatalogSyncGenerator(defs, noticeLock = noticeLock).plan(dir)) {
            is CatalogSyncGenerator.Plan.Skipped -> logger.lifecycle("catalog_sync: skipped, ${plan.reason}.")
            is CatalogSyncGenerator.Plan.UpToDate -> logger.lifecycle("catalog_sync: up to date (${plan.file.name}).")
            is CatalogSyncGenerator.Plan.Write -> {
                val file = File(dir, plan.fileName)
                file.writeText(plan.content)
                logger.lifecycle("catalog_sync: wrote supabase/migrations/${plan.fileName}. Apply it to Supabase and commit it.")
            }
        }
    }
}

/** Adds new catalog keys and notice versions to mobile/build-logic/catalog-keys.lock.json (never removes or changes any). */
@DisableCachingByDefault(because = "Writes into the source tree")
abstract class UpdateCatalogKeysLockTask : DefaultTask() {
    @get:Internal abstract val sharedDir: DirectoryProperty
    @get:Internal abstract val lockFile: RegularFileProperty

    init {
        group = "agentpro"
        description = "Records new catalog keys in the keys lockfile (part of generateShared)."
        doNotTrackState("Writes a committed source file only when keys were added")
    }

    @TaskAction
    fun update() {
        val defs = loadDefinitions(sharedDir.get().asFile)
        val file = lockFile.get().asFile
        val existing = CatalogKeysLock.read(file)
        val merged = CatalogKeysLock.merged(existing, defs)
        val existingNotices = CatalogKeysLock.readNoticeHashes(file)
        val mergedNotices = CatalogKeysLock.mergedNoticeHashes(existingNotices, defs)
        val text = CatalogKeysLock.render(merged, mergedNotices)
        if (!file.isFile || file.readText() != text) {
            file.writeText(text)
            val added = merged.entries.sumOf { (k, v) -> v.size - existing[k].orEmpty().size }
            val addedVersions = mergedNotices.entries.sumOf { (k, v) -> v.size - existingNotices[k].orEmpty().size }
            logger.lifecycle("catalog keys lock: recorded $added new key(s) and $addedVersions notice version(s) in ${file.name}. Commit it.")
        }
    }
}

/** Runs every SharedVerifier rule and fails listing all violations. Wired into `check`. */
@DisableCachingByDefault(because = "Cheap; reads sources directly")
abstract class VerifySharedTask : DefaultTask() {
    @get:Internal abstract val sharedDir: DirectoryProperty
    @get:Internal abstract val composeResourcesDir: DirectoryProperty
    @get:Internal abstract val migrationsDir: DirectoryProperty
    @get:Internal abstract val lockFile: RegularFileProperty
    @get:Internal abstract val displayRoot: DirectoryProperty
    @get:Internal abstract val kotlinSourceRoots: ConfigurableFileCollection

    /** Everything the rules read, for up-to-date checks. */
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) @get:IgnoreEmptyDirectories
    abstract val inputFiles: ConfigurableFileCollection

    @get:OutputFile abstract val reportFile: RegularFileProperty

    init {
        group = "verification"
        description = "Checks shared/*.json, translations, icons, tokens, catalog_sync and raw .dp/.sp usage."
    }

    @TaskAction
    fun verify() {
        val violations = try {
            val defs = SharedDefinitions.load(sharedDir.get().asFile)
            SharedVerifier(
                VerifyInputs(
                    defs = defs,
                    composeResourcesDir = composeResourcesDir.get().asFile,
                    kotlinSourceRoots = kotlinSourceRoots.files.toList(),
                    migrationsDir = migrationsDir.get().asFile,
                    keysLock = CatalogKeysLock.read(lockFile.get().asFile),
                    displayRoot = displayRoot.get().asFile,
                    noticeLock = CatalogKeysLock.readNoticeHashes(lockFile.get().asFile),
                    privacyNoticesJson = File(sharedDir.get().asFile, PrivacyNotices.WEB_JSON),
                )
            ).verify()
        } catch (e: SharedDefinitionsException) {
            e.problems
        }
        val report = reportFile.get().asFile
        report.parentFile.mkdirs()
        if (violations.isEmpty()) {
            report.writeText("OK\n")
            return
        }
        report.writeText(violations.joinToString("\n", postfix = "\n"))
        throw GradleException(
            "verifyShared found ${violations.size} problem(s):\n" + violations.joinToString("\n") { "  - $it" } +
                "\nSee docs/SHARED_DEFINITIONS.md (Codegen) and docs/DESIGN_SYSTEM.md (Enforcement)."
        )
    }
}

/**
 * Writes shared/generated/privacy-notices.json (notice versions, text keys and hashes for the web renderer)
 * when the catalog has privacyNotices. Writes into the source tree; verifyShared catches a stale file.
 */
@DisableCachingByDefault(because = "Writes into the source tree")
abstract class WritePrivacyNoticesTask : DefaultTask() {
    @get:Internal abstract val sharedDir: DirectoryProperty

    init {
        group = "agentpro"
        description = "Writes shared/generated/privacy-notices.json from privacyNotices + i18n (part of generateShared)."
        doNotTrackState("Writes a committed source file only when it changed")
    }

    @TaskAction
    fun write() {
        val shared = sharedDir.get().asFile
        val notices = PrivacyNotices(loadDefinitions(shared))
        if (!notices.present) return
        val file = File(shared, PrivacyNotices.WEB_JSON)
        val text = notices.webJson()
        if (!file.isFile || file.readText() != text) {
            file.writeIfChanged(text)
            logger.lifecycle("privacy notices: wrote shared/${PrivacyNotices.WEB_JSON}. Commit it.")
        }
    }
}

/**
 * Release gate: fails while any shared/i18n value still holds a `{UPPER_CASE}` fill-in placeholder such as
 * `{GRIEVANCE_EMAIL}` (docs/PRIVACY.md, Contact details). Not part of `check`, so debug builds and tests pass;
 * Android release builds depend on it (agentpro.site-preview), and it can be run on its own before any release.
 */
@DisableCachingByDefault(because = "Cheap; reads sources directly")
abstract class VerifyReleaseReadyTask : DefaultTask() {
    @get:Internal abstract val sharedDir: DirectoryProperty

    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputFiles: ConfigurableFileCollection

    @get:OutputFile abstract val reportFile: RegularFileProperty

    init {
        group = "verification"
        description = "Fails while shared/i18n still has {UPPER_CASE} placeholders (e.g. the grievance officer). Run before a release."
    }

    @TaskAction
    fun verify() {
        val violations = try {
            SharedVerifier.releaseBlockers(SharedDefinitions.load(sharedDir.get().asFile))
        } catch (e: SharedDefinitionsException) {
            e.problems
        }
        val report = reportFile.get().asFile
        report.parentFile.mkdirs()
        if (violations.isEmpty()) {
            report.writeText("OK\n")
            return
        }
        report.writeText(violations.joinToString("\n", postfix = "\n"))
        throw GradleException(
            "verifyReleaseReady found ${violations.size} placeholder(s) that must be filled in before a release:\n" +
                violations.joinToString("\n") { "  - $it" }
        )
    }
}

/** Copies web/ (if present) and shared/ into a generated Android assets dir for the in-app SitePreview. */
@DisableCachingByDefault(because = "Plain copy")
abstract class CopySitePreviewAssetsTask : DefaultTask() {
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) @get:IgnoreEmptyDirectories
    abstract val webFiles: ConfigurableFileCollection

    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) @get:IgnoreEmptyDirectories
    abstract val sharedFiles: ConfigurableFileCollection

    @get:Internal abstract val webDir: DirectoryProperty
    @get:Internal abstract val sharedDir: DirectoryProperty

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @get:Inject abstract val fs: FileSystemOperations

    init {
        group = "agentpro"
        description = "Copies web/ and shared/ into Android assets (site/web, site/shared) for SitePreview, plus site/web/tokens.css."
    }

    @TaskAction
    fun copy() {
        val web = webDir.get().asFile
        val shared = sharedDir.get().asFile
        fs.sync {
            into(outputDir)
            if (web.isDirectory) {
                from(web) {
                    into("site/web")
                    exclude("dist/**", "node_modules/**", "config.js")
                }
            }
            from(shared) { into("site/shared") }
        }
        // The hosted site gets tokens.css from scripts/build-web.sh; the preview needs its own copy.
        if (web.isDirectory) {
            val tokens = SharedDefinitions.load(shared).designTokens
            File(outputDir.get().asFile, "site/web/tokens.css").writeText(TokensCssGenerator.generate(tokens))
        }
        if (!web.isDirectory) logger.info("site preview: web/ doesn't exist yet; copied shared/ only")
    }
}
