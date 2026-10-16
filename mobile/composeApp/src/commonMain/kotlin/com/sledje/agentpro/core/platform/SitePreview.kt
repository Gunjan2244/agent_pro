package com.sledje.agentpro.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/**
 * Renders a site with the bundled web renderer (docs/WEB_RENDERER.md, Templates: the in-app preview).
 * Android: a WebView on the `web/` + `shared/` assets copied by `agentpro.site-preview`, opened with
 * `?preview=1`, then `window.renderSite(json)`; iOS: WKWebView (iOS phase).
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
    /** The page the preview loads: no resolution, it waits for `renderSite` (WEB_RENDERER.md, Resolution 0). */
    const val PAGE_PATH = "/index.html?preview=1"

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
