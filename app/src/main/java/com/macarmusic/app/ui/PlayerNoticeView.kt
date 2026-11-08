package com.macarmusic.app.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.isVisible
import com.google.android.material.color.MaterialColors
import com.macarmusic.app.MaCarMusicApp
import com.macarmusic.app.model.Notice
import com.macarmusic.app.R
import com.macarmusic.app.databinding.ViewNoticeBinding

class PlayerNoticeView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {
    private val app get() = context.applicationContext as MaCarMusicApp
    private val binding = ViewNoticeBinding.inflate(LayoutInflater.from(context), this, true)
    private var shown: Notice? = null
    val text get() = binding.noticeText

    init {
        isVisible = false
        binding.root.isVisible = false
        elevation = 8 * resources.displayMetrics.density
        binding.root.installPlayerInputFocus()
        binding.dismissNotice.setOnClickListener { shown?.let { app.dismissNotice(it.id) } }
        binding.noticeText.setOnClickListener {
            shown?.let { notice ->
                AccentTheme.dialog(context).setTitle(R.string.details).setMessage(notice.text)
                    .setTransientNoticesEnabled(false).setPositiveButton(R.string.close, null).show()
            }
        }
    }

    fun render(notice: Notice?) {
        shown = notice
        isVisible = notice != null
        binding.root.isVisible = notice != null
        binding.noticeText.text = notice?.text
        TooltipCompat.setTooltipText(binding.noticeText, notice?.text)
        if (notice == null) return
        val background = MaterialColors.getColor(this,
            if (notice.isError) com.google.android.material.R.attr.colorErrorContainer
            else com.google.android.material.R.attr.colorPrimaryContainer)
        val foreground = MaterialColors.getColor(this,
            if (notice.isError) com.google.android.material.R.attr.colorOnErrorContainer
            else com.google.android.material.R.attr.colorOnPrimaryContainer)
        binding.root.setBackgroundColor(background)
        binding.noticeText.setTextColor(foreground)
        binding.noticeLogo.setImageResource(Branding.icon(context))
        PlayerIcons.image(binding.dismissNotice, R.drawable.ic_close, foreground)
    }
}
