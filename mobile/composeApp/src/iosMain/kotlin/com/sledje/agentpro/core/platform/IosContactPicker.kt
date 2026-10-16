package com.sledje.agentpro.core.platform

import androidx.compose.runtime.Composable

/** iOS phase: CNContactPickerViewController. Until then the seam isn't implemented (docs/ARCHITECTURE.md). */
class IosContactPicker : ContactPicker {
    @Composable
    override fun rememberContactPicker(onResult: (PickedContact?) -> Unit): () -> Unit =
        throw NotImplementedError("iOS phase: CNContactPickerViewController")
}
