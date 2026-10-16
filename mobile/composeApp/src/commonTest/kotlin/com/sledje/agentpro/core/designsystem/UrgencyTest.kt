package com.sledje.agentpro.core.designsystem

import com.sledje.agentpro.core.catalog.DueBucket
import com.sledje.agentpro.core.catalog.Tone
import com.sledje.agentpro.core.designsystem.components.UrgencyLabel
import com.sledje.agentpro.core.designsystem.components.dueBucketFor
import com.sledje.agentpro.core.designsystem.components.urgencyLabel
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class UrgencyTest {
    private val today = LocalDate(2026, 10, 15)

    @Test
    fun tonesFollowTheCatalogDueBuckets() {
        assertEquals(Tone.Danger, dueBucketFor(-1).tone)
        assertEquals(Tone.Warning, dueBucketFor(0).tone)
        assertEquals(Tone.Warning, dueBucketFor(7).tone)
        assertEquals(Tone.Info, dueBucketFor(8).tone)
        assertEquals(Tone.Info, dueBucketFor(30).tone)
        assertEquals(Tone.Neutral, dueBucketFor(31).tone)
        assertEquals(DueBucket.Next3Months, dueBucketFor(400))
    }

    @Test
    fun labels() {
        assertEquals(UrgencyLabel.Overdue(3), urgencyLabel(LocalDate(2026, 10, 12), today))
        assertEquals(UrgencyLabel.Today, urgencyLabel(today, today))
        assertEquals(UrgencyLabel.DueIn(5), urgencyLabel(LocalDate(2026, 10, 20), today))
        assertEquals(UrgencyLabel.DueIn(7), urgencyLabel(LocalDate(2026, 10, 22), today))
        assertEquals(UrgencyLabel.DueOn(LocalDate(2027, 1, 15)), urgencyLabel(LocalDate(2027, 1, 15), today))
    }
}
