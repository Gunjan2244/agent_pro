package com.sledje.agentpro.buildlogic

import com.sledje.agentpro.buildlogic.codegen.CatalogKeysLock
import com.sledje.agentpro.buildlogic.codegen.NoticeHashes
import com.sledje.agentpro.buildlogic.codegen.PrivacyNotices
import com.sledje.agentpro.buildlogic.codegen.SharedDefinitions
import com.sledje.agentpro.buildlogic.verify.SharedVerifier
import com.sledje.agentpro.buildlogic.verify.VerifyInputs
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.nio.file.Files

/**
 * A writable copy of src/test/resources/fixture (a small but complete, valid shared/ set plus
 * composeResources) in a temp dir. Tests mutate files to produce failing cases.
 */
class Fixture {
    val root: File = Files.createTempDirectory("agentpro-fixture").toFile().also { dir ->
        File(javaClass.getResource("/fixture")!!.toURI()).copyRecursively(dir)
    }
    val shared = File(root, "shared")
    val composeResources = File(root, "composeResources")
    val migrations = File(root, "supabase/migrations")
    val kotlinSrc = File(root, "src").also { it.mkdirs() }

    fun file(rel: String) = File(root, rel)

    /** Replaces exactly one occurrence of [old] in [rel]. */
    fun replace(rel: String, old: String, new: String) {
        val f = file(rel)
        val text = f.readText()
        require(text.split(old).size == 2) { "\"$old\" must occur exactly once in $rel" }
        f.writeText(text.replace(old, new))
    }

    fun defs(): SharedDefinitions = SharedDefinitions.load(shared)

    /** A lock that matches the current catalog exactly. */
    fun currentLock(): Map<String, List<String>> = CatalogKeysLock.merged(emptyMap(), defs())

    /** Notice hashes that match the current notices exactly. */
    fun currentNoticeLock(): NoticeHashes = CatalogKeysLock.mergedNoticeHashes(emptyMap(), defs())

    val privacyNoticesJson: File get() = File(shared, PrivacyNotices.WEB_JSON)

    fun verifier(lock: Map<String, List<String>> = currentLock(), noticeLock: NoticeHashes = currentNoticeLock()) = SharedVerifier(
        VerifyInputs(
            defs = defs(),
            composeResourcesDir = composeResources,
            kotlinSourceRoots = listOf(kotlinSrc),
            migrationsDir = migrations,
            keysLock = lock,
            displayRoot = root,
            noticeLock = noticeLock,
            privacyNoticesJson = privacyNoticesJson,
        )
    )

    /**
     * Adds a small privacy set to the fixture: two notices (one with an upper-case release placeholder),
     * dataRequestKinds, dataRequestStatuses and a retention object, plus their en/hi texts, and writes
     * shared/generated/privacy-notices.json. The result passes every verifyShared rule.
     */
    fun addPrivacy(): Fixture {
        replace("shared/catalog.json", "  \"pricing\": {", """
          |  "privacyNotices": [
          |    { "key": "agent_signup", "labelKey": "privacy.agent_signup.title", "order": 1, "version": 1,
          |      "textKeys": ["privacy.agent_signup.consent", "privacy.agent_signup.title", "privacy.agent_signup.grievance"] },
          |    { "key": "enquiry_form", "labelKey": "privacy.enquiry_form.title", "order": 2, "version": 2,
          |      "textKeys": ["privacy.enquiry_form.consent", "privacy.enquiry_form.title"] }
          |  ],
          |  "dataRequestKinds": [
          |    { "key": "access", "labelKey": "data_request_kind.access", "icon": "person", "tone": "neutral", "order": 1 },
          |    { "key": "erasure", "labelKey": "data_request_kind.erasure", "icon": "error", "tone": "neutral", "order": 2 }
          |  ],
          |  "dataRequestStatuses": [
          |    { "key": "open", "labelKey": "data_request_status.open", "icon": "hourglass_empty", "tone": "warning", "order": 1, "terminal": false },
          |    { "key": "done", "labelKey": "data_request_status.done", "icon": "check_circle", "tone": "success", "order": 2, "terminal": true }
          |  ],
          |  "retention": { "softDeletedDays": 30, "lapsedAccount": { "deleteAfterMonths": 12, "noticeDaysBefore": [30, 7, 2] } },
          |  "pricing": {""".trimMargin())
        val en = linkedMapOf(
            "privacy.agent_signup.consent" to "I'm 18 or older, and I agree to the Terms and the Privacy notice.",
            "privacy.agent_signup.title" to "Privacy notice",
            "privacy.agent_signup.grievance" to "Grievance officer: {GRIEVANCE_EMAIL}",
            "privacy.enquiry_form.consent" to "I agree that {name} may contact me.",
            "privacy.enquiry_form.title" to "How your data is used",
            "data_request_kind.access" to "See my data",
            "data_request_kind.erasure" to "Delete my data",
            "data_request_status.open" to "Open",
            "data_request_status.done" to "Done",
        )
        val hi = linkedMapOf(
            "privacy.agent_signup.consent" to "मेरी आयु 18 वर्ष या उससे अधिक है, और मैं शर्तों और गोपनीयता सूचना से सहमत हूँ।",
            "privacy.agent_signup.title" to "गोपनीयता सूचना",
            "privacy.agent_signup.grievance" to "शिकायत अधिकारी: {GRIEVANCE_EMAIL}",
            "privacy.enquiry_form.consent" to "मैं सहमत हूँ कि {name} मुझसे संपर्क कर सकते हैं।",
            "privacy.enquiry_form.title" to "आपके डेटा का उपयोग कैसे होगा",
            "data_request_kind.access" to "मेरा डेटा दिखाएँ",
            "data_request_kind.erasure" to "मेरा डेटा हटाएँ",
            "data_request_status.open" to "खुला",
            "data_request_status.done" to "पूरा हुआ",
        )
        fun append(rel: String, entries: Map<String, String>) {
            val f = file(rel)
            val body = entries.entries.joinToString("") { (k, v) -> ",\n  " + JsonPrimitive(k) + ": " + JsonPrimitive(v) }
            f.writeText(f.readText().trimEnd().removeSuffix("}").trimEnd() + body + "\n}\n")
        }
        append("shared/i18n/en.json", en)
        append("shared/i18n/hi.json", hi)
        writePrivacyJson()
        return this
    }

    fun writePrivacyJson() = privacyNoticesJson.apply { parentFile.mkdirs(); writeText(PrivacyNotices(defs()).webJson()) }

    fun writeKotlin(rel: String, code: String) = File(kotlinSrc, rel).apply { parentFile.mkdirs(); writeText(code) }

    fun addSchemaMigration(name: String = "20261015100000_schema.sql") =
        File(migrations, name).apply { parentFile.mkdirs(); writeText("create table public.enquiries (id uuid);\n") }
}

/** The real repo's shared/ (tests that use it are skipped when it isn't there). */
fun realSharedDir(): File? {
    var dir: File? = File("").absoluteFile
    while (dir != null) {
        val candidate = File(dir, "shared/catalog.json")
        if (candidate.isFile && File(dir, "mobile").isDirectory) return candidate.parentFile
        dir = dir.parentFile
    }
    return null
}
