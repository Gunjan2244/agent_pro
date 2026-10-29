package com.sledje.agentpro.feature.website

import com.sledje.agentpro.core.platform.CropRect
import com.sledje.agentpro.core.platform.ImageAspect
import com.sledje.agentpro.core.platform.ImageCrop
import com.sledje.agentpro.core.platform.ImageGeometry
import com.sledje.agentpro.core.platform.ImageProcessor
import com.sledje.agentpro.feature.website.data.DayViews
import com.sledje.agentpro.feature.website.data.MediaPaths
import com.sledje.agentpro.feature.website.data.SiteStatsAggregator
import com.sledje.agentpro.feature.website.data.StatsPeriod
import com.sledje.agentpro.feature.website.ui.formatPercent
import com.sledje.agentpro.testing.TEST_USER
import com.sledje.agentpro.testing.viewRow
import kotlinx.datetime.LocalDate
import kotlin.math.abs
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
        // A 4x zoom of a 50 MP photo would ask for the full image; the pixel cap (12 MP) takes it to a quarter.
        assertEquals(4, ImageGeometry.sampleSize(8160, 6120, 4096), "≤ ${ImageProcessor.MAX_DECODE_PIXELS} pixels")
    }

    @Test
    fun theAgentsCropZoomsAndMovesInsideTheImage() {
        // Zoom 1, centred: the centred crop.
        assertEquals(CropRect(500, 0, 3000, 3000), ImageGeometry.crop(4000, 3000, ImageAspect.Square, ImageCrop.Centered))
        // Zoom 2 around (1000, 750): a 1500 px square, moved down to the top edge.
        assertEquals(CropRect(250, 0, 1500, 1500), ImageGeometry.crop(4000, 3000, ImageAspect.Square, ImageCrop(2f, 0.25f, 0.25f)))
        // A centre past the edge is moved back inside; zoom outside 1..4 is clamped.
        assertEquals(CropRect(1000, 0, 3000, 3000), ImageGeometry.crop(4000, 3000, ImageAspect.Square, ImageCrop(0.5f, 1f, 0f)))
        assertEquals(750, ImageGeometry.crop(4000, 3000, ImageAspect.Square, ImageCrop(9f)).width)
        assertEquals(CropRect(0, 0, 4000, 3000), ImageGeometry.crop(4000, 3000, null, ImageCrop(2f)))
    }

    @Test
    fun clampKeepsTheFrameCovered() {
        val c = ImageGeometry.clampCrop(4000, 3000, ImageAspect.Square, ImageCrop(1f, 0f, 0.9f))
        assertEquals(ImageCrop(1f, 0.375f, 0.5f), c, "at zoom 1 the square spans the height, so only x can move")
        assertEquals(ImageCrop(ImageCrop.MAX_ZOOM, 0.5f, 0.5f), ImageGeometry.clampCrop(4000, 3000, ImageAspect.Square, ImageCrop(10f)))
    }

    @Test
    fun shownPartFollowsTheWebsitesObjectPosition() {
        // A tall 4:5 cut from a square keeps the full height and the middle 80% of the width.
        assertClose(listOf(0.1f, 0f, 0.8f, 1f), ImageGeometry.shownPart(ImageAspect.Square, ImageAspect(4, 5), focusY = 25).toList())
        // A wide 4:3 cut keeps 75% of the height, a quarter of the spare room above it (object-position 50% 25%).
        val wide = ImageGeometry.shownPart(ImageAspect.Square, ImageAspect(4, 3), focusY = 25).toList()
        assertClose(listOf(0f, 0.0625f, 1f, 0.75f), wide)
        assertClose(listOf(0f, 0f, 1f, 1f), ImageGeometry.shownPart(ImageAspect.Square, ImageAspect.Square, focusY = 50).toList())
    }

    private fun assertClose(expected: List<Float>, actual: List<Float>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) -> assertTrue(abs(e - a) < 1e-4f, "expected $expected, got $actual") }
    }

    @Test
    fun objectPathsStartWithTheAgentsFolder() {
        val path = MediaPaths.objectPath(TEST_USER, "Hero Photo!", "webp", 1760000000000, 42)
        assertEquals("$TEST_USER/hero-photo-1760000000000-42.webp", path)
        assertTrue(path.startsWith("$TEST_USER/"), "Storage policy: first folder = auth.uid()")
    }
}
