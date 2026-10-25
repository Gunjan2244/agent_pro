package com.sledje.agentpro.buildlogic

import com.sledje.agentpro.buildlogic.codegen.CatalogKeysLock
import com.sledje.agentpro.buildlogic.codegen.CatalogKotlinGenerator
import com.sledje.agentpro.buildlogic.codegen.CatalogSyncGenerator
import com.sledje.agentpro.buildlogic.codegen.PrivacyNoticeHashing
import com.sledje.agentpro.buildlogic.codegen.PrivacyNotices
import com.sledje.agentpro.buildlogic.codegen.PrivacyNoticesKotlinGenerator
import com.sledje.agentpro.buildlogic.codegen.SharedDefinitions
import com.sledje.agentpro.buildlogic.codegen.SharedDefinitionsException
import com.sledje.agentpro.buildlogic.verify.SharedVerifier
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Privacy notices: hashing, generated Kotlin/JSON/SQL, the lockfile and the verifyShared/release rules. */
class PrivacyNoticesTest {
    private val now = Instant.parse("2026-10-17T12:00:00Z")

    private fun assertViolation(violations: List<String>, vararg fragments: String) {
        assertTrue(violations.any { v -> fragments.all { it in v } }, "expected a violation containing ${fragments.toList()}, got:\n${violations.joinToString("\n")}")
    }

    private fun notice(defs: SharedDefinitions, key: String) = PrivacyNotices(defs).notices.single { it.key == key }

    // ---- hashing --------------------------------------------------------------------------------

    @Test fun `hash matches JSON stringify + sha256 (cross-implementation reference)`() {
        // node -e 'h=o=>require("crypto").createHash("sha256").update(JSON.stringify(o)).digest("hex");
        //          console.log(h({key:"demo",locale:"hi",text:"नमस्ते {name}\n“quoted” \"x\" \\ end",version:2}))'
        assertEquals(
            "76b97e1244540a5315fbc33000a775a3395a1b2be6f62ad2f0879767fa81f05d",
            PrivacyNoticeHashing.hash("demo", 2, "hi", listOf("नमस्ते {name}", "“quoted” \"x\" \\ end")),
        )
        assertEquals(
            "{\"key\":\"demo\",\"locale\":\"en\",\"text\":\"a\\nb\",\"version\":1}",
            PrivacyNoticeHashing.canonicalPayload("demo", 1, "en", listOf("a", "b")),
        )
    }

    @Test fun `hash is stable across loads and runs`() {
        val fx = Fixture().addPrivacy()
        val a = PrivacyNotices(fx.defs()).current
        val b = PrivacyNotices(SharedDefinitions.load(fx.shared)).current
        assertEquals(a, b)
        assertEquals(setOf("en", "hi"), a.getValue("agent_signup").getValue(1).keys)
        a.values.flatMap { it.values }.flatMap { it.values }.forEach { assertTrue(Regex("^[0-9a-f]{64}$").matches(it), it) }
    }

    @Test fun `hash changes with the text and with the version, and differs per locale`() {
        val fx = Fixture().addPrivacy()
        val before = PrivacyNotices(fx.defs()).hashes(notice(fx.defs(), "enquiry_form"))
        assertNotEquals(before.getValue("en"), before.getValue("hi"), "EN and HI hash differently")

        fx.replace("shared/i18n/en.json", "may contact me.", "may contact me by phone.")
        val textChanged = PrivacyNotices(fx.defs()).hashes(notice(fx.defs(), "enquiry_form"))
        assertNotEquals(before.getValue("en"), textChanged.getValue("en"))
        assertEquals(before.getValue("hi"), textChanged.getValue("hi"), "only the edited locale changes")

        fx.replace("shared/catalog.json", "\"order\": 2, \"version\": 2", "\"order\": 2, \"version\": 3")
        val versionChanged = PrivacyNotices(fx.defs()).hashes(notice(fx.defs(), "enquiry_form"))
        assertNotEquals(textChanged.getValue("hi"), versionChanged.getValue("hi"))
    }

