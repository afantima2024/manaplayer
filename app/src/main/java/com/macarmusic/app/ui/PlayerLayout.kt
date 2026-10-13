package com.macarmusic.app.ui

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.macarmusic.app.R
import com.macarmusic.app.model.LyricSide
import kotlin.math.roundToInt

class PlayerLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ScrollView(context, attrs) {
    private val content by lazy { getChildAt(0) as LinearLayout }
    private val cover by lazy { findViewById<View>(R.id.cover) }
    private val controls by lazy { findViewById<View>(R.id.controls) }
    private val metadata by lazy { findViewById<View>(R.id.metadataRow) }
    private val gap get() = resources.getDimensionPixelSize(R.dimen.player_gap)
    private var appliedControls: Triple<Boolean, Boolean, Boolean>? = null
    var compactControls = false
        private set

    fun configureLyrics(landscape: LyricSide, portrait: LyricSide) {
        val horizontal = content.orientation == LinearLayout.HORIZONTAL
        val start = (if (horizontal) landscape else portrait) == LyricSide.START
        val index = if (start) 0 else content.childCount - 1
        if (content.indexOfChild(cover) == index) return
        val params = cover.layoutParams as LinearLayout.LayoutParams
        params.setMargins(0, 0, 0, 0)
        params.marginStart = if (horizontal && !start) gap else 0
        params.marginEnd = if (horizontal && start) gap else 0
        params.topMargin = if (!horizontal && !start) gap else 0
        params.bottomMargin = if (!horizontal && start) gap else 0
        content.removeView(cover)
        content.addView(cover, index, params)
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (childCount == 0 || MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            return super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
        val landscape = content.orientation == LinearLayout.HORIZONTAL
        val height = MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom
        val width = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        var controlsWidth = width
        if (landscape) {
            val coverWidth = (width - 2 * gap).coerceAtLeast(0) / 3
            (cover.layoutParams as LinearLayout.LayoutParams).apply { weight = 0f; this.width = coverWidth }
            (controls.layoutParams as LinearLayout.LayoutParams).apply {
                weight = 0f
                this.width = (width - coverWidth - gap).coerceAtLeast(0)
                controlsWidth = this.width
            }
        }
        configureCompact(height < dp(if (landscape) 420 else 760), controlsWidth)
        isFillViewport = false
        cover.layoutParams.height = 0
        controls.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        metadata.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        // Reserve real control heights first, then share the remainder with artwork/lyrics and metadata.
        forceContentLayout()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val available = (measuredHeight - paddingTop - paddingBottom - content.paddingTop - content.paddingBottom)
            .coerceAtLeast(1)
        val minimumControls = controls.measuredHeight
        val coverParams = cover.layoutParams as LinearLayout.LayoutParams
        val coverHeight = if (landscape) available else {
            minOf((available * 0.4f).roundToInt(), content.measuredWidth,
                (available - minimumControls - gap).coerceAtLeast(dp(72)))
        }
        val controlsHeight = if (landscape) available else {
            available - coverHeight - coverParams.topMargin - coverParams.bottomMargin
        }
        coverParams.height = coverHeight
        metadata.layoutParams.height = metadata.measuredHeight + (controlsHeight - minimumControls).coerceAtLeast(0)
        forceContentLayout()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun forceContentLayout() {
        // Both passes share parent specs; cached measurements would otherwise keep obsolete child heights.
        cover.forceLayout()
        metadata.forceLayout()
        controls.forceLayout()
        content.forceLayout()
    }

    private fun configureCompact(compact: Boolean, width: Int) {
        val narrowTransport = width < dp(7 * 48) + 6 * gap
        val actionWidth = dp(if (narrowTransport) 192 else 96) + (if (narrowTransport) 3 else 1) * gap
        val compactFooter = compact && width >= actionWidth + gap + dp(112)
        val configuration = Triple(compact, narrowTransport, compactFooter)
        if (appliedControls == configuration) return
        appliedControls = configuration
        compactControls = compact
        val transport = findViewById<TransportRow>(R.id.transportRow)
        transport.buttonHeightDp = if (compact) 48 else 64
        val actions = findViewById<LinearLayout>(R.id.actionRow)
        val target = if (narrowTransport) actions else transport
        for ((id, start) in listOf(R.id.shuffle to true, R.id.repeat to false)) {
            val button = findViewById<View>(id)
            if (button.parent !== target) {
                (button.parent as ViewGroup).removeView(button)
                target.addView(button, if (start) 0 else target.childCount,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            }
        }
        for (index in 0 until actions.childCount) {
            (actions.getChildAt(index).layoutParams as LinearLayout.LayoutParams).marginEnd =
                if (index < actions.childCount - 1) gap else 0
        }
        for (id in intArrayOf(R.id.shuffle, R.id.previous, R.id.rewind, R.id.play, R.id.forward, R.id.next, R.id.repeat)) {
            findViewById<View>(id).apply {
                minimumWidth = dp(48)
                minimumHeight = dp(48)
            }
        }
        fun text(id: Int, size: Float, lines: Int) {
            findViewById<TextView>(id).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
                maxLines = lines
            }
        }
        text(R.id.title, if (compact) 20f else 24f, if (compact) 1 else 2)
        text(R.id.artist, if (compact) 14f else 16f, 1)
        text(R.id.album, if (compact) 12f else 14f, 1)
        text(R.id.status, if (compact) 12f else 14f, 1)
        text(R.id.playbackOptions, if (compact) 12f else 14f, if (compact) 1 else 2)
        findViewById<View>(R.id.spectrum).minimumHeight = dp(if (compact) 96 else 132)
        findViewById<LinearLayout>(R.id.footerRow).orientation =
            if (compactFooter) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        actions.layoutParams.width =
            if (compactFooter) actionWidth else ViewGroup.LayoutParams.MATCH_PARENT
        findViewById<View>(R.id.volumeBlock).layoutParams = LinearLayout.LayoutParams(
            if (compactFooter) 0 else ViewGroup.LayoutParams.MATCH_PARENT, dp(48), if (compactFooter) 1f else 0f,
        ).apply {
            marginStart = if (compactFooter) gap else 0
            topMargin = if (compactFooter) 0 else gap
        }
        findViewById<View>(R.id.volumeLabel).isVisible = !compact
        for (id in intArrayOf(R.id.favorite, R.id.details)) {
            findViewById<MaterialButton>(id).apply {
                text = if (compact) "" else contentDescription
                minimumWidth = dp(48)
                iconGravity = if (compact) MaterialButton.ICON_GRAVITY_TEXT_START else MaterialButton.ICON_GRAVITY_START
                iconPadding = if (compact) 0 else dp(8)
                setPaddingRelative(dp(if (compact) 12 else 16), paddingTop, dp(if (compact) 12 else 16), paddingBottom)
            }
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
