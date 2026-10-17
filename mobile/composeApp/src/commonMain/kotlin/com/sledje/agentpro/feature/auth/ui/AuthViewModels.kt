package com.sledje.agentpro.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sledje.agentpro.core.catalog.AppLocale
import com.sledje.agentpro.core.catalog.PrivacyNotice
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.forms.FieldLink
import com.sledje.agentpro.core.forms.FieldSpec
import com.sledje.agentpro.core.forms.FieldType
import com.sledje.agentpro.core.forms.FormState
import com.sledje.agentpro.feature.auth.data.AuthRepository
import com.sledje.agentpro.feature.auth.data.AuthRules
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.auth_email
import com.sledje.agentpro.resources.auth_new_password
import com.sledje.agentpro.resources.auth_password
import com.sledje.agentpro.resources.cat_privacy_agent_signup_consent
import com.sledje.agentpro.resources.cat_privacy_agent_signup_link_notice
import com.sledje.agentpro.resources.cat_privacy_agent_signup_link_terms
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The three email forms (docs/UX.md, Sign in / Create account / Forgot password). */
enum class AuthFormMode { SignIn, SignUp, ResetPassword }

/** A human, inline error under the form ("Wrong email or password"). */
sealed interface AuthFormError {
    data object Credentials : AuthFormError
    data object NotConfirmed : AuthFormError
    data object WeakPassword : AuthFormError
    data object SamePassword : AuthFormError
    data object AccountExists : AuthFormError
    data class Other(val error: AppError) : AuthFormError
}

fun AppError.toAuthFormError(): AuthFormError = when {
    this is AppError.Validation && field == "credentials" -> AuthFormError.Credentials
    this is AppError.Validation && field == "email" && rule == "not_confirmed" -> AuthFormError.NotConfirmed
    this is AppError.Validation && field == "password" && rule == "same" -> AuthFormError.SamePassword
    this is AppError.Validation && field == "password" -> AuthFormError.WeakPassword
    this == AppError.Conflict -> AuthFormError.AccountExists
    else -> AuthFormError.Other(this)
}

data class AuthFormUiState(
    val submitting: Boolean = false,
    val error: AuthFormError? = null,
    /** Set when sign-up or the reset email succeeded: the screen moves on to "Check your email" / "Reset sent". */
    val sentTo: String? = null,
)

/**
 * Sign in, create account and forgot password: email (+ password) built by FormRenderer. Sign-in success
 * needs no navigation: the auth state flips and the gate moves on.
 */
class AuthFormViewModel(val mode: AuthFormMode, private val auth: AuthRepository) : ViewModel() {
    val form = FormState(fieldsFor(mode))

    private val _state = MutableStateFlow(AuthFormUiState())
    val state: StateFlow<AuthFormUiState> = _state.asStateFlow()

