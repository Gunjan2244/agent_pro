package com.sledje.agentpro.core.designsystem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import com.github.takahirom.roborazzi.captureRoboImage
import com.sledje.agentpro.core.designsystem.gallery.GalleryFrame
import com.sledje.agentpro.core.designsystem.gallery.GallerySection
import com.sledje.agentpro.core.designsystem.gallery.GallerySectionContent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi baselines of the component gallery (docs/TESTING.md): every section in light, dark,
 * Hindi and at 200% font scale. Images: composeApp/src/androidHostTest/screenshots/<variant>/<section>.png.
 * Record: ./gradlew :composeApp:recordRoborazziAndroidHostTest ; verify: :composeApp:verifyRoborazziAndroidHostTest.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h3000dp-xhdpi")
class GalleryScreenshotTest {
    private fun capture(variant: String, dark: Boolean = false, hindi: Boolean = false, largeText: Boolean = false) =
        runComposeUiTest {
            var section by mutableStateOf(GallerySection.entries.first())
            setContent {
                GalleryFrame(dark = dark, hindi = hindi, largeText = largeText) {
                    GallerySectionContent(section, Modifier.fillMaxWidth().wrapContentHeight())
                }
            }
            GallerySection.entries.forEach { s ->
                section = s
                waitForIdle()
                onNodeWithTag("gallery_${s.name}").captureRoboImage("src/androidHostTest/screenshots/$variant/${s.name}.png")
            }
        }

    @Test
    fun lightEnglish() = capture("light_en")

    @Test
    fun darkEnglish() = capture("dark_en", dark = true)

    @Test
    fun lightHindi() = capture("light_hi", hindi = true)

    @Test
    fun darkHindi() = capture("dark_hi", dark = true, hindi = true)

    @Test
    fun largeTextEnglish() = capture("light_en_200", largeText = true)

    @Test
    fun largeTextHindi() = capture("light_hi_200", hindi = true, largeText = true)
}
