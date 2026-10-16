package com.sledje.agentpro.core.forms

import com.sledje.agentpro.core.catalog.SiteSchema
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.validation_required
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FormStateTest {
    private fun field(key: String, type: FieldType = FieldType.Text, required: Boolean = false) =
        FieldSpec(key = key, type = type, label = Res.string.validation_required, required = required)

    @Test
    fun sixVisibleFieldsAndRequiredNeverHidden() {
        val five = (1..5).map { field("f$it") }
        assertEquals(5 to 0, splitVisibleFields(five).let { it.first.size to it.second.size })

        val nine = (1..9).map { field("f$it", required = it == 8) }
        val (visible, more) = splitVisibleFields(nine)
        assertEquals(listOf("f1", "f2", "f3", "f4", "f5", "f8"), visible.map { it.key })
        assertEquals(listOf("f6", "f7", "f9"), more.map { it.key })

        val (v2, m2) = splitVisibleFields(nine, visibleKeys = setOf("f1"))
        assertEquals(listOf("f1", "f8"), v2.map { it.key })
        assertEquals(7, m2.size)
    }

    @Test
    fun contactSectionFitsTheRule() {
        val contact = SiteSchema.sections.first { it.key == "contact" }
        val (visible, _) = splitVisibleFields(contact.fields)
        assertTrue(visible.size <= MAX_VISIBLE_FIELDS)
        assertTrue(visible.any { it.key == "phone" })
    }

    @Test
    fun errorsShowOnlyAfterBlurOrSubmit() {
        val state = FormState(listOf(field("name", required = true), field("phone", FieldType.Phone)))
        assertEquals(ValidationError.Required, state.error("name"))
        assertNull(state.visibleError("name"))
        state.onFocusLost("name")
        assertEquals(ValidationError.Required, state.visibleError("name"))
        assertFalse(state.validateAll())
        state.set("name", "Ramesh")
        state.set("phone", "098765 43210")
        assertTrue(state.validateAll())
        assertEquals("+919876543210", state.values()["phone"]) // normalised to +91
    }

    @Test
    fun valuesAreTypedAndDirtyTracked() {
        val fields = listOf(field("years", FieldType.Number), field("show", FieldType.Toggle), field("bio"))
        val state = FormState(fields, mapOf("years" to "12"))
        assertFalse(state.isDirty)
        assertEquals(12L, state.values()["years"])
        assertEquals(false, state.values()["show"])
        assertNull(state.values()["bio"])
        state.set("bio", "Agent since 2010")
        assertTrue(state.isDirty)
    }

    @Test
    fun itemLists() {
        val testimonials = SiteSchema.sections.first { it.key == "testimonials" }.fields.first()
        val state = FormState(listOf(testimonials), mapOf("items" to listOf(mapOf("name" to "Asha", "quote" to "Very helpful"))))
        assertEquals(1, state.items("items").size)
        assertTrue(state.validateAll())
        state.addItem("items")
        assertFalse(state.validateAll()) // the new item's required fields are empty
        state.removeItem("items", 1)
        assertTrue(state.validateAll())
        repeat(10) { state.addItem("items") }
        assertEquals(testimonials.maxItems, state.items("items").size)
        assertEquals(listOf(mapOf("name" to "Asha", "city" to null, "quote" to "Very helpful")), state.values()["items"].let { (it as List<*>).take(1) })
    }
}
