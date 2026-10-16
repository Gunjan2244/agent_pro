package com.sledje.agentpro.feature.website

import com.sledje.agentpro.core.platform.MediaPicker
import com.sledje.agentpro.core.platform.SitePreview
import com.sledje.agentpro.feature.website.data.ImageUploader
import com.sledje.agentpro.feature.website.data.SiteStatsRepository

/**
 * What the Website hub (and onboarding's photo) needs beyond [com.sledje.agentpro.app.AppDependencies.sites]:
 * the MediaPicker and SitePreview seams, the image pipeline (ImageProcessor + Storage upload) and the
 * site stats. One group, so the hub adds a single line to AppDependencies.
 */
class WebsiteServices(
    val mediaPicker: MediaPicker,
    val imageUploader: ImageUploader,
    val sitePreview: SitePreview,
    val stats: SiteStatsRepository,
)
