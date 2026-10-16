package com.sledje.agentpro.core.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

/** [UrlLauncher] on Android: Intents started from the application context. */
class AndroidUrlLauncher(private val context: Context) : UrlLauncher {
    override fun openEmailApp(): Boolean {
        val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    override fun share(text: String): Boolean {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        return start(Intent.createChooser(send, null))
    }

    override fun openUrl(url: String): Boolean {
        if (!url.startsWith("https://") && !url.startsWith("http://")) return false
        return start(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
    }

    // ACTION_DIAL opens the dialer without placing the call, so no CALL_PHONE permission is needed.
    override fun call(phone: String): Boolean = start(Intent(Intent.ACTION_DIAL, android.net.Uri.fromParts("tel", phone, null)))

    override fun sms(phone: String): Boolean = start(Intent(Intent.ACTION_SENDTO, android.net.Uri.fromParts("smsto", phone, null)))

    override fun email(address: String): Boolean = start(Intent(Intent.ACTION_SENDTO, android.net.Uri.fromParts("mailto", address, null)))

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
