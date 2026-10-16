package com.sledje.agentpro.feature.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.sledje.agentpro.core.catalog.AppLocale
import com.sledje.agentpro.core.catalog.label
import com.sledje.agentpro.core.data.message
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.Dimens
import com.sledje.agentpro.core.designsystem.Spacing
import com.sledje.agentpro.core.designsystem.components.AppScaffold
import com.sledje.agentpro.core.designsystem.components.AppTopBar
import com.sledje.agentpro.core.designsystem.components.BottomActionBar
import com.sledje.agentpro.core.designsystem.components.OfflineBanner
import com.sledje.agentpro.core.designsystem.components.PrimaryButton
import com.sledje.agentpro.core.designsystem.components.TertiaryButton
import com.sledje.agentpro.core.forms.FormRenderer
import com.sledje.agentpro.core.forms.FormState
import com.sledje.agentpro.core.ui.LocalIsOnline
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.app_name
import com.sledje.agentpro.resources.auth_back_to_sign_in
import com.sledje.agentpro.resources.auth_check_email_text
import com.sledje.agentpro.resources.auth_check_email_title
import com.sledje.agentpro.resources.auth_create_account
import com.sledje.agentpro.resources.auth_error_credentials
import com.sledje.agentpro.resources.auth_error_exists
import com.sledje.agentpro.resources.auth_error_not_confirmed
import com.sledje.agentpro.resources.auth_error_same_password
import com.sledje.agentpro.resources.auth_error_weak_password
import com.sledje.agentpro.resources.auth_forgot_password
import com.sledje.agentpro.resources.auth_link_expired_text
import com.sledje.agentpro.resources.auth_link_expired_title
import com.sledje.agentpro.resources.auth_no_email_app
import com.sledje.agentpro.resources.auth_open_email
import com.sledje.agentpro.resources.auth_resend
import com.sledje.agentpro.resources.auth_resent
import com.sledje.agentpro.resources.auth_reset_sent_text
import com.sledje.agentpro.resources.auth_reset_sent_title
import com.sledje.agentpro.resources.auth_reset_title
import com.sledje.agentpro.resources.auth_save_password
import com.sledje.agentpro.resources.auth_send_new_link
import com.sledje.agentpro.resources.auth_send_reset
import com.sledje.agentpro.resources.auth_set_password_title
import com.sledje.agentpro.resources.auth_sign_in
import com.sledje.agentpro.resources.ic_check_circle
import com.sledje.agentpro.resources.ic_language
import com.sledje.agentpro.resources.ic_mail
import com.sledje.agentpro.resources.ic_timer_off
import com.sledje.agentpro.resources.needs_internet
import com.sledje.agentpro.resources.welcome_tagline
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The translated inline message for [error]. */
@Composable
fun authErrorMessage(error: AuthFormError): String = when (error) {
    AuthFormError.Credentials -> stringResource(Res.string.auth_error_credentials)
    AuthFormError.NotConfirmed -> stringResource(Res.string.auth_error_not_confirmed)
    AuthFormError.WeakPassword -> stringResource(Res.string.auth_error_weak_password)
    AuthFormError.SamePassword -> stringResource(Res.string.auth_error_same_password)
    AuthFormError.AccountExists -> stringResource(Res.string.auth_error_exists)
    is AuthFormError.Other -> stringResource(error.error.message)
}

/**
 * Welcome (docs/UX.md): the name, one line, **Create account** (primary) and **Sign in** (text button),
 * and the EN/हिं switch at the top right. Nothing else.
 */
@Composable
fun WelcomeScreen(
    currentLocale: AppLocale,
    onSwitchLocale: (AppLocale) -> Unit,
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
) {
    val online = LocalIsOnline.current
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding(),
    ) {
        if (!online) OfflineBanner()
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.s), horizontalArrangement = Arrangement.End) {
            val other = AppLocale.active.firstOrNull { it != currentLocale } ?: currentLocale
            TertiaryButton(other.label(), onClick = { onSwitchLocale(other) }, icon = Res.drawable.ic_language)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterVertically),
        ) {
            Text(stringResource(Res.string.app_name), style = AgentProTheme.text.display, modifier = Modifier.semantics { heading() })
            Text(stringResource(Res.string.welcome_tagline), style = AgentProTheme.text.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.fillMaxWidth().padding(Spacing.m)) {
            PrimaryButton(stringResource(Res.string.auth_create_account), onCreateAccount, Modifier.fillMaxWidth())
            TertiaryButton(stringResource(Res.string.auth_sign_in), onSignIn, Modifier.fillMaxWidth())
        }
    }
}

/**
 * Sign in, Create account and Forgot password share one layout: the fields (FormRenderer), one inline
 * error line, at most one text-button link, and the primary action in the sticky bottom bar. The button
 * is disabled until the form is valid, while submitting and while offline ("Needs internet").
 */
