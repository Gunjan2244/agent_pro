package com.sledje.agentpro.feature.website.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.sledje.agentpro.core.catalog.Tone
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.message
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.AppShapes
import com.sledje.agentpro.core.designsystem.Dimens
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.Tokens
import com.sledje.agentpro.core.designsystem.components.AppBottomSheet
import com.sledje.agentpro.core.designsystem.components.PrimaryButton
import com.sledje.agentpro.core.designsystem.components.SecondaryButton
import com.sledje.agentpro.core.designsystem.components.StatusPill
import com.sledje.agentpro.core.ui.LocalIsOnline
import com.sledje.agentpro.feature.website.data.ContentTooLarge
import com.sledje.agentpro.feature.website.data.ImageUnreadable
import com.sledje.agentpro.feature.website.data.Site
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.ic_check_circle
import com.sledje.agentpro.resources.ic_hourglass_empty
import com.sledje.agentpro.resources.ic_sync
import com.sledje.agentpro.resources.website_content_too_large
import com.sledje.agentpro.resources.website_copy_link
import com.sledje.agentpro.resources.website_photo_unreadable
import com.sledje.agentpro.resources.website_qr_description
import com.sledje.agentpro.resources.website_qr_hint
import com.sledje.agentpro.resources.website_share
import com.sledje.agentpro.resources.website_share_title
import com.sledje.agentpro.resources.website_status_draft
import com.sledje.agentpro.resources.website_status_live
import com.sledje.agentpro.resources.website_status_unpublished
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The site's state on the hub's status card (docs/UX.md: Live / Draft / Unpublished changes). */
enum class SiteStatus { Live, Draft, Unpublished }

val Site.status: SiteStatus
    get() = when {
        !isPublished -> SiteStatus.Draft
        hasUnpublishedChanges -> SiteStatus.Unpublished
        else -> SiteStatus.Live
    }

/** A tone always comes with an icon and a label (docs/DESIGN_SYSTEM.md, Semantic tones). */
@Composable
fun SiteStatusPill(status: SiteStatus, modifier: Modifier = Modifier) {
    when (status) {
        SiteStatus.Live -> StatusPill(Tone.Success, Res.drawable.ic_check_circle, stringResource(Res.string.website_status_live), modifier)
        SiteStatus.Draft -> StatusPill(Tone.Neutral, Res.drawable.ic_hourglass_empty, stringResource(Res.string.website_status_draft), modifier)
        SiteStatus.Unpublished -> StatusPill(Tone.Warning, Res.drawable.ic_sync, stringResource(Res.string.website_status_unpublished), modifier)
    }
}

/** The one-line message for a website error (specific copy for the cases the hub expects). */
@Composable
fun websiteErrorText(error: AppError): String = stringResource(websiteErrorTextRes(error))

fun websiteErrorTextRes(error: AppError): StringResource = when (error) {
    ContentTooLarge -> Res.string.website_content_too_large
    ImageUnreadable -> Res.string.website_photo_unreadable
    else -> error.message
}

@Composable
fun ErrorText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = AgentProTheme.text.body,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * The site's QR code (qrose). Always dark modules on a white quiet zone, also in dark mode, so every
 * scanner reads it.
 */
@Composable
fun SiteQrCode(url: String, modifier: Modifier = Modifier) {
    val painter = rememberQrCodePainter(url)
    Box(
        modifier.background(QR_BACKGROUND, AppShapes.md).padding(Spacing.m),
        contentAlignment = Alignment.Center,
    ) {
        Image(painter, contentDescription = stringResource(Res.string.website_qr_description, url), modifier = Modifier.size(Dimens.qrCode))
    }
}

/** QR modules must stay dark on light; these are the light theme's tokens in both themes. */
private val QR_BACKGROUND: Color = Tokens.ColorLight.background

/** The body of the Share / QR sheet: the QR code, the address, then Share (primary) and Copy link. */
@Composable
fun ShareContent(url: String, display: String, onShare: () -> Unit, onCopy: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        SiteQrCode(url)
        Text(display, style = AgentProTheme.text.strong, textAlign = TextAlign.Center)
        Text(stringResource(Res.string.website_qr_hint), style = AgentProTheme.text.bodyMuted, textAlign = TextAlign.Center)
        PrimaryButton(stringResource(Res.string.website_share), onShare, Modifier.fillMaxWidth())
        SecondaryButton(stringResource(Res.string.website_copy_link), onCopy, Modifier.fillMaxWidth())
    }
}

/** Share / QR (sheet) from the hub and the Link screen (docs/UX.md, navigation map). */
@Composable
fun ShareSheet(url: String, display: String, onShare: () -> Unit, onCopy: () -> Unit, onDismiss: () -> Unit) {
    AppBottomSheet(title = stringResource(Res.string.website_share_title), onDismiss = onDismiss) {
        ShareContent(url, display, onShare, onCopy)
    }
}

/** Save / Publish need a connection (docs/UX.md, Global rule 7). */
@Composable
fun isOnline(): Boolean = LocalIsOnline.current
