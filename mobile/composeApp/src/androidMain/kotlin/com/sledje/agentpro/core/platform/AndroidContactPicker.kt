package com.sledje.agentpro.core.platform

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * [ContactPicker] on Android: `ACTION_PICK` on `CommonDataKinds.Phone.CONTENT_URI`, so the agent picks
 * one phone number of one contact. The returned URI carries a temporary read grant for that row only,
 * so no `READ_CONTACTS` permission is declared or requested.
 */
class AndroidContactPicker : ContactPicker {
    @Composable
    override fun rememberContactPicker(onResult: (PickedContact?) -> Unit): () -> Unit {
        val context = LocalContext.current.applicationContext
        val callback by rememberUpdatedState(onResult)
        val launcher = rememberLauncherForActivityResult(PickPhoneContact) { uri ->
            callback(uri?.let { readContact(context, it) })
        }
        return remember(launcher) { { runCatching { launcher.launch(Unit) }.onFailure { callback(null) } } }
    }

    private object PickPhoneContact : ActivityResultContract<Unit, Uri?>() {
        override fun createIntent(context: Context, input: Unit): Intent =
            Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
            if (resultCode == Activity.RESULT_OK) intent?.data else null
    }

    private fun readContact(context: Context, uri: Uri): PickedContact? = runCatching {
        val columns = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        context.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            PickedContact(name = cursor.getString(0)?.trim(), phone = cursor.getString(1)?.trim())
        }
    }.getOrNull()
}
