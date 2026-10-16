package com.sledje.agentpro.buildlogic

import com.sledje.agentpro.buildlogic.codegen.SharedDefinitions
import com.sledje.agentpro.buildlogic.codegen.TokensCssGenerator
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** tokens.css for the in-app site preview (copySitePreviewAssets) matches scripts/gen-tokens.mjs. */
class TokensCssGeneratorTest {
    @Test
    fun fixtureTokensBecomeCssVariables() {
        val css = TokensCssGenerator.generate(Fixture().defs().designTokens)
        assertTrue(css.startsWith(":root{\n"), css)
        assertTrue("--c-primary:#1F4AA8;" in css, css)
        assertTrue("--accent:#1F4AA8;" in css, css)
        assertTrue("--text-s:14px;--line-s:20px;" in css, css)
        assertTrue("--text-xl:65px;--line-xl:72px;" in css, css)
        assertTrue("--space-m:16px;" in css, css)
        assertTrue("--shape-md:12px;" in css, css)
        assertTrue("--motion-short:150ms;" in css, css)
        assertTrue(css.trimEnd().endsWith(":lang(hi){--line-s:22px;--line-m:32px;--line-l:48px;--line-xl:74px;}"), css)
    }

    /** Byte-for-byte equal to the web build's generator on the real shared/ (skipped without node or the repo). */
    @Test
    fun sameOutputAsTheWebScript() {
        val shared = realSharedDir() ?: return
        val script = File(shared.parentFile, "scripts/gen-tokens.mjs").takeIf { it.isFile } ?: return
        val out = Files.createTempFile("tokens", ".css").toFile()
        val process = try {
            ProcessBuilder("node", script.absolutePath, out.absolutePath).redirectErrorStream(true).start()
        } catch (e: Exception) {
            return // node isn't installed
        }
        if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) return
        assertEquals(out.readText(), TokensCssGenerator.generate(SharedDefinitions.load(shared).designTokens))
    }
}