    @Test fun `canonicalization - NFC and line endings don't change the hash, text order does`() {
        val composed = "é" // é
        val decomposed = "é"
        assertEquals(PrivacyNoticeHashing.hash("k", 1, "en", listOf(composed)), PrivacyNoticeHashing.hash("k", 1, "en", listOf(decomposed)))
        assertEquals(PrivacyNoticeHashing.hash("k", 1, "en", listOf("a\r\nb")), PrivacyNoticeHashing.hash("k", 1, "en", listOf("a\nb")))
        assertEquals(PrivacyNoticeHashing.hash("k", 1, "en", listOf("a", "b")), PrivacyNoticeHashing.hash("k", 1, "en", listOf("a\nb")))
        assertNotEquals(PrivacyNoticeHashing.hash("k", 1, "en", listOf("a", "b")), PrivacyNoticeHashing.hash("k", 1, "en", listOf("b", "a")))
        assertNotEquals(PrivacyNoticeHashing.hash("k", 1, "en", listOf("a")), PrivacyNoticeHashing.hash("other", 1, "en", listOf("a")))
    }

    @Test fun `catalog hash covers notice text but not unrelated i18n`() {
        val plain = Fixture()
        val plainHash = plain.defs().catalogHash
        plain.replace("shared/i18n/en.json", "\"Get a free quote\"", "\"Get a quote\"")
        assertEquals(plainHash, plain.defs().catalogHash, "i18n isn't part of the hash without notices")

        val fx = Fixture().addPrivacy()
        val before = fx.defs().catalogHash
        fx.replace("shared/i18n/en.json", "\"Get a free quote\"", "\"Get a quote\"")
        assertEquals(before, fx.defs().catalogHash, "a non-notice string doesn't change it")
        fx.replace("shared/i18n/hi.json", "मुझसे संपर्क कर सकते हैं।", "मुझसे फ़ोन पर संपर्क कर सकते हैं।")
        assertNotEquals(before, fx.defs().catalogHash, "a notice text change does")
    }

    // ---- generated Kotlin -------------------------------------------------------------------------

    @Test fun `PrivacyNotices kt has versions, texts and hashes, and Catalog kt the other lists`() {
        val fx = Fixture().addPrivacy()
        val defs = fx.defs()
        val pn = PrivacyNotices(defs)
        val code = PrivacyNoticesKotlinGenerator(defs).generate()
        val en = pn.hashes(notice(defs, "agent_signup")).getValue("en")
        val hi = pn.hashes(notice(defs, "agent_signup")).getValue("hi")
        listOf(
            "package com.sledje.agentpro.core.catalog",
            "enum class PrivacyNotice(",
            ") : CatalogEntry {",
            "AgentSignup(\"agent_signup\", Res.string.cat_privacy_agent_signup_title, 1, version = 1, " +
                "texts = listOf(Res.string.cat_privacy_agent_signup_consent, Res.string.cat_privacy_agent_signup_title, Res.string.cat_privacy_agent_signup_grievance), " +
                "hashes = mapOf(\"en\" to \"$en\", \"hi\" to \"$hi\")),",
            "EnquiryForm(\"enquiry_form\", Res.string.cat_privacy_enquiry_form_title, 2, version = 2,",
            "fun hash(locale: AppLocale): String = hashes.getValue(locale.key)",
            "fun consent(locale: AppLocale): NoticeConsent = NoticeConsent(key, version, hash(locale))",
            "const val CANONICALIZATION: Int = 1",
            "data class NoticeConsent(val noticeKey: String, val version: Int, val hash: String)",
        ).forEach { assertTrue(it in code, "missing:\n$it\n--- in ---\n$code") }

        val catalog = CatalogKotlinGenerator(defs).generate()
        assertFalse("enum class PrivacyNotice(" in catalog, "privacyNotices has its own file")
        assertTrue("enum class DataRequestKind(" in catalog && "Access(\"access\", Res.string.cat_data_request_kind_access, Res.drawable.ic_person, Tone.Neutral, 1)," in catalog)
        assertTrue("enum class DataRequestStatus(" in catalog && "Done(\"done\", Res.string.cat_data_request_status_done, Res.drawable.ic_check_circle, Tone.Success, 2, terminal = true)," in catalog)
        assertTrue("object Retention {" in catalog && "const val softDeletedDays: Int = 30" in catalog, catalog)
        assertTrue("object LapsedAccount {" in catalog && "val noticeDaysBefore: List<Int> = listOf(30, 7, 2)" in catalog)
    }

    // ---- generated web JSON -----------------------------------------------------------------------

