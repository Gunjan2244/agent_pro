package com.sledje.agentpro.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.sledje.agentpro.app.App
import com.sledje.agentpro.core.platform.AppCompatLocaleController

/**
 * The single activity. `agentpro://` links (docs/UX.md, Deep links) are passed to DeepLinks.
 * An AppCompatActivity so AppCompat's per-app language applies to it (DECISIONS #45).
 */
class MainActivity : AppCompatActivity() {
    private val container get() = (application as AgentProApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // AppCompat has loaded the stored app language by now (Android 12 and lower).
        (container.locale as? AppCompatLocaleController)?.sync()
        if (savedInstanceState == null) container.deepLinks.handle(intent?.dataString)
        setContent { App(container) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        container.deepLinks.handle(intent.dataString)
    }
}