    /**
     * [locale] is the language the form and its consent line are shown in: sign-up sends the hash of
     * the notice text in that language.
     */
    fun submit(locale: AppLocale) {
        if (_state.value.submitting || !form.validateAll()) return
        val values = form.values()
        val email = values[EMAIL] as? String ?: return
        val password = values[PASSWORD] as? String
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val result = when (mode) {
                AuthFormMode.SignIn -> auth.signIn(email, password.orEmpty())
                AuthFormMode.SignUp -> auth.signUp(email, password.orEmpty(), PrivacyNotice.AgentSignup.consent(locale))
                AuthFormMode.ResetPassword -> auth.sendPasswordReset(email)
            }
            _state.update {
                when (result) {
                    is Result.Ok -> it.copy(submitting = false, sentTo = if (mode == AuthFormMode.SignIn) null else email)
                    is Result.Err -> it.copy(submitting = false, error = result.error.toAuthFormError())
                }
            }
        }
    }

    /** The screen navigated away after [AuthFormUiState.sentTo]. */
    fun onNavigated() = _state.update { it.copy(sentTo = null) }

    companion object {
        const val EMAIL = "email"
        const val PASSWORD = "password"

        /** The sign-up consent checkbox (`agent_signup` notice; unticked, required). */
        const val CONSENT = "consent"

        /** Link keys of the consent line. */
        const val LINK_TERMS = "terms"
        const val LINK_NOTICE = "notice"

        /** Email ≤ 120 like `submit_enquiry`; the password minimum (8) only applies to new passwords. */
        fun fieldsFor(mode: AuthFormMode): List<FieldSpec> = buildList {
            add(FieldSpec(EMAIL, FieldType.Email, Res.string.auth_email, required = true, maxLength = 120))
            if (mode != AuthFormMode.ResetPassword) {
                add(
                    FieldSpec(
                        PASSWORD, FieldType.Password, Res.string.auth_password, required = true,
                        minLength = if (mode == AuthFormMode.SignUp) AuthRules.PASSWORD_MIN_LENGTH else null,
                    ),
                )
            }
            if (mode == AuthFormMode.SignUp) {
                add(
                    FieldSpec(
                        CONSENT, FieldType.Consent, Res.string.cat_privacy_agent_signup_consent, required = true,
                        links = listOf(
                            FieldLink(LINK_TERMS, Res.string.cat_privacy_agent_signup_link_terms),
                            FieldLink(LINK_NOTICE, Res.string.cat_privacy_agent_signup_link_notice),
                        ),
                    ),
                )
            }
        }
    }
}

data class CheckEmailUiState(val sending: Boolean = false, val resent: Boolean = false, val error: AppError? = null)

/** "Check your email": Resend the confirmation. */
class CheckEmailViewModel(private val email: String, private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(CheckEmailUiState())
    val state: StateFlow<CheckEmailUiState> = _state.asStateFlow()

    fun resend() {
        if (_state.value.sending) return
        _state.update { it.copy(sending = true, resent = false, error = null) }
        viewModelScope.launch {
            val result = auth.resendConfirmation(email)
            _state.update {
                when (result) {
                    is Result.Ok -> it.copy(sending = false, resent = true)
                    is Result.Err -> it.copy(sending = false, error = result.error)
                }
            }
        }
    }

    fun onResentShown() = _state.update { it.copy(resent = false) }

    fun onErrorShown() = _state.update { it.copy(error = null) }
}

/** Where a password-reset link leads (docs/UX.md, Set new password). */
enum class RecoveryStep {
    /** The session in the link is being imported: Splash. */
    Verifying,
    SetPassword,

    /** "This link has expired" → Send a new link. */
    Expired,
}

data class SetNewPasswordUiState(
    val submitting: Boolean = false,
    val error: AuthFormError? = null,
    /** Saved: go to Home with "Password updated". */
    val saved: Boolean = false,
    /** The recovery session ran out before saving: show "This link has expired". */
    val expired: Boolean = false,
)

/**
 * Set new password (docs/UX.md): one password field (at least 8 characters, built by FormRenderer)
 * and **Save password** → `updateUser(password)` for the recovery session.
 */
class SetNewPasswordViewModel(private val auth: AuthRepository) : ViewModel() {
    val form = FormState(FIELDS)

    private val _state = MutableStateFlow(SetNewPasswordUiState())
    val state: StateFlow<SetNewPasswordUiState> = _state.asStateFlow()

    fun submit() {
        if (_state.value.submitting || _state.value.saved || !form.validateAll()) return
        val password = form.values()[PASSWORD] as? String ?: return
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val result = auth.updatePassword(password)
            _state.update {
                when (result) {
                    is Result.Ok -> it.copy(submitting = false, saved = true)
                    is Result.Err ->
                        if (result.error == AppError.Unauthorized) it.copy(submitting = false, expired = true)
                        else it.copy(submitting = false, error = result.error.toAuthFormError())
                }
            }
        }
    }

    companion object {
        const val PASSWORD = "password"

        /** The same rule as sign-up (docs/BACKEND.md, Auth: at least 8 characters). */
        val FIELDS: List<FieldSpec> = listOf(
            FieldSpec(PASSWORD, FieldType.Password, Res.string.auth_new_password, required = true, minLength = AuthRules.PASSWORD_MIN_LENGTH),
        )
    }
}
