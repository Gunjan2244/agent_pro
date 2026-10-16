package com.sledje.agentpro.android

import android.app.Application
import com.sledje.agentpro.app.AppContainer
import com.sledje.agentpro.app.androidAppContainer

/** Builds the manual-DI container once per process (DECISIONS #6) with the Android seam implementations. */
class AgentProApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = androidAppContainer(this)
    }
}