    @Test fun `web JSON lists active notices with text keys, hashes and release blockers`() {
        val fx = Fixture().addPrivacy()
        fx.replace("shared/catalog.json", "\"order\": 2, \"version\": 2,", "\"order\": 2, \"version\": 2, \"deprecated\": true,")
        val defs = fx.defs()
        val root = Json.parseToJsonElement(PrivacyNotices(defs).webJson()).jsonObject
        assertEquals(1, root.getValue("canonicalization").jsonPrimitive.content.toInt())
        assertEquals(listOf("{GRIEVANCE_EMAIL}"), root.getValue("releaseBlockers").jsonArray.map { it.jsonPrimitive.content })
        val notices = root.getValue("notices").jsonObject
        assertEquals(setOf("agent_signup"), notices.keys, "deprecated notices are left out")
        val signup = notices.getValue("agent_signup").jsonObject
        assertEquals(1, signup.getValue("version").jsonPrimitive.content.toInt())
        assertEquals("privacy.agent_signup.title", signup.getValue("labelKey").jsonPrimitive.content)
        assertEquals(3, signup.getValue("textKeys").jsonArray.size)
        val hashes = PrivacyNotices(defs).hashes(notice(defs, "agent_signup"))
        assertEquals(hashes, signup.getValue("hash").jsonObject.mapValues { it.value.jsonPrimitive.content })
    }

    // ---- catalog_sync SQL -------------------------------------------------------------------------

    @Test fun `catalog_sync emits the notice and data-request functions and the retention row`() {
        val fx = Fixture().addPrivacy()
        val defs = fx.defs()
        val pn = PrivacyNotices(defs)
        val signup = pn.hashes(notice(defs, "agent_signup"))
        val enquiry = pn.hashes(notice(defs, "enquiry_form"))
        // Version 1 of enquiry_form was released earlier; only the lockfile still knows its hashes.
        val lock = CatalogKeysLock.mergedNoticeHashes(mapOf("enquiry_form" to mapOf(1 to mapOf("en" to "a".repeat(64), "hi" to "b".repeat(64)))), defs)
        val sql = CatalogSyncGenerator(defs, lock) { now }.sql()
        listOf(
            "create or replace function public.catalog_privacy_notice(p_key text)\nreturns jsonb\nlanguage sql\nimmutable\nset search_path = ''\n",
            "select case p_key when 'agent_signup' then '{\"version\":1,\"hash_en\":\"${signup["en"]}\",\"hash_hi\":\"${signup["hi"]}\"}'::jsonb " +
                "when 'enquiry_form' then '{\"version\":2,\"hash_en\":\"${enquiry["en"]}\",\"hash_hi\":\"${enquiry["hi"]}\"}'::jsonb else null end",
            "revoke all on function public.catalog_privacy_notice(text) from public, anon, authenticated;",
            "create or replace function public.catalog_privacy_notice_hash(p_key text, p_version integer, p_locale text)\nreturns text",
            "('enquiry_form', 1, 'en', '${"a".repeat(64)}')",
            "('enquiry_form', 2, 'hi', '${enquiry["hi"]}')",
            "where t.k = p_key and t.v = p_version and t.l = p_locale",
            "revoke all on function public.catalog_privacy_notice_hash(text, integer, text) from public, anon, authenticated;",
            "create or replace function public.catalog_privacy_notice_locale(p_key text, p_version integer, p_hash text)\nreturns text",
            "where t.k = p_key and t.v = p_version and t.h = p_hash",
            "revoke all on function public.catalog_privacy_notice_locale(text, integer, text) from public, anon, authenticated;",
            "create or replace function public.catalog_data_request_kinds()\nreturns text[]\nlanguage sql\nimmutable\nset search_path = ''\nas \$\$\n  select array['access', 'erasure']::text[]\n\$\$;",
            "revoke all on function public.catalog_data_request_kinds() from public, anon, authenticated;",
            "select array['open', 'done']::text[]",
            "revoke all on function public.catalog_data_request_statuses() from public, anon, authenticated;",
            "  ('retention', '{\"softDeletedDays\":30,\"lapsedAccount\":{\"deleteAfterMonths\":12,\"noticeDaysBefore\":[30,7,2]}}'::jsonb)\non conflict (key) do update set value = excluded.value;",
        ).forEach { assertTrue(it in sql, "missing:\n$it\n--- in ---\n$sql") }
        assertEquals(13, Regex("add constraint \\w+_check check").findAll(sql).count(), "the 10 base CHECKs plus consents.notice_key, data_requests.kind and data_requests.status")
        assertTrue(sql.lines()[2].startsWith("-- catalog-hash: sha256:"))
    }

