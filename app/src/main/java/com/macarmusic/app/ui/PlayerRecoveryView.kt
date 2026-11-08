package com.macarmusic.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.macarmusic.app.MaCarMusicApp
import com.macarmusic.app.R

class PlayerRecoveryView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {
    private lateinit var retryAction: () -> Unit
    private lateinit var reselectAction: () -> Unit
    private var contentsBuilt = false
    val text: AppCompatTextView by lazy {
        AppCompatTextView(context).apply {
            id = R.id.recoveryText
            textSize = 14f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(48)
            isFocusable = true
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener {
                AccentTheme.dialog(context).setTitle(R.string.recovery_details).setMessage(this.text)
                    .setTransientNoticesEnabled(false).setPositiveButton(R.string.close, null).show()
            }
        }
    }
    private fun action(id: Int) =
        MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            this.id = id
            isAllCaps = false
            textSize = 14f
            minWidth = dp(48)
            minimumWidth = dp(48)
            minHeight = dp(48)
            insetTop = 0
            insetBottom = 0
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
    val retry by lazy {
        action(R.id.recoveryRetry).apply {
            setText(R.string.recovery_retry)
            setOnClickListener { retryAction() }
        }
    }
    val reselect by lazy {
        action(R.id.recoveryReselect).apply { setOnClickListener { reselectAction() } }
    }
    private lateinit var buttons: BlockButtonStyler

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), dp(2), dp(4), dp(2))
        isVisible = false
    }

    fun bindActions(onRetry: () -> Unit, onReselect: () -> Unit) {
        retryAction = onRetry
        reselectAction = onReselect
    }

    private fun buildContents() {
        addView(text, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(retry, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(reselect, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(4)
        })
        buttons = BlockButtonStyler(this, preserveMargins = true)
        installPlayerInputFocus()
        ViewCompat.setAccessibilityLiveRegion(text, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
        contentsBuilt = true
    }

    fun render(message: CharSequence?, busy: Boolean = false, reselectLabel: Int? = null) {
        isVisible = message != null
        if (message == null) return
        if (!contentsBuilt) buildContents()
        if (text.text != message) text.text = message
        retry.isEnabled = !busy
        reselect.isVisible = reselectLabel != null
        reselect.isEnabled = !busy
        reselectLabel?.let(reselect::setText)
        val display = (context.applicationContext as MaCarMusicApp).displaySettings.options.value
        buttons.apply(display.controlBlocks, display.blockOutline)
        val foreground = AccentTheme.color(this, com.google.android.material.R.attr.colorOnErrorContainer)
        setBackgroundColor(AccentTheme.color(this, com.google.android.material.R.attr.colorErrorContainer))
        text.setTextColor(foreground)
        listOf(retry, reselect).forEach {
            it.setTextColor(foreground)
            it.strokeColor = ColorStateList.valueOf(foreground)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (contentsBuilt && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            val width = ((MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight) / 3)
                .coerceAtLeast(dp(48))
            if (retry.maxWidth != width) retry.maxWidth = width
            if (reselect.maxWidth != width) reselect.maxWidth = width
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
}
