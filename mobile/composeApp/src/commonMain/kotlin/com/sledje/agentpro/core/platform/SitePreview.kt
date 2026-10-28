package com.sledje.agentpro.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/**
 * Renders a site with the web renderer (docs/WEB_RENDERER.md, Templates: the in-app preview).
 * The live renderer at `SITE_BASE_URL/?preview=1` comes first, so template changes reach the preview with
 * each web deploy, without a new app build; the copy bundled in the app is the fallback when that page
 * can't be reached (DECISIONS #119). Either page then gets `window.renderSite(json)`.
 * Android: a WebView (the bundled copy is the `web/` + `shared/` assets copied by `agentpro.site-preview`);
 * iOS: WKWebView (iOS phase).
 *
 * [siteJson] is the renderSite input: `{content, template_key, accent, locale, profile}` (built by
 * `SitePreviewPayload` in feature/website). Each new value re-renders the page without reloading it.
 */
interface SitePreview {
    @Composable
    fun Render(siteJson: String, modifier: Modifier)
}

/** The JavaScript a platform preview evaluates; pure, so the exact call is unit-tested. */
object SitePreviewScript {
    /** The bundled page the preview loads: no resolution, it waits for `renderSite` (WEB_RENDERER.md, Resolution 0). */
    const val PAGE_PATH = "/index.html?preview=1"

    /** The live page on the Worker: `/` with `?preview=1` is the same static shell, never SSR'd. */
    const val REMOTE_PATH = "/?preview=1"

    /** How long the live page has to load and define `renderSite` before the bundled copy takes over. */
    const val REMOTE_TIMEOUT_MS = 10_000L

    /** After the live page failed, previews go straight to the bundled copy for this long. */
    const val REMOTE_RETRY_AFTER_MS = 60_000L

    /** Evaluates to `true` once the loaded page is a renderer (an error or other page isn't). */
    const val READY_CHECK = "typeof window.renderSite==='function'"

    /** The live preview page for [base] (`SITE_BASE_URL`), or null when no https base is configured. */
    fun remoteUrl(base: String): String? {
        val clean = base.trim().trimEnd('/')
        return if (clean.startsWith("https://") && clean.length > "https://".length) clean + REMOTE_PATH else null
    }

    /**
     * Calls `window.renderSite(<json as a string literal>)` once the renderer has defined it. The JSON is
     * passed as a JS string (renderSite parses strings), escaped by kotlinx-serialization; U+2028/U+2029
     * are escaped too because older JS engines end string literals at them.
     */
    fun renderCall(siteJson: String): String {
        val literal = Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(siteJson))
            .replace(" ", "\\u2028")
            .replace(" ", "\\u2029")
        return "(function r(){if(window.renderSite){window.renderSite($literal);}else{setTimeout(r,50);}})();"
    }
}
