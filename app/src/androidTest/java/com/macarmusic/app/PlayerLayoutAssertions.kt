package com.macarmusic.app

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.children
import com.macarmusic.app.ui.MainActivity
import com.macarmusic.app.ui.PlayerLayout
import org.junit.Assert.*

internal object PlayerLayoutAssertions {
    private val controls = listOf(R.id.shuffle, R.id.previous, R.id.rewind, R.id.play, R.id.forward,
        R.id.next, R.id.repeat, R.id.favorite, R.id.switchPlaybackMode, R.id.details, R.id.volumeMute, R.id.volume)
    private val panels = listOf(R.id.screenContainer, R.id.cover, R.id.metadataRow, R.id.seekBlock,
        R.id.transportRow, R.id.actionRow, R.id.volumeBlock, R.id.navigation)

    fun bounds(view: View): Rect {
        val xy = IntArray(2).also(view::getLocationOnScreen)
        return Rect(xy[0], xy[1], xy[0] + view.width, xy[1] + view.height)
    }

    fun snapshot(activity: MainActivity) = (controls + panels).associateWith { bounds(activity.findViewById(it)) }

    fun labels(activity: MainActivity) = listOf(R.id.favorite, R.id.switchPlaybackMode, R.id.details)
        .map { activity.findViewById<TextView>(it).text.toString() }

    fun assertModeActions(activity: MainActivity) {
        val mode = activity.findViewById<View>(R.id.switchPlaybackMode)
        val pin = activity.findViewById<View>(R.id.favorite)
        val details = activity.findViewById<View>(R.id.details)
        val row = mode.parent as ViewGroup
        assertEquals(row.indexOfChild(mode) + 1, row.indexOfChild(pin))
        assertEquals(row.indexOfChild(pin) + 1, row.indexOfChild(details))
        val play = bounds(activity.findViewById(R.id.play)).exactCenterX()
        assertTrue("Mode switching must be farther from central transport than Pin",
            kotlin.math.abs(bounds(mode).exactCenterX() - play) > kotlin.math.abs(bounds(pin).exactCenterX() - play))
    }

    fun assertFits(activity: MainActivity) {
        assertModeActions(activity)
        val container = activity.findViewById<ViewGroup>(R.id.screenContainer)
        val player = container.children.filterIsInstance<PlayerLayout>().single()
        val viewport = bounds(container)
        val density = activity.resources.displayMetrics.density
        val minimum = (48 * density).toInt()
        val geometry = panels.associate { activity.resources.getResourceEntryName(it) to bounds(activity.findViewById(it)) }
        assertEquals("The player must open at the top", 0, player.scrollY)
        for (id in controls) {
            val control = activity.findViewById<View>(id)
            assertTrue("Control needs a 48dp target: $id ${bounds(control)}",
                control.width >= minimum && control.height >= minimum)
            assertTrue("Control must be visible without scrolling: ${activity.resources.getResourceEntryName(id)} " +
                "${bounds(control)} in $viewport; $geometry",
                viewport.contains(bounds(control)))
        }
        assertEquals("The page must fit its viewport", player.height, player.getChildAt(0).height)
        val transport = listOf(R.id.previous, R.id.rewind, R.id.play, R.id.forward, R.id.next)
            .map { bounds(activity.findViewById(it)) }
        assertEquals("Primary transport controls must share one row", 1, transport.map { it.top }.distinct().size)
        transport.zipWithNext().forEach { (left, right) -> assertTrue(left.right <= right.left) }
        val bar = activity.findViewById<SeekBar>(R.id.volume)
        assertEquals(View.LAYOUT_DIRECTION_LTR, bar.layoutDirection)
        val travel = bar.width - bar.paddingLeft - bar.paddingRight
        assertTrue("The volume slider needs useful travel, not a squeezed/reversed track: $travel", travel >= 96 * density)
        val previous = bar.progress
        try {
            bar.progress = 0
            val low = bar.thumb.bounds.centerX()
            assertTrue(bar.paddingLeft - bar.thumbOffset + bar.thumb.bounds.left >= 0)
            bar.progress = bar.max
            assertTrue("Increasing volume must move right", bar.thumb.bounds.centerX() > low)
            assertTrue(bar.paddingLeft - bar.thumbOffset + bar.thumb.bounds.right <= bar.width)
        } finally { bar.progress = previous }
    }
}
