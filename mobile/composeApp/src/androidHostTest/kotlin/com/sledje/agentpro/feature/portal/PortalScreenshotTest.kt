package com.sledje.agentpro.feature.portal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import com.github.takahirom.roborazzi.captureRoboImage
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.gallery.GalleryFrame
import com.sledje.agentpro.core.ui.LocalIsOnline
import com.sledje.agentpro.feature.portal.data.PortalState
import com.sledje.agentpro.feature.portal.data.PortalStatus
import com.sledje.agentpro.feature.portal.ui.PortalNotice
import com.sledje.agentpro.feature.portal.ui.WebsiteLoginCardContent
import com.sledje.agentpro.feature.portal.ui.WebsiteLoginUiState
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi baselines of the Customer detail "Website login" card (customer portal; docs/TESTING.md),
 * every state in light/dark × EN/HI, with a fixed clock so relative times never drift.
 * Images: composeApp/src/androidHostTest/screenshots/<variant>/portal/<State>.png.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class PortalScreenshotTest {
    private val now = Instant.parse("2026-10-23T10:00:00Z")
    private val name = "Ramesh Kumar"

    private fun ready(status: PortalStatus, online: Boolean = true, busy: Boolean = false, notice: PortalNotice? = null) =
        WebsiteLoginUiState.Ready(name, status, online = online, busy = busy, notice = notice, now = now)

    private val active = PortalStatus(PortalState.Active, lastLoginAt = now - 2.days, sharedWith = listOf("Sunita Verma"))

    private fun states(): List<Pair<String, WebsiteLoginUiState>> = listOf(
        "PortalNone" to ready(PortalStatus.None),
        "PortalInvited" to ready(PortalStatus(PortalState.Invited, inviteExpiresAt = Instant.parse("2026-10-30T10:00:00Z"))),
        "PortalActive" to ready(active),
        "PortalActiveNeverSignedIn" to ready(PortalStatus(PortalState.Active)),
        "PortalLoginOff" to ready(PortalStatus.None.copy(portalAvailable = false)),
        "PortalLocked" to ready(PortalStatus(PortalState.Locked, lockedUntil = now + 1.days)),
        "PortalOffline" to ready(active, online = false),
        "PortalBusy" to ready(active, busy = true),
        "PortalErrorNotice" to ready(PortalStatus.None, notice = PortalNotice.Failed(AppError.Unknown())),
        "PortalNeedsPhone" to WebsiteLoginUiState.NeedsPhone(name),
        "PortalNeedsSite" to WebsiteLoginUiState.NeedsSite(name),
        "PortalLoading" to WebsiteLoginUiState.Loading,
        "PortalError" to WebsiteLoginUiState.Error(AppError.Offline),
    )

    @Composable
    private fun Card(state: WebsiteLoginUiState) {
        Column(Modifier.fillMaxWidth().padding(Spacing.m)) {
            WebsiteLoginCardContent(state, onSend = {}, onTurnOff = {}, onEditCustomer = {}, onRetry = {})
        }
    }

    private fun capture(variant: String, dark: Boolean, hindi: Boolean) = runComposeUiTest {
        val states = states()
        var index by mutableStateOf(0)
        setContent {
            CompositionLocalProvider(LocalIsOnline provides true) {
                GalleryFrame(dark = dark, hindi = hindi, largeText = false) {
                    key(index) { Card(states[index].second) }
                }
            }
        }
        states.forEachIndexed { i, (stateName, _) ->
            index = i
            waitForIdle()
            onRoot().captureRoboImage("src/androidHostTest/screenshots/$variant/portal/$stateName.png")
        }
    }

    @Test fun lightEnglish() = capture("light_en", dark = false, hindi = false)

    @Test fun darkEnglish() = capture("dark_en", dark = true, hindi = false)

    @Test fun lightHindi() = capture("light_hi", dark = false, hindi = true)

    @Test fun darkHindi() = capture("dark_hi", dark = true, hindi = true)
}
