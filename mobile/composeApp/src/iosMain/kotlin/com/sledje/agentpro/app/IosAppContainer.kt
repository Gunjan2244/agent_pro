package com.sledje.agentpro.app

import androidx.room.Room
import com.sledje.agentpro.core.data.AppDatabase
import com.sledje.agentpro.core.platform.IosImageProcessor
import com.sledje.agentpro.core.platform.IosMediaPicker
import com.sledje.agentpro.core.platform.IosContactPicker
import com.sledje.agentpro.core.platform.IosNetworkMonitor
import com.sledje.agentpro.core.platform.IosSitePreview
import com.sledje.agentpro.core.platform.IosSecureSettings
import com.sledje.agentpro.core.platform.IosUrlLauncher
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/** iOS phase: builds the [AppContainer] with the iOS seams (stubs until then; not built on Linux). */
@OptIn(ExperimentalForeignApi::class)
fun iosAppContainer(): AppContainer {
    val documents = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )?.path ?: error("No documents directory")
    return AppContainer(
        PlatformServices(
            databaseBuilder = Room.databaseBuilder<AppDatabase>(name = "$documents/${AppDatabase.FILE_NAME}"),
            dataStorePath = "$documents/agentpro.preferences_pb",
            network = IosNetworkMonitor(),
            urlLauncher = IosUrlLauncher(),
            contactPicker = IosContactPicker(),
            mediaPicker = IosMediaPicker(),
            imageProcessor = IosImageProcessor(),
            sitePreview = IosSitePreview(),
            secureSettings = IosSecureSettings(),
        ),
    )
}
