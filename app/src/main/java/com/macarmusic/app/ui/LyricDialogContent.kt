package com.macarmusic.app.ui

import android.view.View
import android.widget.FrameLayout

internal fun lyricDialogContent(content: View, heightDp: Int): FrameLayout =
    object : FrameLayout(content.context) {
        private val preferredHeight = (heightDp * resources.displayMetrics.density + 0.5f).toInt()

        init {
            addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            // The scrolling viewport must fit the space left by large titles and system buttons.
            val height = resolveSize(preferredHeight, heightMeasureSpec)
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        }
    }
