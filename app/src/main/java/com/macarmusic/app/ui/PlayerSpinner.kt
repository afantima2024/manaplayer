package com.macarmusic.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.view.SoundEffectConstants
import android.view.accessibility.AccessibilityEvent
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.widget.AppCompatSpinner
import androidx.core.view.children
import com.macarmusic.app.R

class PlayerSpinner @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AppCompatSpinner(context, attrs, androidx.appcompat.R.attr.spinnerStyle, Spinner.MODE_DIALOG) {
    private var choices: PlayerDialog? = null

    @SuppressLint("ClickableViewAccessibility") // Calling Spinner.performClick would also open its platform popup.
    override fun performClick(): Boolean {
        if (!isEnabled || choices?.isShowing == true) return true
        val source = adapter ?: return false
        if (source.count == 0) return false
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED)
        playSoundEffect(SoundEffectConstants.CLICK)
        val label = (parent as? ViewGroup)?.children?.filterIsInstance<TextView>()?.firstOrNull { it.labelFor == id }?.text
        choices = AccentTheme.dialog(context)
            .setTitle(prompt ?: contentDescription ?: label ?: "")
            .setSingleChoiceItems(Array(source.count) { source.getItem(it).toString() }, selectedItemPosition) { dialog, index ->
                setSelection(index)
                dialog.dismiss()
            }.setNegativeButton(R.string.close, null).create().also { dialog ->
                dialog.setOnDismissListener { if (choices === dialog) choices = null }
                dialog.show()
            }
        return true
    }

    override fun onDetachedFromWindow() {
        choices?.dismiss()
        choices = null
        super.onDetachedFromWindow()
    }
}
