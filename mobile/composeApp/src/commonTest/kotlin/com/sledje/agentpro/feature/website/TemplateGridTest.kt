package com.sledje.agentpro.feature.website

import com.sledje.agentpro.core.catalog.Templates
import com.sledje.agentpro.feature.website.ui.TEMPLATE_GRID_START_DELAY_MS
import com.sledje.agentpro.feature.website.ui.TemplatePages
import com.sledje.agentpro.feature.website.ui.runTemplateAutoScroll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The template grid's auto-scroll order and its picture strips (docs/UX.md, Template grid; DECISIONS #120). */
class TemplateGridTest {
    @Test
    fun tilesScrollOneAtATimeInOrderAfterTheWaitThenJumpBackAndWrapAround() = runTest {
        val events = mutableListOf<String>()
        // Tile 1 has nothing to scroll (a page no taller than its tile); 1 000 px at 100 px/s is 10 s.
        val max = listOf(1_000f, 0f, 500f)
        val job = launch {
            runTemplateAutoScroll(
                count = 3,
                pxPerSecond = 100f,
                maxOf = { max[it] },
                scrollTo = { i, target, millis ->
                    events += "start $i to ${target.toInt()} in $millis"
                    delay(millis.toLong())
                    events += "end $i"
                },
                reset = { events += "top $it" },
            )
        }
        advanceTimeBy(TEMPLATE_GRID_START_DELAY_MS - 1)
        runCurrent()
        assertTrue(events.isEmpty(), "nothing moves in the first 4 s")
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("start 0 to 1000 in 10000"), events)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(
            listOf("start 0 to 1000 in 10000", "end 0", "top 0", "start 2 to 500 in 5000"),
            events,
            "the first jumps back to the top, then the next scrollable tile starts",
        )
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf("end 2", "top 2", "start 0 to 1000 in 10000"), events.takeLast(3), "after the last, the first again")
        job.cancel()
    }

    @Test
    fun withNothingToScrollItWaitsInsteadOfSpinning() = runTest {
        var asked = 0
        val job = launch {
            runTemplateAutoScroll(
                count = 2,
                pxPerSecond = 100f,
                maxOf = { asked++; 0f },
                scrollTo = { _, _, _ -> error("nothing to scroll") },
                reset = { error("nothing to reset") },
            )
        }
        advanceTimeBy(TEMPLATE_GRID_START_DELAY_MS * 3 + 1)
        runCurrent()
        assertEquals(6, asked, "one pass of both tiles per wait")
        job.cancel()
    }

    @Test
    fun stripsAreMatchedByKeyAndOrderedByNumber() {
        val all = mapOf(
            "template_page_classic_10" to "ten",
            "template_page_classic_2" to "two",
            "template_page_classic_1" to "one",
            "template_page_classic_x" to "not numbered",
            "template_page_classical_1" to "another key",
            "ic_add" to "an icon",
        )
        assertEquals(listOf("one", "two", "ten"), TemplatePages.strips("classic", all))
        assertEquals(emptyList(), TemplatePages.strips("guide", all))
    }

    @Test
    fun everyTemplateHasItsPictureStrips() {
        Templates.all.forEach { template ->
            assertTrue(TemplatePages.strips(template.key).isNotEmpty(), "${template.key}: run scripts/gen-template-pages.py")
        }
    }
}
