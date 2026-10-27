package com.sledje.agentpro.app

import android.content.Context
import androidx.room.Room
import com.sledje.agentpro.core.data.AppDatabase
import com.sledje.agentpro.core.platform.AndroidImageProcessor
import com.sledje.agentpro.core.platform.AndroidMediaPicker
import com.sledje.agentpro.core.platform.AndroidContactPicker
import com.sledje.agentpro.core.platform.AndroidGoogleSignIn
import com.sledje.agentpro.core.platform.AndroidNetworkMonitor
import com.sledje.agentpro.core.platform.AndroidSitePreview
import com.sledje.agentpro.core.platform.AndroidUrlLauncher
import com.sledje.agentpro.core.platform.AppCompatLocaleController
import com.sledje.agentpro.core.platform.KeystoreSecureSettings

/** Builds the [AppContainer] with the Android seam implementations (called once by AgentProApp). */
fun androidAppContainer(context: Context): AppContainer {
    val app = context.applicationContext
    return AppContainer(
        PlatformServices(
            databaseBuilder = Room.databaseBuilder<AppDatabase>(app, app.getDatabasePath(AppDatabase.FILE_NAME).absolutePath),
            dataStorePath = app.filesDir.resolve("agentpro.preferences_pb").absolutePath,
            network = AndroidNetworkMonitor(app),
            urlLauncher = AndroidUrlLauncher(app),
            mediaPicker = AndroidMediaPicker(),
            imageProcessor = AndroidImageProcessor(),
            sitePreview = AndroidSitePreview(),
            secureSettings = KeystoreSecureSettings(app),
            locale = AppCompatLocaleController(),
            contactPicker = AndroidContactPicker(),
            googleSignIn = AndroidGoogleSignIn(AppConfig.GOOGLE_WEB_CLIENT_ID),
        ),
    )
}
