package com.sledje.agentpro.core.platform

import android.annotation.SuppressLint
import android.content.res.AssetManager
import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.math.abs

/**
 * [SitePreview] on Android: a WebView on the live renderer (`SITE_BASE_URL/?preview=1`), falling back to the
 * renderer files that `agentpro.site-preview` bundles as assets (`site/web/…`, `site/shared/…`, plus a generated
 * `site/web/tokens.css`) when the live page can't be reached (DECISIONS #119). The live page means a template
 * change reaches the preview with each web deploy, without rebuilding the app.
 *
 * The renderer uses root-relative URLs (`/app.js`, `/shared/catalog.json`) and ES modules, which don't
 * work from `file://`. So the bundled copy is served from the reserved host `appassets.androidplatform.net`
 * by [PreviewSiteClient], which answers those requests from the assets. The preview never submits enquiries
 * or records views (WEB_RENDERER.md, Resolution 0), and taps on links don't navigate away.
 *
 * Inside the template pager a vertical scroll used to be taken over by the pager as soon as the finger drifted
 * sideways, so the page stopped or jumped to the next template. [VerticalScrollLock] keeps a drag that starts
 * vertical with the WebView until the finger lifts; a sideways drag still swipes the pager.
 */
class AndroidSitePreview(private val siteBaseUrl: String) : SitePreview {
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
                    val client = PreviewSiteClient(context.assets, SitePreviewScript.remoteUrl(siteBaseUrl))
                    webViewClient = client
                    tag = client
                    setOnTouchListener(VerticalScrollLock(ViewConfiguration.get(context).scaledTouchSlop))
                    client.start(this)
                }
            },
            update = { view -> (view.tag as PreviewSiteClient).render(view, siteJson) },
            onRelease = { view ->
                (view.tag as PreviewSiteClient).release(view)
                view.destroy()
            },
        )
    }
}

/**
 * Once a drag has moved [touchSlop] mostly vertically, asks the parents not to intercept it. Compose's AndroidView
 * passes that on to its pointer interop, so an enclosing pager or scrollable no longer sees the rest of the gesture.
 */
@SuppressLint("ClickableViewAccessibility") // Observes only; the WebView still handles every event (returns false).
internal class VerticalScrollLock(private val touchSlop: Int) : View.OnTouchListener {
    private var downX = 0f
    private var downY = 0f
    private var decided = false

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                decided = false
            }
            MotionEvent.ACTION_MOVE -> if (!decided) {
                val dx = abs(event.x - downX)
                val dy = abs(event.y - downY)
                if (dx > touchSlop || dy > touchSlop) {
                    decided = true
                    if (dy > dx) view.parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
        }
        return false
    }
}

/**
 * Loads the live renderer ([remoteUrl]) or, when it is null, failed recently, errors, or hasn't defined
 * `renderSite` within [SitePreviewScript.REMOTE_TIMEOUT_MS], the bundled one; serves the bundled files and
 * keeps the latest site JSON until a page is ready.
 */
internal class PreviewSiteClient(private val assets: AssetManager, private val remoteUrl: String?) : WebViewClient() {
    private var remote = false
    private var loaded = false
    private var released = false
    private var pending: String? = null
    private var rendered: String? = null
    private var view: WebView? = null
    private val timeout = Runnable { view?.let { fallBack(it) } }
    private val remoteHost = remoteUrl?.let { Uri.parse(it).host }

    fun start(view: WebView) {
        this.view = view
        val failedAt = lastRemoteFailure
        val recentlyFailed = failedAt != null && SystemClock.elapsedRealtime() - failedAt < SitePreviewScript.REMOTE_RETRY_AFTER_MS
        if (remoteUrl != null && !recentlyFailed) {
            remote = true
            view.postDelayed(timeout, SitePreviewScript.REMOTE_TIMEOUT_MS)
            view.loadUrl(remoteUrl)
        } else {
            loadBundled(view)
        }
    }

    fun render(view: WebView, json: String) {
        pending = json
        if (loaded) flush(view)
    }

    fun release(view: WebView) {
        released = true
        view.removeCallbacks(timeout)
        this.view = null
    }

    private fun flush(view: WebView) {
        val json = pending ?: return
        if (json == rendered) return
        rendered = json
        view.evaluateJavascript(SitePreviewScript.renderCall(json), null)
    }

    private fun loadBundled(view: WebView) {
        remote = false
        loaded = false
        rendered = null
        view.loadUrl(ORIGIN + SitePreviewScript.PAGE_PATH)
    }

    /** The live page didn't work: note it for the next previews and switch this one to the bundled copy. */
    private fun fallBack(view: WebView) {
        if (!remote || released) return
        lastRemoteFailure = SystemClock.elapsedRealtime()
        view.removeCallbacks(timeout)
        view.stopLoading()
        loadBundled(view)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        if (released) return
        val host = url?.let { Uri.parse(it).host }
        if (!remote) {
            if (host != HOST) return // the abandoned live page finishing after the switch
            loaded = true
            flush(view)
            return
        }
        if (host != remoteHost) return
        view.evaluateJavascript(SitePreviewScript.READY_CHECK) { result ->
            if (released || !remote) return@evaluateJavascript
            if (result == "true") {
                view.removeCallbacks(timeout)
                loaded = true
                flush(view)
            } else {
                fallBack(view)
            }
        }
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) fallBack(view)
    }

    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
        if (request.isForMainFrame) fallBack(view)
    }

    /** Links never navigate; only a server redirect of the live page itself is followed. */
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        !(remote && request.isForMainFrame && request.isRedirect && request.url.host == remoteHost)

    /**
     * The WebView's renderer process died (typically out of memory with two previews alive in the template
     * pager). Without this override Android kills the whole app; returning true keeps it running and the
     * preview simply stays blank.
     */
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        loaded = false
        view.removeCallbacks(timeout)
        return true
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url
        if (url.host != HOST) return null // the live renderer, Google Fonts
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
        private const val ORIGIN = "https://$HOST"

        /** When a live page last failed in this process (elapsedRealtime), so the next previews skip the wait. */
        @Volatile private var lastRemoteFailure: Long? = null

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
