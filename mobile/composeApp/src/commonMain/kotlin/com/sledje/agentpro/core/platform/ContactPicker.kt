package com.sledje.agentpro.core.platform

import androidx.compose.runtime.Composable

/** A contact the agent picked: the display name and the chosen phone number (either may be missing). */
data class PickedContact(val name: String?, val phone: String?)

/**
 * The system contact picker (docs/ARCHITECTURE.md, Platform seams; docs/UX.md, Customers list: "Pick
 * from contacts"). Android: `ACTION_PICK` on phone numbers, which needs **no contacts permission**
 * (the picker grants read access to the one row picked); iOS: CNContactPickerViewController (iOS phase).
 *
 * Composable because the picker is bound to the screen's lifecycle (an Activity result launcher on
 * Android). Call it once per screen; the returned function opens the picker.
 */
interface ContactPicker {
    /** [onResult] gets the picked contact, or null when the agent cancelled or nothing could be read. */
    @Composable
    fun rememberContactPicker(onResult: (PickedContact?) -> Unit): () -> Unit
}

/** A picker that never opens (platforms or tests without one). */
object NoContactPicker : ContactPicker {
    @Composable
    override fun rememberContactPicker(onResult: (PickedContact?) -> Unit): () -> Unit = { onResult(null) }
}
