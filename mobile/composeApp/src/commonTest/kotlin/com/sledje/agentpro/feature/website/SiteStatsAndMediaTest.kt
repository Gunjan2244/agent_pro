package com.sledje.agentpro.feature.website

import com.sledje.agentpro.core.platform.CropRect
import com.sledje.agentpro.core.platform.ImageAspect
import com.sledje.agentpro.core.platform.ImageGeometry
import com.sledje.agentpro.feature.website.data.DayViews
import com.sledje.agentpro.feature.website.data.MediaPaths
import com.sledje.agentpro.feature.website.data.SiteStatsAggregator
import com.sledje.agentpro.feature.website.data.StatsPeriod
import com.sledje.agentpro.feature.website.ui.formatPercent
import com.sledje.agentpro.testing.TEST_USER
import com.sledje.agentpro.testing.viewRow
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Stats aggregation for a period (Asia/Kolkata days), image geometry and media paths. */
class SiteStatsAndMediaTest {
    private val today = LocalDate(2026, 10, 16)

    @Test
    fun weekCoversTheLastSevenDaysIncludingTodayZeroFilled() {
        val rows = listOf(
            DayViews(LocalDate(2026, 10, 9), 100), // 8 days ago: outside the week
            DayViews(LocalDate(2026, 10, 10), 5), // first day of the week
            DayViews(LocalDate(2026, 10, 16), 7),
            DayViews(LocalDate(2026, 10, 17), 50), // future: ignored
        )
        val stats = SiteStatsAggregator.aggregate(rows, enquiryDays = emptyList(), period = StatsPeriod.Week, today = today)
        assertEquals(7, stats.days.size)
        assertEquals(LocalDate(2026, 10, 10), stats.days.first().day)
        assertEquals(today, stats.days.last().day)
        assertEquals(listOf(5, 0, 0, 0, 0, 0, 7), stats.days.map { it.views })
        assertEquals(12, stats.views)
    }

    @Test
    fun monthCountsEnquiriesInsideThePeriodAndConversion() {
        val rows = listOf(DayViews(LocalDate(2026, 9, 17), 150), DayViews(LocalDate(2026, 10, 1), 50))
        val enquiries = listOf(LocalDate(2026, 9, 16), LocalDate(2026, 9, 17), LocalDate(2026, 10, 2), LocalDate(2026, 10, 16), LocalDate(2026, 10, 16))
        val stats = SiteStatsAggregator.aggregate(rows, enquiries, StatsPeriod.Month, today)
        assertEquals(30, stats.days.size)
        assertEquals(LocalDate(2026, 9, 17), stats.days.first().day)
        assertEquals(200, stats.views)
        assertEquals(4, stats.enquiries, "16 Sep is outside the 30 days")
        assertEquals(2.0, stats.conversionPercent)
    }

    @Test
    fun noViewsMeansNoConversionRate() {
        val stats = SiteStatsAggregator.aggregate(emptyList(), listOf(today), StatsPeriod.Week, today)
        assertEquals(0, stats.views)
        assertNull(stats.conversionPercent)
    }

    @Test
    fun cacheRowsParseAndBadDaysAreSkipped() {
        val parsed = SiteStatsAggregator.parseRows(listOf(viewRow("2026-10-15", 3), viewRow("not-a-day", 9)))
        assertEquals(listOf(DayViews(LocalDate(2026, 10, 15), 3)), parsed)
    }

    @Test
    fun percentFormatting() {
        assertEquals("12.5", formatPercent(12.5))
        assertEquals("3", formatPercent(3.0))
        assertEquals("33.3", formatPercent(100.0 / 3))
    }

    @Test
    fun aspectParsing() {
        assertEquals(ImageAspect.Square, ImageAspect.parse("1:1"))
        assertEquals(ImageAspect(16, 9), ImageAspect.parse(" 16 : 9 "))
        assertNull(ImageAspect.parse(null))
        assertNull(ImageAspect.parse("0:1"))
        assertNull(ImageAspect.parse("square"))
    }

    @Test
    fun centreCropToTheSchemaAspect() {
        assertEquals(CropRect(500, 0, 3000, 3000), ImageGeometry.centerCrop(4000, 3000, ImageAspect.Square))
        assertEquals(CropRect(0, 500, 3000, 3000), ImageGeometry.centerCrop(3000, 4000, ImageAspect.Square))
        assertEquals(CropRect(0, 0, 4000, 3000), ImageGeometry.centerCrop(4000, 3000, null))
        assertEquals(CropRect(0, 375, 4000, 2250), ImageGeometry.centerCrop(4000, 3000, ImageAspect(16, 9)))
    }

    @Test
    fun scaledToAtMost1024AndNeverUp() {
        assertEquals(1024 to 1024, ImageGeometry.fitWithin(3000, 3000, 1024))
        assertEquals(1024 to 576, ImageGeometry.fitWithin(4000, 2250, 1024))
        assertEquals(800 to 600, ImageGeometry.fitWithin(800, 600, 1024))
        assertEquals(2, ImageGeometry.sampleSize(4000, 3000, 1024), "decoded ≥ 1024 on the shorter side")
        assertEquals(1, ImageGeometry.sampleSize(1500, 1200, 1024))
    }

    @Test
    fun objectPathsStartWithTheAgentsFolder() {
        val path = MediaPaths.objectPath(TEST_USER, "Hero Photo!", "webp", 1760000000000, 42)
        assertEquals("$TEST_USER/hero-photo-1760000000000-42.webp", path)
        assertTrue(path.startsWith("$TEST_USER/"), "Storage policy: first folder = auth.uid()")
    }
}
