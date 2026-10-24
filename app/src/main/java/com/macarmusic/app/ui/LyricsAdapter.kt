package com.macarmusic.app.ui

import android.os.SystemClock
import android.view.Gravity
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.macarmusic.app.lyrics.LyricKaraoke
import com.macarmusic.app.lyrics.LyricPlayback
import com.macarmusic.app.lyrics.Lyrics
import com.macarmusic.app.model.LyricAppearance

class LyricsAdapter : RecyclerView.Adapter<LyricsAdapter.Holder>() {
    var lyrics: Lyrics? = null
        private set
    var activeLine: Int = -1
        private set
    private var appearance = LyricAppearance()
    private var playback = LyricPlayback()
    private var activeHolder: Holder? = null

    fun configure(value: LyricAppearance): Boolean {
        if (appearance == value) return false
        appearance = value
        activeHolder?.let(::configureKaraoke)
        notifyItemRangeChanged(0, itemCount)
        return true
    }

    fun submit(value: Lyrics?) {
        if (value == lyrics) return
        val previousCount = itemCount
        activeHolder?.text?.stopKaraoke()
        activeHolder = null
        lyrics = value
        activeLine = -1
        if (previousCount > 0) notifyItemRangeRemoved(0, previousCount)
        if (itemCount > 0) notifyItemRangeInserted(0, itemCount)
    }

    fun highlight(positionMs: Long, durationMs: Long = 0, advancing: Boolean = false, speed: Float = 1f): Boolean {
        playback = LyricPlayback(positionMs, durationMs, advancing, speed, SystemClock.uptimeMillis())
        val next = lyrics?.activeLine(positionMs) ?: -1
        if (next == activeLine) {
            activeHolder?.let(::configureKaraoke)
            return false
        }
        val previous = activeLine
        activeHolder?.text?.stopKaraoke()
        activeHolder = null
        activeLine = next
        if (previous >= 0) notifyItemChanged(previous)
        if (next >= 0) notifyItemChanged(next)
        return true
    }

    fun stopAnimation() {
        playback = playback.copy(positionMs = playback.positionAt(SystemClock.uptimeMillis()), advancing = false)
        activeHolder?.let(::configureKaraoke)
    }

    private fun configureKaraoke(holder: Holder) {
        holder.text.configure(appearance.karaoke, appearance.karaokeColor, lyrics?.lines?.getOrNull(activeLine)?.timeMs,
            LyricKaraoke.endMs(lyrics, activeLine, playback.durationMs), playback)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val density = parent.resources.displayMetrics.density
        return Holder(KaraokeTextView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            gravity = Gravity.CENTER
            textSize = 20f
            includeFontPadding = false
            setPadding((12 * density).toInt(), 0, (12 * density).toInt(), 0)
        })
    }

    override fun getItemCount(): Int = lyrics?.lines?.size ?: 0

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val active = position == activeLine
        holder.text.apply {
            val density = resources.displayMetrics.density
            textSize = (if (active) appearance.activeSizeSp else appearance.otherSizeSp).toFloat()
            val verticalPadding = ((if (active) 10 else 2) * density).toInt()
            setPadding(paddingLeft, verticalPadding, paddingRight, verticalPadding)
            minHeight = if (active) (48 * density).toInt() else 0
            text = lyrics?.lines?.get(position)?.text
            isSelected = active
            val customColor = if (active) appearance.activeColor else appearance.otherColor
            alpha = if (active || customColor != null) 1f else 0.75f
            setTextColor(customColor?.let { it or 0xff000000.toInt() } ?: AccentTheme.color(this,
                if (active) com.google.android.material.R.attr.colorPrimary else com.google.android.material.R.attr.colorOnSurface))
        }
        if (active) {
            activeHolder = holder
            configureKaraoke(holder)
        } else {
            if (activeHolder === holder) activeHolder = null
            holder.text.stopKaraoke()
        }
    }

    override fun onViewAttachedToWindow(holder: Holder) {
        super.onViewAttachedToWindow(holder)
        if (holder.bindingAdapterPosition == activeLine && activeLine >= 0) {
            activeHolder = holder
            configureKaraoke(holder)
        }
    }

    override fun onViewDetachedFromWindow(holder: Holder) {
        if (activeHolder === holder) activeHolder = null
        holder.text.stopKaraoke()
        super.onViewDetachedFromWindow(holder)
    }

    override fun onViewRecycled(holder: Holder) {
        if (activeHolder === holder) activeHolder = null
        holder.text.stopKaraoke()
        super.onViewRecycled(holder)
    }

    class Holder(val text: KaraokeTextView) : RecyclerView.ViewHolder(text)
}