    @Test fun `deprecated notices can't take new consents but their hashes stay resolvable`() {
        val fx = Fixture().addPrivacy()
        fx.replace("shared/catalog.json", "\"order\": 2, \"version\": 2,", "\"order\": 2, \"version\": 2, \"deprecated\": true,")
        val sql = CatalogSyncGenerator(fx.defs()) { now }.sql()
        val noticeFn = sql.substringAfter("function public.catalog_privacy_notice(p_key text)").substringBefore("revoke")
        assertFalse("'enquiry_form'" in noticeFn, noticeFn)
        assertTrue("('enquiry_form', 2, 'en', '" in sql.substringAfter("function public.catalog_privacy_notice_hash("))
    }

    @Test fun `a notice text change produces a new catalog_sync`() {
        val fx = Fixture().addPrivacy()
        fx.addSchemaMigration()
        val plan = CatalogSyncGenerator(fx.defs()) { now }.plan(fx.migrations) as CatalogSyncGenerator.Plan.Write
        File(fx.migrations, plan.fileName).writeText(plan.content)
        fx.replace("shared/i18n/en.json", "may contact me.", "may contact me by phone.")
        fx.replace("shared/catalog.json", "\"order\": 2, \"version\": 2", "\"order\": 2, \"version\": 3")
        assertTrue(CatalogSyncGenerator(fx.defs()) { now.plusSeconds(60) }.plan(fx.migrations) is CatalogSyncGenerator.Plan.Write)
    }

    // ---- lockfile ---------------------------------------------------------------------------------

    @Test fun `lock adds new notice versions and never overwrites a locked one`() {
        val fx = Fixture().addPrivacy()
        val first = fx.currentNoticeLock()
        assertEquals(setOf(1), first.getValue("agent_signup").keys)
        assertEquals(setOf(2), first.getValue("enquiry_form").keys)

        fx.replace("shared/i18n/en.json", "may contact me.", "may contact me by phone.")
        val same = CatalogKeysLock.mergedNoticeHashes(first, fx.defs())
        assertEquals(first, same, "a changed text with the same version isn't relocked")

        fx.replace("shared/catalog.json", "\"order\": 2, \"version\": 2", "\"order\": 2, \"version\": 3")
        val bumped = CatalogKeysLock.mergedNoticeHashes(first, fx.defs())
        assertEquals(setOf(2, 3), bumped.getValue("enquiry_form").keys)
        assertEquals(first.getValue("enquiry_form").getValue(2), bumped.getValue("enquiry_form").getValue(2))
    }

    @Test fun `lockfile round-trips notice hashes and keeps the old format without them`() {
        val fx = Fixture().addPrivacy()
        val file = Files.createTempFile("lock", ".json").toFile()
        file.writeText(CatalogKeysLock.render(fx.currentLock(), fx.currentNoticeLock()))
        assertEquals(fx.currentLock(), CatalogKeysLock.read(file))
        assertEquals(fx.currentNoticeLock(), CatalogKeysLock.readNoticeHashes(file))
        assertTrue("\"privacyNoticeHashes\": {" in file.readText())

        val plain = Fixture()
        val text = CatalogKeysLock.render(plain.currentLock(), plain.currentNoticeLock())
        assertEquals(CatalogKeysLock.render(plain.currentLock()), text)
        assertFalse("privacyNoticeHashes" in text)
    }

    // ---- verifyShared -----------------------------------------------------------------------------

    @Test fun `the privacy fixture passes every rule`() {
        assertEquals(emptyList(), Fixture().addPrivacy().verifier().verify())
    }

