package com.sledje.agentpro.core.forms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.performScrollTo
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.cat_field_city
import com.sledje.agentpro.resources.cat_field_email
import com.sledje.agentpro.resources.cat_field_name
import com.sledje.agentpro.resources.cat_field_phone
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** FormRenderer validation (docs/TESTING.md, Compose UI tests). */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class FormRendererUiTest {
    private val fields = listOf(
        FieldSpec("name", FieldType.Text, Res.string.cat_field_name, required = true, maxLength = 40),
        FieldSpec("phone", FieldType.Phone, Res.string.cat_field_phone, required = true),
        FieldSpec("email", FieldType.Email, Res.string.cat_field_email),
    )

    @Test
    fun errorsAppearOnSubmitAndClearWhenFixed() = runComposeUiTest {
        val state = FormState(fields)
        var valid: Boolean? = null
        setContent {
            AgentProTheme(darkTheme = false) {
                Column {
                    FormRenderer(state)
                    Button(onClick = { valid = state.validateAll() }) { Text("save") }
                }
            }
        }
        // No error before the agent has left a field or saved.
        onAllNodesWithText("Required").assertCountEquals(0)

        onNodeWithText("save").performClick()
        assertEquals(false, valid)
        onAllNodesWithText("Required").assertCountEquals(2)

        onNodeWithTag(fieldTestTag("name")).performTextInput("Ramesh Kumar")
        onNodeWithTag(fieldTestTag("phone")).performTextInput("98765 43210")
        onNodeWithTag(fieldTestTag("email")).performTextInput("ramesh@")
        onNodeWithText("save").performClick()
        assertEquals(false, valid)
        onAllNodesWithText("Required").assertCountEquals(0)
        onNodeWithText("Enter a valid email").assertExists()

        onNodeWithTag(fieldTestTag("email")).performTextInput("example.com")
        onNodeWithText("save").performClick()
        assertEquals(true, valid)
        assertEquals("+919876543210", state.values()["phone"])
        onAllNodesWithText("Enter a valid email").assertCountEquals(0)
    }

    @Test
    fun optionalFieldsBeyondSixGoUnderMoreDetails() = runComposeUiTest {
        val many = (1..8).map { FieldSpec("f$it", FieldType.Text, Res.string.cat_field_city, required = it == 8) }
        val state = FormState(many)
        setContent {
            AgentProTheme(darkTheme = false) {
                Column(Modifier.verticalScroll(rememberScrollState())) { FormRenderer(state) }
            }
        }
        onNodeWithTag(fieldTestTag("f8")).assertExists() // required: always visible
        onNodeWithTag(fieldTestTag("f6")).assertDoesNotExist()
        onNodeWithText("More details").performScrollTo().performClick()
        onNodeWithTag(fieldTestTag("f6")).assertExists()
        assertTrue(state.isValid.not())
        assertFalse(state.isDirty)
    }
}
