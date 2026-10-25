package com.sledje.agentpro.core.forms

import com.sledje.agentpro.core.catalog.SiteSchema
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.validation_required
import com.sledje.agentpro.core.format.Dates
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ValidatorsTest {
    private fun spec(type: FieldType, required: Boolean = false, maxLength: Int? = null, min: Long? = null, max: Long? = null) =
        FieldSpec(key = "f", type = type, label = Res.string.validation_required, required = required, maxLength = maxLength, min = min, max = max)

    @Test
    fun phone() {
        assertNull(Validators.phone("9876543210"))
        assertNull(Validators.phone("+91 98765-43210"))
        assertNull(Validators.phone("919876543210"))
        assertNull(Validators.phone("09876543210"))
        assertEquals(ValidationError.Phone, Validators.phone("1234567890"))
        assertEquals(ValidationError.Phone, Validators.phone("+44 7911 123456"))
    }

    @Test
    fun email() {
        assertNull(Validators.email("ramesh@example.com"))
        assertNull(Validators.email(" a@b.in "))
        assertEquals(ValidationError.Email, Validators.email("ramesh@"))
        assertEquals(ValidationError.Email, Validators.email("ramesh example.com"))
        assertEquals(ValidationError.Email, Validators.email("a@b@c.com"))
    }

    @Test
    fun url() {
        assertNull(Validators.url("https://maps.google.com/x"))
        assertNull(Validators.url("HTTP://example.in"))
        assertEquals(ValidationError.Url, Validators.url("example.com"))
        assertEquals(ValidationError.Url, Validators.url("https://exa mple.com"))
    }

    @Test
    fun maxLength() {
        assertNull(Validators.maxLength("a".repeat(70), 70))
        assertEquals(ValidationError.MaxLength(70), Validators.maxLength("a".repeat(71), 70))
        assertNull(Validators.maxLength("  abc  ", 3)) // trimmed, like the server
    }

    @Test
    fun slug() {
        assertNull(Validators.slug("ramesh-kumar"))
        assertNull(Validators.slug("abc", reserved = emptySet()))
        assertEquals(ValidationError.Slug, Validators.slug("ab"))
        assertEquals(ValidationError.Slug, Validators.slug("a".repeat(31)))
        assertEquals(ValidationError.Slug, Validators.slug("Ramesh"))
        assertEquals(ValidationError.Slug, Validators.slug("ramesh_kumar"))
        assertEquals(ValidationError.SlugReserved, Validators.slug("admin", reserved = setOf("admin")))
    }

    @Test
    fun numbersAndRange() {
        assertNull(Validators.number("70", 0, 70))
        assertEquals(ValidationError.Range(0, 70), Validators.number("71", 0, 70))
        assertEquals(ValidationError.NotANumber, Validators.number("7a"))
    }

    @Test
    fun validateBySpec() {
        assertEquals(ValidationError.Required, Validators.validate(spec(FieldType.Text, required = true), "  "))
        assertNull(Validators.validate(spec(FieldType.Email), ""))
        assertEquals(ValidationError.Email, Validators.validate(spec(FieldType.Email), "x"))
        assertEquals(ValidationError.MaxLength(3), Validators.validate(spec(FieldType.Text, maxLength = 3), "abcd"))
        assertEquals(ValidationError.Range(0, 70), Validators.validate(spec(FieldType.Number, min = 0, max = 70), "80"))
        assertEquals(ValidationError.Required, Validators.validate(spec(FieldType.MultiSelect, required = true), emptyList<String>()))
        assertNull(Validators.validate(spec(FieldType.Toggle, required = true), false))
    }

    @Test
    fun schemaFieldRules() {
        val hero = SiteSchema.sections.first { it.key == "hero" }
        val headline = hero.fields.first { it.key == "headline" }
        assertEquals(ValidationError.Required, Validators.validate(headline, ""))
        assertEquals(ValidationError.MaxLength(70), Validators.validate(headline, "x".repeat(71)))
        assertNull(Validators.validate(headline, "Your trusted LIC advisor"))
    }

    private val today = LocalDate(2026, 10, 24)

    @Test
    fun maxDaysAheadAllowsPastAndTheLimitButNotBeyond() {
        assertNull(Validators.maxDaysAhead(LocalDate(2026, 1, 1), 366, today), "a past date is not this rule's business")
        assertNull(Validators.maxDaysAhead(LocalDate(2026, 12, 31), 366, today))
        assertNull(Validators.maxDaysAhead(today.plus(DatePeriod(days = 366)), 366, today))
        assertEquals(ValidationError.TooFarAhead(366), Validators.maxDaysAhead(today.plus(DatePeriod(days = 367)), 366, today))
    }

    @Test
    fun mustBeTrueFailsWhenOffOrMissing() {
        assertNull(Validators.mustBeTrue(true))
        assertEquals(ValidationError.MustBeTrue, Validators.mustBeTrue(false))
        assertEquals(ValidationError.MustBeTrue, Validators.mustBeTrue(null))
    }

    @Test
    fun customOfferItemFieldsFollowTheSchema() {
        val custom = SiteSchema.section("offers")!!.fields.first { it.key == "custom" }
        val confirmed = custom.itemFields.first { it.key == "confirmed" }
        val endsOn = custom.itemFields.first { it.key == "endsOn" }
        assertTrue(confirmed.mustBeTrue)
        assertEquals(ValidationError.MustBeTrue, Validators.validate(confirmed, false))
        assertEquals(ValidationError.MustBeTrue, Validators.validate(confirmed, null))
        assertNull(Validators.validate(confirmed, true))
        assertEquals(366, endsOn.maxDaysAhead)
        val far = Dates.today().plus(DatePeriod(days = 400))
        assertEquals(ValidationError.TooFarAhead(366), Validators.validate(endsOn, far))
        assertNull(Validators.validate(endsOn, Dates.today().plus(DatePeriod(days = 30))))
        assertNull(Validators.validate(endsOn, Dates.today().minus(DatePeriod(days = 3))))
    }

    @Test
    fun addingACustomOfferGeneratesAnIdAndStartsUnconfirmed() {
        val custom = SiteSchema.section("offers")!!.fields.first { it.key == "custom" }
        val form = FormState(listOf(custom))
        form.addItem("custom")
        form.addItem("custom")
        val ids = form.items("custom").map { it.text("id") }
        assertTrue(ids.all { it.isNotBlank() } && ids.toSet().size == 2, "each new offer gets its own uuid")
        assertEquals("uuid", custom.itemFields.first { it.key == "id" }.generated)
        assertEquals(false, form.items("custom").first().value("confirmed"))
        assertTrue(!form.validateAll())
    }

    @Test
    fun offerFieldSummariesCountTheRawJson() {
        val ref = buildJsonObject { put("source", "company"); put("id", "o1") }
        assertEquals(0, offerFieldCount(FieldType.OfferList, null))
        assertEquals(0, offerFieldCount(FieldType.OfferList, JsonArray(emptyList())))
        assertEquals(2, offerFieldCount(FieldType.OfferList, JsonArray(listOf(ref, ref))))
        assertEquals(0, offerFieldCount(FieldType.OfferRef, null))
        assertEquals(0, offerFieldCount(FieldType.OfferRef, JsonObject(emptyMap())))
        assertEquals(1, offerFieldCount(FieldType.OfferRef, ref))
    }
}