    @Test fun `changed notice text without a new version fails`() {
        val fx = Fixture().addPrivacy()
        val lock = fx.currentNoticeLock()
        fx.replace("shared/i18n/hi.json", "मुझसे संपर्क कर सकते हैं।", "मुझसे फ़ोन पर संपर्क कर सकते हैं।")
        fx.writePrivacyJson()
        assertViolation(fx.verifier(noticeLock = lock).checkPrivacy(), "catalog.privacyNotices.enquiry_form", "hi text of version 2 changed", "new \"version\"")

        fx.replace("shared/catalog.json", "\"order\": 2, \"version\": 2", "\"order\": 2, \"version\": 3")
        fx.writePrivacyJson()
        assertViolation(fx.verifier(noticeLock = lock).checkPrivacy(), "enquiry_form", "version 3 isn't in", "generateShared")
        assertEquals(emptyList(), fx.verifier(noticeLock = CatalogKeysLock.mergedNoticeHashes(lock, fx.defs())).checkPrivacy())
    }

    @Test fun `a version below the newest locked one fails`() {
        val fx = Fixture().addPrivacy()
        val lock = fx.currentNoticeLock()
        fx.replace("shared/catalog.json", "\"order\": 2, \"version\": 2", "\"order\": 2, \"version\": 1")
        fx.writePrivacyJson()
        assertViolation(fx.verifier(noticeLock = lock).checkPrivacy(), "enquiry_form", "version 1 is lower than the newest locked version 2")
    }

    @Test fun `stale or missing web JSON fails`() {
        val fx = Fixture().addPrivacy()
        fx.privacyNoticesJson.writeText("{}\n")
        assertViolation(fx.verifier().checkPrivacy(), "shared/generated/privacy-notices.json", "out of date", "generateShared")
        fx.privacyNoticesJson.delete()
        assertViolation(fx.verifier().checkPrivacy(), "privacy-notices.json is missing")
    }

    @Test fun `malformed notices, missing texts and bad retention fail`() {
        val fx = Fixture().addPrivacy()
        fx.replace("shared/catalog.json", "\"order\": 1, \"version\": 1,", "\"order\": 1, \"version\": 0,")
        fx.replace("shared/catalog.json", "\"textKeys\": [\"privacy.enquiry_form.consent\", \"privacy.enquiry_form.title\"]",
            "\"textKeys\": [\"privacy.enquiry_form.consent\", \"privacy.enquiry_form.consent\", \"privacy.enquiry_form.missing\"]")
        fx.replace("shared/catalog.json", "\"softDeletedDays\": 30", "\"softDeletedDays\": -1, \"x\": \"30 days\", \"y\": []")
        val v = fx.verifier().verify()
        assertViolation(v, "catalog.privacyNotices.agent_signup", "\"version\" must be a whole number >= 1")
        assertViolation(v, "catalog.privacyNotices.enquiry_form", "\"privacy.enquiry_form.consent\" is listed twice")
        assertViolation(v, "catalog.privacyNotices.enquiry_form.textKeys", "\"privacy.enquiry_form.missing\" is missing from i18n/en.json")
        assertViolation(v, "catalog.retention.softDeletedDays", "whole number >= 0")
        assertViolation(v, "catalog.retention.x")
        assertViolation(v, "catalog.retention.y", "non-empty list")
    }

    @Test fun `new privacy lists need lockfile entries, labels and icons like any catalog list`() {
        val fx = Fixture().addPrivacy()
        val lock = fx.currentLock().filterKeys { it != "dataRequestKinds" }
        assertViolation(fx.verifier(lock).checkKeys(), "catalog.dataRequestKinds", "\"access\" isn't in")
        fx.replace("shared/catalog.json", "\"icon\": \"person\"", "\"icon\": \"no_such_icon\"")
        assertViolation(fx.verifier().checkTonesAndIcons(), "dataRequestKinds.access", "unknown icon")
        assertEquals(emptyList(), fx.verifier().checkTonesAndIcons().filter { "privacyNotices" in it }, "notices need no icon")
    }

    // ---- release gate -----------------------------------------------------------------------------

    @Test fun `release placeholders fail only the release gate`() {
        val fx = Fixture().addPrivacy()
        val verifier = fx.verifier()
        assertEquals(emptyList(), verifier.verify(), "debug builds and check pass with placeholders")
        val blockers = verifier.checkReleaseReady()
        assertEquals(2, blockers.size, blockers.toString())
        assertViolation(blockers, "i18n/en.json", "\"privacy.agent_signup.grievance\"", "{GRIEVANCE_EMAIL}", "before a release")
        assertViolation(blockers, "i18n/hi.json", "{GRIEVANCE_EMAIL}")

        fx.replace("shared/i18n/en.json", "{GRIEVANCE_EMAIL}", "privacy@example.com")
        fx.replace("shared/i18n/hi.json", "{GRIEVANCE_EMAIL}", "privacy@example.com")
        assertEquals(emptyList(), SharedVerifier.releaseBlockers(fx.defs()))
        assertEquals(emptyList(), Fixture().verifier().checkReleaseReady(), "lower-case {name} placeholders are fine")
    }

