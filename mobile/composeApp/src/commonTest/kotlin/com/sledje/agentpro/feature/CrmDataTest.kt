package com.sledje.agentpro.feature

import com.sledje.agentpro.core.catalog.EnquirySource
import com.sledje.agentpro.core.catalog.EnquiryStatus
import com.sledje.agentpro.core.catalog.Interest
import com.sledje.agentpro.core.catalog.PolicyStatus
import com.sledje.agentpro.core.catalog.PremiumMode
import com.sledje.agentpro.core.data.RowJson
import com.sledje.agentpro.core.data.parseDateOrNull
import com.sledje.agentpro.feature.customers.data.CustomerDto
import com.sledje.agentpro.feature.customers.data.CustomerInput
import com.sledje.agentpro.feature.customers.data.toEntity
import com.sledje.agentpro.feature.customers.data.toModel
import com.sledje.agentpro.feature.customers.data.toRow
import com.sledje.agentpro.feature.enquiries.data.EnquiryDto
import com.sledje.agentpro.feature.enquiries.data.statusPatch
import com.sledje.agentpro.feature.enquiries.data.toEntity
import com.sledje.agentpro.feature.enquiries.data.toModel
import com.sledje.agentpro.feature.policies.data.MarkPaidResult
import com.sledje.agentpro.feature.policies.data.PolicyDto
import com.sledje.agentpro.feature.policies.data.PolicyInput
import com.sledje.agentpro.feature.policies.data.PremiumPaymentDto
import com.sledje.agentpro.feature.policies.data.toEntity
import com.sledje.agentpro.feature.policies.data.toModel
import com.sledje.agentpro.feature.policies.data.toRow
import com.sledje.agentpro.core.format.RelativeTime
import com.sledje.agentpro.core.forms.FieldSpec
import com.sledje.agentpro.core.forms.FieldType
import com.sledje.agentpro.core.forms.FormState
import com.sledje.agentpro.core.forms.ValidationError
import com.sledje.agentpro.core.forms.Validators
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.payment_amount
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Rows as PostgREST returns them → Room entities → models, and the column patches the repositories send. */
class CrmDataTest {
    private fun obj(text: String) = RowJson.decodeFromString(JsonObject.serializer(), text)

    @Test
    fun enquiryRowDecodes() {
        val row = obj(
            """{"id":"e1","agent_id":"a","site_id":"s","name":"Sunita","phone":"+919812345678","email":null,
            "interest":"term","source":"website","message":"Call me","status":"follow_up",
            "follow_up_at":"2026-10-20T04:30:00+00:00","notes":null,"seen_at":null,"customer_id":null,
            "created_at":"2026-10-16T04:30:00.123456+00:00","updated_at":"2026-10-16T04:30:00.123456+00:00",
            "deleted_at":null,"new_server_column":1}""",
        )
        val e = RowJson.decodeFromJsonElement(EnquiryDto.serializer(), row).toEntity().toModel()
        assertEquals(EnquiryStatus.FollowUp, e.status)
        assertEquals(Interest.Term, e.interest)
        assertEquals(EnquirySource.Website, e.source)
        assertEquals(Instant.parse("2026-10-20T04:30:00Z"), e.followUpAt)
        assertTrue(e.unseen)
    }

    @Test
    fun statusPatchSetsTheFollowUpOnlyForFollowUp() {
        val at = Instant.parse("2026-10-20T04:30:00Z")
        val followUp = statusPatch(EnquiryStatus.FollowUp, at)
        assertEquals("follow_up", followUp["status"]!!.jsonPrimitive.content)
        assertEquals(at.toString(), followUp["follow_up_at"]!!.jsonPrimitive.content)
        val contacted = statusPatch(EnquiryStatus.Contacted, at)
        assertFalse("follow_up_at" in contacted, "other statuses keep the stored follow-up date")
    }

