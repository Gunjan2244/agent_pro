package com.sledje.agentpro

import androidx.compose.ui.window.ComposeUIViewController
import com.sledje.agentpro.app.App
import com.sledje.agentpro.app.iosAppContainer
import platform.UIKit.UIViewController

private val container by lazy { iosAppContainer() }

/** iOS entry point (iOS phase): the Xcode app shows this controller. */
@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController = ComposeUIViewController { App(container) }