    // ---- the real shared/ -------------------------------------------------------------------------

    @Test fun `real shared notices hash the same in Node (JSON stringify, NFC)`() {
        val dir = realSharedDir() ?: return
        val defs = try { SharedDefinitions.load(dir) } catch (e: SharedDefinitionsException) { return }
        val pn = PrivacyNotices(defs)
        if (!pn.present) return
        val script = """
            const fs = require("fs"), c = require("crypto"), d = process.argv[1];
            const cat = JSON.parse(fs.readFileSync(d + "/catalog.json", "utf8"));
            const out = [];
            for (const l of ["en", "hi"]) {
              const t = JSON.parse(fs.readFileSync(d + "/i18n/" + l + ".json", "utf8"));
              for (const n of cat.privacyNotices) {
                const text = n.textKeys.map((k) => t[k].replace(/\r\n?/g, "\n").normalize("NFC")).join("\n");
                const h = c.createHash("sha256").update(JSON.stringify({ key: n.key, locale: l, text, version: n.version })).digest("hex");
                out.push(n.key + "/" + l + "=" + h);
              }
            }
            console.log(out.sort().join("\n"));
        """.trimIndent()
        val process = try {
            ProcessBuilder("node", "-e", script, dir.absolutePath).redirectErrorStream(true).start()
        } catch (e: Exception) {
            return // node isn't installed
        }
        if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) return
        val node = process.inputStream.bufferedReader().readText().trim()
        val kotlin = pn.notices.flatMap { n -> pn.hashes(n).map { (l, h) -> "${n.key}/$l=$h" } }.sorted().joinToString("\n")
        assertEquals(kotlin, node)
    }

    @Test fun `real shared notices have every locale and no unknown text keys`() {
        val dir = realSharedDir() ?: return
        val defs = try { SharedDefinitions.load(dir) } catch (e: SharedDefinitionsException) { return }
        val pn = PrivacyNotices(defs)
        if (!pn.present) return
        pn.notices.forEach { n -> assertEquals(pn.locales.toSet(), pn.hashes(n).keys, n.key) }
        assertNull(pn.notices.firstOrNull { it.textKeys.isEmpty() })
    }

    // ---- customer portal limits -------------------------------------------------------------------

    @Test fun `portal is optional, valid portal passes, and it joins the app_config upsert`() {
        assertEquals(emptyList(), Fixture().addPrivacy().verifier().checkPortal())
        assertFalse("('portal'," in CatalogSyncGenerator(Fixture().addPrivacy().defs()) { now }.sql())
        val fx = Fixture().addPrivacy().addPortal()
        assertEquals(emptyList(), fx.verifier().checkPortal())
        val sql = CatalogSyncGenerator(fx.defs()) { now }.sql()
        assertTrue(
            ",\n  ('portal', '{\"pinLength\":6,\"inviteDays\":7,\"inviteMaxPhoneTries\":5,\"sessionDays\":90,\"maxFailedAttempts\":5," +
                "\"lockMinutes\":15,\"hardLockAfter\":20,\"enquiriesPerHour\":5,\"paymentsShown\":12,\"remindDaysBefore\":[7,1],\"failureWindowDays\":30," +
                "\"hardLockHours\":24,\"siteFailedLoginsPerMinute\":30,\"blockedPins\":[\"123456\",\"000000\"]}'::jsonb)\non conflict" in sql, sql
        )
        val kotlin = CatalogKotlinGenerator(fx.defs()).generate()
        assertTrue("object Portal {" in kotlin && "val remindDaysBefore: List<Int> = listOf(7, 1)" in kotlin, kotlin)
    }

    @Test fun `a bad portal object fails`() {
        fun violations(edit: (Fixture) -> Unit): List<String> = Fixture().addPrivacy().addPortal().also(edit).verifier().checkPortal()
        fun has(v: List<String>, vararg f: String) = assertTrue(v.any { m -> f.all { it in m } }, v.joinToString("\n"))
        has(violations { it.replace("shared/catalog.json", "\"pinLength\": 6", "\"pinLength\": 0") }, "pinLength", "> 0")
        has(violations { it.replace("shared/catalog.json", "\"lockMinutes\": 15", "\"lockMinutes\": \"15\"") }, "lockMinutes")
        has(violations { it.replace("shared/catalog.json", "\"paymentsShown\": 12,", "") }, "paymentsShown", "missing")
        has(violations { it.replace("shared/catalog.json", "\"hardLockAfter\": 20", "\"hardLockAfter\": 5") }, "hardLockAfter", "greater")
        has(violations { it.replace("shared/catalog.json", "[7, 1]", "[7, 7]") }, "remindDaysBefore", "distinct")
        has(violations { it.replace("shared/catalog.json", "[7, 1]", "[]") }, "remindDaysBefore")
        has(violations { it.replace("shared/catalog.json", "\"pinLength\": 6,", "\"pinLength\": 6, \"extra\": 1,") }, "catalog.portal.extra", "unknown")
        has(violations { it.replace("shared/catalog.json", "\"hardLockHours\": 24", "\"hardLockHours\": 0") }, "hardLockHours", "> 0")
        has(violations { it.replace("shared/catalog.json", "\"failureWindowDays\": 30, ", "") }, "failureWindowDays", "missing")
        has(violations { it.replace("shared/catalog.json", "\"siteFailedLoginsPerMinute\": 30", "\"siteFailedLoginsPerMinute\": \"x\"") }, "siteFailedLoginsPerMinute")
        has(violations { it.replace("shared/catalog.json", "\"000000\"", "\"12ab56\"") }, "blockedPins[1]", "pinLength")
        has(violations { it.replace("shared/catalog.json", "\"000000\"", "\"123456\"") }, "blockedPins", "duplicated")
        has(violations { it.replace("shared/catalog.json", "\"000000\"", "\"12345\"") }, "blockedPins[1]", "pinLength")
        has(violations { it.replace("shared/catalog.json", "[\"123456\", \"000000\"]", "[]") }, "blockedPins", "empty")
    }

    // ---- customer sign-up link limits (DECISIONS #108) ---------------------------------------------

    @Test fun `customerSignup is optional, valid passes, and it joins the app_config upsert`() {
        assertEquals(emptyList(), Fixture().addPrivacy().verifier().checkCustomerSignup())
        assertFalse("('customer_signup'," in CatalogSyncGenerator(Fixture().addPrivacy().defs()) { now }.sql())
        val fx = Fixture().addPrivacy().addCustomerSignup()
        assertEquals(emptyList(), fx.verifier().checkCustomerSignup())
        val sql = CatalogSyncGenerator(fx.defs()) { now }.sql()
        assertTrue(
            ",\n  ('customer_signup', '{\"linkDays\":7,\"savesPerMinute\":60,\"openLinksPerAgent\":25,\"presenceSeconds\":30}'::jsonb)" in sql, sql,
        )
    }

    @Test fun `a bad customerSignup object fails`() {
        fun violations(edit: (Fixture) -> Unit): List<String> =
            Fixture().addPrivacy().addCustomerSignup().also(edit).verifier().checkCustomerSignup()
        fun has(v: List<String>, vararg f: String) = assertTrue(v.any { m -> f.all { it in m } }, v.joinToString("\n"))
        has(violations { it.replace("shared/catalog.json", "\"linkDays\": 7", "\"linkDays\": 0") }, "linkDays", "> 0")
        has(violations { it.replace("shared/catalog.json", "\"savesPerMinute\": 60, ", "") }, "savesPerMinute", "missing")
        has(violations { it.replace("shared/catalog.json", "\"presenceSeconds\": 30", "\"presenceSeconds\": \"30\"") }, "presenceSeconds")
        has(violations { it.replace("shared/catalog.json", "\"linkDays\": 7,", "\"linkDays\": 7, \"extra\": 1,") }, "catalog.customerSignup.extra", "unknown")
        has(
            Fixture().addPrivacy().also { it.replace("shared/catalog.json", "  \"pricing\": {", "  \"customerSignup\": [],\n  \"pricing\": {") }
                .verifier().checkCustomerSignup(),
            "must be an object",
        )
    }
}