@Composable
fun AuthFormScreen(
    mode: AuthFormMode,
    form: FormState,
    state: AuthFormUiState,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    onSwitchMode: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    val online = LocalIsOnline.current
    val title = when (mode) {
        AuthFormMode.SignIn -> stringResource(Res.string.auth_sign_in)
        AuthFormMode.SignUp -> stringResource(Res.string.auth_create_account)
        AuthFormMode.ResetPassword -> stringResource(Res.string.auth_reset_title)
    }
    val action = when (mode) {
        AuthFormMode.ResetPassword -> stringResource(Res.string.auth_send_reset)
        else -> title
    }
    AppScaffold(
        topBar = { AppTopBar(title = title, onBack = onBack) },
        offline = !online,
        bottomBar = {
            BottomActionBar(
                text = action,
                onClick = onSubmit,
                enabled = online && form.isValid && !state.submitting,
                helperText = if (!online) stringResource(Res.string.needs_internet) else null,
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            FormRenderer(form, enabled = !state.submitting, onDone = { if (online && form.isValid) onSubmit() })
            state.error?.let {
                Text(
                    authErrorMessage(it),
                    style = AgentProTheme.text.body,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            when (mode) {
                AuthFormMode.SignIn -> {
                    TertiaryButton(stringResource(Res.string.auth_forgot_password), onForgotPassword)
                    TertiaryButton(stringResource(Res.string.auth_create_account), onSwitchMode)
                }
                AuthFormMode.SignUp -> TertiaryButton(stringResource(Res.string.auth_sign_in), onSwitchMode)
                AuthFormMode.ResetPassword -> Unit
            }
        }
    }
}

/** A calm message screen: icon, title, one line, the primary action and an optional text button. */
@Composable
private fun MessageScreen(
    icon: DrawableResource,
    title: String,
    text: String,
    primary: String,
    onPrimary: () -> Unit,
    onBack: () -> Unit,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
    secondaryEnabled: Boolean = true,
    snackbarHostState: SnackbarHostState? = null,
) {
    AppScaffold(
        topBar = { AppTopBar(title = "", onBack = onBack) },
        offline = !LocalIsOnline.current,
        snackbarHostState = snackbarHostState,
        bottomBar = { BottomActionBar(primary, onPrimary, secondaryText = secondary, onSecondary = onSecondary, secondaryEnabled = secondaryEnabled) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(painterResource(icon), null, Modifier.size(Dimens.icon), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = AgentProTheme.text.title, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Text(text, style = AgentProTheme.text.body, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/** "Check your email to confirm", with **Open email app** and Resend (docs/UX.md). */
@Composable
fun CheckEmailScreen(
    email: String,
    state: CheckEmailUiState,
    onOpenEmailApp: () -> Boolean,
    onResend: () -> Unit,
    onResentShown: () -> Unit,
    onErrorShown: () -> Unit,
    onBack: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val resent = stringResource(Res.string.auth_resent)
    val noEmailApp = stringResource(Res.string.auth_no_email_app)
    val error = state.error?.let { stringResource(it.message) }
    LaunchedEffect(state.resent) { if (state.resent) { snackbar.showSnackbar(resent); onResentShown() } }
    LaunchedEffect(error) { if (error != null) { snackbar.showSnackbar(error); onErrorShown() } }
    var showNoApp by remember { mutableStateOf(false) }
    LaunchedEffect(showNoApp) { if (showNoApp) { snackbar.showSnackbar(noEmailApp); showNoApp = false } }
    MessageScreen(
        icon = Res.drawable.ic_mail,
        title = stringResource(Res.string.auth_check_email_title),
        text = stringResource(Res.string.auth_check_email_text, email),
        primary = stringResource(Res.string.auth_open_email),
        onPrimary = { if (!onOpenEmailApp()) showNoApp = true },
        onBack = onBack,
        secondary = stringResource(Res.string.auth_resend),
        onSecondary = onResend,
        secondaryEnabled = LocalIsOnline.current && !state.sending,
        snackbarHostState = snackbar,
    )
}

/** "Reset link sent" (docs/UX.md, Forgot password). */
@Composable
fun ResetSentScreen(email: String, onBackToSignIn: () -> Unit) {
    MessageScreen(
        icon = Res.drawable.ic_check_circle,
        title = stringResource(Res.string.auth_reset_sent_title),
        text = stringResource(Res.string.auth_reset_sent_text, email),
        primary = stringResource(Res.string.auth_back_to_sign_in),
        onPrimary = onBackToSignIn,
        onBack = onBackToSignIn,
    )
}

/**
 * Set new password (docs/UX.md), opened only by a reset link: one password field (FormRenderer, at
 * least 8 characters) and **Save password** in the sticky bottom bar, disabled until valid, while
 * saving and offline ("Needs internet"). Back leaves without saving; the agent stays signed in.
 */
@Composable
fun SetNewPasswordScreen(
    form: FormState,
    state: SetNewPasswordUiState,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val online = LocalIsOnline.current
    AppScaffold(
        topBar = { AppTopBar(title = stringResource(Res.string.auth_set_password_title), onBack = onBack) },
        offline = !online,
        bottomBar = {
            BottomActionBar(
                text = stringResource(Res.string.auth_save_password),
                onClick = onSave,
                enabled = online && form.isValid && !state.submitting && !state.saved,
                helperText = if (!online) stringResource(Res.string.needs_internet) else null,
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            FormRenderer(form, enabled = !state.submitting, onDone = { if (online && form.isValid) onSave() })
            state.error?.let {
                Text(
                    authErrorMessage(it),
                    style = AgentProTheme.text.body,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

/** "This link has expired" with one button, **Send a new link** → Forgot password (docs/UX.md). */
@Composable
fun LinkExpiredScreen(onSendNewLink: () -> Unit, onBack: () -> Unit) {
    MessageScreen(
        icon = Res.drawable.ic_timer_off,
        title = stringResource(Res.string.auth_link_expired_title),
        text = stringResource(Res.string.auth_link_expired_text),
        primary = stringResource(Res.string.auth_send_new_link),
        onPrimary = onSendNewLink,
        onBack = onBack,
    )
}
