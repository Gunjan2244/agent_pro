package com.sledje.agentpro.core.platform

import android.annotation.SuppressLint
import android.content.res.AssetManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * [SitePreview] on Android: a WebView on the renderer files that `agentpro.site-preview` bundles as
 * assets (`site/web/…`, `site/shared/…`, plus a generated `site/web/tokens.css`).
 *
 * The renderer uses root-relative URLs (`/app.js`, `/shared/catalog.json`) and ES modules, which don't
 * work from `file://`. So the page is served from the reserved host `appassets.androidplatform.net`
 * by [AssetSiteClient], which answers every request from the assets and never touches the network
 * except for Google Fonts (the CSS link in index.html). The preview never submits enquiries or records
 * views (WEB_RENDERER.md, Resolution 0), and taps on links don't navigate away.
 */
class AndroidSitePreview : SitePreview {
    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    override fun Render(siteJson: String, modifier: Modifier) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    val client = AssetSiteClient(context.assets)
                    webViewClient = client
                    tag = client
                    loadUrl(ORIGIN + SitePreviewScript.PAGE_PATH)
                }
            },
            update = { view -> (view.tag as AssetSiteClient).render(view, siteJson) },
            onRelease = { it.destroy() },
        )
    }

    private companion object {
        const val ORIGIN = "https://${AssetSiteClient.HOST}"
    }
}

/** Serves the bundled renderer and keeps the latest site JSON until the page has loaded. */
internal class AssetSiteClient(private val assets: AssetManager) : WebViewClient() {
    private var loaded = false
    private var pending: String? = null
    private var rendered: String? = null

    fun render(view: WebView, json: String) {
        pending = json
        if (loaded) flush(view)
    }

    private fun flush(view: WebView) {
        val json = pending ?: return
        if (json == rendered) return
        rendered = json
        view.evaluateJavascript(SitePreviewScript.renderCall(json), null)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        loaded = true
        flush(view)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

    /**
     * The WebView's renderer process died (typically out of memory with two previews alive in the template
     * pager). Without this override Android kills the whole app; returning true keeps it running and the
     * preview simply stays blank.
     */
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        loaded = false
        return true
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url
        if (url.host != HOST) return null // Google Fonts
        val path = assetPath(url.path.orEmpty()) ?: return notFound()
        return try {
            WebResourceResponse(mimeType(path), "utf-8", assets.open(path))
        } catch (e: IOException) {
            notFound()
        }
    }

    private fun notFound() = WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))

    companion object {
        /** Android's reserved host for app assets (it never resolves on the network). */
        const val HOST = "appassets.androidplatform.net"

        /** `/shared/x` → `site/shared/x`; `/` → `site/web/index.html`; `/x` → `site/web/x`. Null for `..`. */
        fun assetPath(path: String): String? {
            val clean = path.trimStart('/')
            if (clean.split('/').any { it == ".." }) return null
            return when {
                clean.isEmpty() -> "site/web/index.html"
                clean.startsWith("shared/") -> "site/$clean"
                else -> "site/web/$clean"
            }
        }

        fun mimeType(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
            "html" -> "text/html"
            "js", "mjs" -> "text/javascript"
            "css" -> "text/css"
            "json" -> "application/json"
            "webp" -> "image/webp"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "svg" -> "image/svg+xml"
            else -> "application/octet-stream"
        }
    }
}