    @Test
    fun customerRowsRoundTrip() {
        val row = CustomerInput(name = " Sunita ", phone = "+919812345678", email = "S@Example.com", dob = LocalDate(1990, 5, 1)).toRow()
        assertEquals("Sunita", row["name"]!!.jsonPrimitive.content)
        assertEquals("s@example.com", row["email"]!!.jsonPrimitive.content)
        assertEquals("1990-05-01", row["dob"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, row["city"], "blank optional fields are cleared")
        val dto = RowJson.decodeFromJsonElement(
            CustomerDto.serializer(),
            obj("""{"id":"c1","name":"Sunita","dob":"1990-05-01","source_enquiry_id":"e1","created_at":"2026-10-16T04:30:00+00:00","updated_at":"2026-10-16T04:30:00+00:00"}"""),
        )
        val c = dto.toEntity().toModel()
        assertEquals(LocalDate(1990, 5, 1), c.dob)
        assertEquals("e1", c.sourceEnquiryId)
    }

    @Test
    fun policyRowsRoundTrip() {
        val input = PolicyInput(
            customerId = "c1", planName = "Jeevan Anand", policyNumber = "123", premiumAmount = 12000.0,
            premiumMode = PremiumMode.Quarterly, startDate = LocalDate(2026, 10, 15), nextDueDate = LocalDate(2027, 1, 15),
        )
        val row = input.toRow()
        assertEquals("quarterly", row["premium_mode"]!!.jsonPrimitive.content)
        assertEquals("2027-01-15", row["next_due_date"]!!.jsonPrimitive.content)
        assertEquals("active", row["status"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, row["sum_assured"])
        val p = RowJson.decodeFromJsonElement(
            PolicyDto.serializer(),
            obj(
                """{"id":"p1","customer_id":"c1","plan_name":"Jeevan Anand","premium_amount":12000.00,"sum_assured":500000,
                "premium_mode":"quarterly","start_date":"2026-10-15","next_due_date":"2027-01-15","status":"lapsed",
                "created_at":"2026-10-16T04:30:00+00:00","updated_at":"2026-10-16T04:30:00+00:00"}""",
            ),
        ).toEntity().toModel()
        assertEquals(12000.0, p.premiumAmount)
        assertEquals(PolicyStatus.Lapsed, p.status)
        assertEquals(48000.0, p.annualPremium)
        assertEquals(LocalDate(2027, 1, 15), p.nextDueDate)
    }

    @Test
    fun paymentsAndRpcResultsDecode() {
        val pay = RowJson.decodeFromJsonElement(
            PremiumPaymentDto.serializer(),
            obj("""{"id":"x","policy_id":"p1","amount":12000,"paid_on":"2026-10-16","period_due_date":"2026-10-15","created_at":"2026-10-16T04:30:00+00:00","updated_at":"2026-10-16T04:30:00+00:00"}"""),
        ).toEntity().toModel()!!
        assertEquals(LocalDate(2026, 10, 15), pay.periodDueDate)
        val result = RowJson.decodeFromJsonElement(
            MarkPaidResult.serializer(),
            obj("""{"payment_id":"x","period_due_date":"2026-10-15","next_due_date":"2027-01-15"}"""),
        )
        assertEquals("2027-01-15", result.nextDueDate)
        assertNull(parseDateOrNull("not a date"))
    }

    @Test
    fun relativeTimesInLists() {
        val now = Instant.parse("2026-10-16T10:00:00Z")
        assertEquals(RelativeTime.Label.JustNow, RelativeTime.of(now, now))
        assertEquals(RelativeTime.Label.Minutes(5), RelativeTime.of(now - 5.minutes, now))
        assertEquals(RelativeTime.Label.Hours(3), RelativeTime.of(now - 3.hours, now))
        assertEquals(RelativeTime.Label.Yesterday, RelativeTime.of(Instant.parse("2026-10-15T10:00:00Z"), now))
        assertEquals(RelativeTime.Label.Days(3), RelativeTime.of(Instant.parse("2026-10-13T10:00:00Z"), now))
        assertEquals(RelativeTime.Label.DayMonth(LocalDate(2026, 10, 1)), RelativeTime.of(Instant.parse("2026-10-01T10:00:00Z"), now))
        assertEquals(RelativeTime.Label.FullDate(LocalDate(2025, 12, 1)), RelativeTime.of(Instant.parse("2025-12-01T10:00:00Z"), now))
        // Asia/Kolkata calendar days: 19:00Z on the 15th is already the 16th (00:30 IST).
        assertEquals(RelativeTime.Label.Hours(15), RelativeTime.of(Instant.parse("2026-10-15T19:00:00Z"), now))
    }

    @Test
    fun moneyFieldsValidateAndReturnAmounts() {
        val spec = FieldSpec("amount", FieldType.Money, Res.string.payment_amount, required = true, min = 1)
        assertNull(Validators.validate(spec, "1,25,000.50"))
        assertEquals(ValidationError.NotANumber, Validators.validate(spec, "12.345"))
        assertEquals(ValidationError.Range(1, null), Validators.validate(spec, "0"))
        val form = FormState(listOf(spec), mapOf("amount" to "12000"))
        assertEquals(12000.0, form.values()["amount"])
    }

    @Test
    fun websiteEnquirySourceCountsOnlyWebsiteEnquiriesByKolkataDay() = kotlinx.coroutines.test.runTest {
        val repo = com.sledje.agentpro.testing.FakeEnquiryRepository(
            listOf(
                com.sledje.agentpro.testing.testEnquiry("a", source = EnquirySource.Website, createdAt = Instant.parse("2026-10-15T19:00:00Z")),
                com.sledje.agentpro.testing.testEnquiry("b", source = EnquirySource.Referral, createdAt = Instant.parse("2026-10-16T04:30:00Z")),
                com.sledje.agentpro.testing.testEnquiry("c", source = EnquirySource.Website, createdAt = Instant.parse("2026-10-16T04:30:00Z")),
            ),
        )
        val days = com.sledje.agentpro.feature.website.data.WebsiteEnquirySource.from(repo).observeWebsiteEnquiryDays().first()
        assertEquals(listOf(LocalDate(2026, 10, 16), LocalDate(2026, 10, 16)), days)
    }
}
