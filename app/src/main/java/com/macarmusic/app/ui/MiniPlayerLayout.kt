package com.macarmusic.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.isVisible
import com.macarmusic.app.R
import kotlin.math.ceil

class MiniPlayerLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {
    private lateinit var transport: LinearLayout
    private lateinit var progress: LinearLayout
    private val row by lazy { LinearLayout(context) }
    private val scroll by lazy {
        HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    var singleRow = false
        set(value) {
            if (field == value) return
            field = value
            (transport.parent as ViewGroup).removeView(transport)
            (progress.parent as ViewGroup).removeView(progress)
            removeAllViews()
            minimumHeight = if (value) 0 else dp(80)
            setPaddingRelative(dp(if (value) 8 else 12), if (value) 0 else dp(8),
                dp(if (value) 8 else 12), if (value) 0 else dp(8))
            findInTransport<View>(R.id.miniInfo).minimumHeight = dp(if (value) 48 else 56)
            findInTransport<View>(R.id.miniArtist).isVisible = !value
            val time = progress.findViewById<TextView>(R.id.miniTime)
            time.maxLines = if (value) 1 else Int.MAX_VALUE
            for (id in listOf(R.id.miniPlay, R.id.miniNext)) {
                findInTransport<View>(id).layoutParams = LayoutParams(dp(if (value) 48 else 56),
                    dp(if (value) 48 else 56)).apply { marginEnd = if (id == R.id.miniPlay) dp(8) else 0 }
            }
            if (value) {
                row.addView(transport)
                row.addView(progress)
                addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
            } else {
                findInTransport<View>(R.id.miniArtwork).isVisible = true
                findInTransport<View>(R.id.miniInfo).isVisible = true
                findInTransport<View>(R.id.miniInfo).layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                addView(transport, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
                addView(progress, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
                time.textSize = 14f
            }
            requestLayout()
        }

    @SuppressLint("ClickableViewAccessibility") // Native SeekBar retains its touch/accessibility handling.
    override fun onFinishInflate() {
        super.onFinishInflate()
        transport = getChildAt(0) as LinearLayout
        progress = getChildAt(1) as LinearLayout
        progress.findViewById<SeekBar>(R.id.miniSeek).setOnTouchListener { view, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) view.parent.requestDisallowInterceptTouchEvent(true)
            false
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (singleRow) {
            val available = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
            val time = progress.findViewById<TextView>(R.id.miniTime)
            val sizeSp = if (available < dp(360)) 12f else 14f
            if (time.textSize != TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, resources.displayMetrics))
                time.textSize = sizeSp
            val timeWidth = ceil(time.paint.measureText(time.text.toString())).toInt() + time.paddingLeft + time.paddingRight
            val minimum = dp(104 + 8 + if (available >= dp(360)) 96 else 48) + timeWidth
            val width = maxOf(available, minimum)
            val spare = width - minimum
            val artworkWidth = if (spare >= dp(56)) dp(56) else 0
            val infoWidth = if (spare >= dp(152)) minOf(dp(220), spare - artworkWidth) else 0
            findInTransport<View>(R.id.miniArtwork).isVisible = artworkWidth > 0
            findInTransport<View>(R.id.miniInfo).apply {
                isVisible = infoWidth > 0
                rowSize(this, infoWidth)
            }
            val transportWidth = dp(104) + artworkWidth + infoWidth
            rowSize(transport, transportWidth)
            rowSize(progress, width - transportWidth - dp(8), dp(8))
            if (row.layoutParams.width != width) row.layoutParams = row.layoutParams.apply { this.width = width }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun rowSize(view: View, width: Int, start: Int = 0) {
        val params = view.layoutParams as LayoutParams
        if (params.width != width || params.height != dp(48) || params.weight != 0f || params.marginStart != start) {
            view.layoutParams = params.apply {
                this.width = width
                height = dp(48)
                weight = 0f
                marginStart = start
            }
        }
    }

    private fun <T : View> findInTransport(id: Int): T = transport.findViewById(id)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
