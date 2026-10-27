package com.sledje.agentpro.core.platform

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The site preview keeps a vertical drag away from the template pager (AndroidSitePreview): once a drag passes the
 * touch slop mostly vertically, the parent is asked not to intercept; a sideways drag is left to the pager.
 */
@RunWith(RobolectricTestRunner::class)
class VerticalScrollLockTest {
    private class Parent : FrameLayout(RuntimeEnvironment.getApplication()) {
        val requests = mutableListOf<Boolean>()

        override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            requests += disallowIntercept
            super.requestDisallowInterceptTouchEvent(disallowIntercept)
        }
    }

    private val parent = Parent()
    private val child = View(parent.context).also(parent::addView)
    private val lock = VerticalScrollLock(touchSlop = SLOP)

    private fun send(action: Int, x: Float, y: Float): Boolean {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, x, y, 0)
        return lock.onTouch(child, event).also { event.recycle() }
    }

    @Test
    fun aVerticalDragIsKeptFromThePager() {
        assertFalse(send(MotionEvent.ACTION_DOWN, 100f, 100f))
        send(MotionEvent.ACTION_MOVE, 102f, 104f) // within the slop: undecided
        assertEquals(emptyList<Boolean>(), parent.requests)
        assertFalse("the WebView still scrolls itself", send(MotionEvent.ACTION_MOVE, 104f, 100f + SLOP + 4))
        assertEquals(listOf(true), parent.requests)
        send(MotionEvent.ACTION_MOVE, 140f, 200f)
        assertEquals("decided once per gesture", listOf(true), parent.requests)
    }

    @Test
    fun aSidewaysDragStillSwipesThePager() {
        send(MotionEvent.ACTION_DOWN, 100f, 100f)
        send(MotionEvent.ACTION_MOVE, 100f + SLOP + 4, 103f)
        send(MotionEvent.ACTION_MOVE, 100f, 300f) // turning vertical later doesn't take it back
        assertEquals(emptyList<Boolean>(), parent.requests)
    }

    @Test
    fun eachGestureDecidesAfresh() {
        send(MotionEvent.ACTION_DOWN, 100f, 100f)
        send(MotionEvent.ACTION_MOVE, 100f + SLOP + 4, 100f)
        send(MotionEvent.ACTION_UP, 100f + SLOP + 4, 100f)
        send(MotionEvent.ACTION_DOWN, 100f, 100f)
        send(MotionEvent.ACTION_MOVE, 100f, 100f + SLOP + 4)
        assertEquals(listOf(true), parent.requests)
    }

    private companion object {
        const val SLOP = 16
    }
}
