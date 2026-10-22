package com.macarmusic.app.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatDialog
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.children
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.button.MaterialButton
import com.macarmusic.app.MaCarMusicApp
import com.macarmusic.app.R

class PlayerDialog(context: Context) : AppCompatDialog(context, R.style.ThemeOverlay_ManaPlayer_Panel) {
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density + 0.5f).toInt()
    private val owner = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<LifecycleOwner>().firstOrNull()
    private val cleanup = object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) = dismiss()
    }
    private val heading = AppCompatTextView(this.context).apply {
        id = R.id.playerDialogTitle
        textSize = 22f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(AccentTheme.color(this, com.google.android.material.R.attr.colorOnSurface))
        maxLines = 3
        ellipsize = TextUtils.TruncateAt.END
        setPadding(0, 0, 0, dp(12))
        visibility = View.GONE
    }
    private val message = AppCompatTextView(this.context).apply {
        id = android.R.id.message
        textSize = 18f
        setTextColor(AccentTheme.color(this, com.google.android.material.R.attr.colorOnSurface))
    }
    private val body = FrameLayout(this.context)
    internal val actions = PlayerDialogActions(this.context)
    private val buttons = listOf(DialogInterface.BUTTON_NEUTRAL, DialogInterface.BUTTON_NEGATIVE,
        DialogInterface.BUTTON_POSITIVE).associateWith { which ->
        MaterialButton(this.context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            id = when (which) {
                DialogInterface.BUTTON_POSITIVE -> android.R.id.button1
                DialogInterface.BUTTON_NEGATIVE -> android.R.id.button2
                else -> android.R.id.button3
            }
            isAllCaps = false
            textSize = 16f
            minHeight = dp(48)
            minWidth = dp(64)
            insetTop = 0
            insetBottom = 0
            setSingleLine(false)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            visibility = View.GONE
            actions.addView(this, ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }
    internal val buttonStyler by lazy { BlockButtonStyler(actions, preserveMargins = true) }
    val listView: ListView by lazy {
        ListView(this.context).apply {
            id = R.id.playerDialogList
            divider = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            dividerHeight = dp(6)
            setSelector(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            clipToPadding = false
        }
    }
    private val panel = PlayerDialogLayout(this.context).apply {
        id = R.id.playerDialogPanel
        setPadding(dp(16), dp(16), dp(16), dp(16))
        addView(heading)
        addView(body)
        addView(actions)
    }

    init {
        setCanceledOnTouchOutside(true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(panel)
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun onStart() {
        super.onStart()
        val options = (context.applicationContext as MaCarMusicApp).displaySettings.options.value
        panel.background = GradientDrawable().apply {
            setColor(AccentTheme.color(context, com.google.android.material.R.attr.colorSurface))
            cornerRadius = dp(if (options.controlBlocks && options.blockOutline.hasArc) options.blockOutline.arcDp else 6).toFloat()
        }
        panel.foreground = if (options.controlBlocks) PlayerBlockDrawable(options.blockOutline,
            AccentTheme.color(context, com.google.android.material.R.attr.colorOutline), context.resources.displayMetrics.density) else null
        buttonStyler.apply(options.controlBlocks, options.blockOutline)
        AccentTheme.tintControls(panel)
        window?.setLayout(minOf(dp(560), context.resources.displayMetrics.widthPixels - dp(32)),
            ViewGroup.LayoutParams.WRAP_CONTENT)
        owner?.lifecycle?.addObserver(cleanup)
    }

    override fun dismiss() {
        owner?.lifecycle?.removeObserver(cleanup)
        super.dismiss()
    }

    override fun setTitle(title: CharSequence?) {
        super.setTitle(title)
        heading.text = title
        heading.visibility = if (title.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    override fun setTitle(titleId: Int) = setTitle(context.getText(titleId))

    fun setMessage(value: CharSequence) {
        message.text = value
        if (message.parent == null) setBody(ScrollView(context).apply { addView(message) })
    }

    fun getButton(which: Int): Button = buttons.getValue(which)

    private fun setBody(view: View) {
        body.removeAllViews()
        body.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun button(which: Int, label: CharSequence, listener: DialogInterface.OnClickListener?) {
        buttons.getValue(which).apply {
            text = label
            visibility = View.VISIBLE
            setOnClickListener {
                listener?.onClick(this@PlayerDialog, which)
                dismiss()
            }
        }
    }

    private fun choices(labels: Array<out CharSequence>, mode: Int, selected: Int = -1, checked: BooleanArray? = null,
        click: DialogInterface.OnClickListener? = null, toggle: DialogInterface.OnMultiChoiceClickListener? = null,
    ) {
        require(checked == null || checked.size == labels.size)
        listView.choiceMode = mode
        listView.adapter = object : BaseAdapter() {
            override fun getCount() = labels.size
            override fun getItem(position: Int) = labels[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                ((convertView as? PlayerDialogChoice) ?: PlayerDialogChoice(context, mode)).apply {
                    text = labels[position]
                    isChecked = listView.isItemChecked(position)
                }
        }
        if (selected in labels.indices) listView.setItemChecked(selected, true)
        checked?.forEachIndexed { index, value -> listView.setItemChecked(index, value) }
        listView.setOnItemClickListener { _, _, index, _ ->
            if (mode == ListView.CHOICE_MODE_MULTIPLE) {
                val value = listView.isItemChecked(index)
                checked?.set(index, value)
                toggle?.onClick(this, index, value)
            } else {
                if (mode == ListView.CHOICE_MODE_NONE) dismiss()
                click?.onClick(this, index)
            }
        }
        setBody(listView)
        if (selected in labels.indices) listView.setSelection(selected)
    }

    class Builder(private val context: Context) {
        private val dialog = PlayerDialog(context)
        fun setTitle(value: Int) = apply { dialog.setTitle(value) }
        fun setTitle(value: CharSequence) = apply { dialog.setTitle(value) }
        fun setMessage(value: Int) = setMessage(context.getText(value))
        fun setMessage(value: CharSequence) = apply { dialog.setMessage(value) }
        fun setView(view: View) = apply { dialog.setBody(view) }
        fun setCancelable(value: Boolean) = apply { dialog.setCancelable(value) }
        fun setOnCancelListener(listener: DialogInterface.OnCancelListener) = apply { dialog.setOnCancelListener(listener) }
        fun setPositiveButton(label: Int, listener: DialogInterface.OnClickListener?) =
            setPositiveButton(context.getText(label), listener)
        fun setPositiveButton(label: CharSequence, listener: DialogInterface.OnClickListener?) =
            apply { dialog.button(DialogInterface.BUTTON_POSITIVE, label, listener) }
        fun setNegativeButton(label: Int, listener: DialogInterface.OnClickListener?) =
            setNegativeButton(context.getText(label), listener)
        fun setNegativeButton(label: CharSequence, listener: DialogInterface.OnClickListener?) =
            apply { dialog.button(DialogInterface.BUTTON_NEGATIVE, label, listener) }
        fun setNeutralButton(label: Int, listener: DialogInterface.OnClickListener?) =
            apply { dialog.button(DialogInterface.BUTTON_NEUTRAL, context.getText(label), listener) }
        fun setItems(items: Array<out CharSequence>, listener: DialogInterface.OnClickListener) =
            apply { dialog.choices(items, ListView.CHOICE_MODE_NONE, click = listener) }
        fun setSingleChoiceItems(items: Array<out CharSequence>, selected: Int, listener: DialogInterface.OnClickListener) =
            apply { dialog.choices(items, ListView.CHOICE_MODE_SINGLE, selected, click = listener) }
        fun setMultiChoiceItems(items: Array<out CharSequence>, selected: BooleanArray,
            listener: DialogInterface.OnMultiChoiceClickListener,
        ) = apply { dialog.choices(items, ListView.CHOICE_MODE_MULTIPLE, checked = selected, toggle = listener) }
        fun create() = dialog
        fun show() = dialog.also { it.show() }
    }
}

private class PlayerDialogLayout(context: Context) : ViewGroup(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val screenLimit = ((resources.configuration.screenHeightDp - 24) * resources.displayMetrics.density).toInt()
        val limit = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) screenLimit
            else minOf(screenLimit, MeasureSpec.getSize(heightMeasureSpec))
        val childWidth = MeasureSpec.makeMeasureSpec((width - paddingLeft - paddingRight).coerceAtLeast(0), MeasureSpec.EXACTLY)
        var remaining = (limit - paddingTop - paddingBottom).coerceAtLeast(0)
        for (index in intArrayOf(0, 2, 1)) {
            val child = getChildAt(index)
            if (child.visibility == GONE) continue
            child.measure(childWidth, MeasureSpec.makeMeasureSpec(remaining, MeasureSpec.AT_MOST))
            remaining = (remaining - child.measuredHeight).coerceAtLeast(0)
        }
        setMeasuredDimension(width, limit - remaining)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        var y = paddingTop
        for (child in children) if (child.visibility != GONE) {
            child.layout(paddingLeft, y, paddingLeft + child.measuredWidth, y + child.measuredHeight)
            y += child.measuredHeight
        }
    }

    override fun generateDefaultLayoutParams() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
}

internal class PlayerDialogActions(context: Context) : ViewGroup(context) {
    private val gap = (8 * resources.displayMetrics.density + 0.5f).toInt()
    private val rows = mutableListOf<List<View>>()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        rows.clear()
        var row = mutableListOf<View>()
        var used = 0
        for (child in children) if (child.visibility != GONE) {
            child.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            val spacing = if (row.isEmpty()) 0 else gap
            if (row.isNotEmpty() && used + spacing + child.measuredWidth > width) {
                rows.add(row)
                row = mutableListOf()
                used = 0
            }
            used += (if (row.isEmpty()) 0 else gap) + child.measuredWidth
            row.add(child)
        }
        if (row.isNotEmpty()) rows.add(row)
        val height = rows.sumOf { it.maxOf(View::getMeasuredHeight) + gap }
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        var y = gap
        for (row in rows) {
            var x = width - row.sumOf(View::getMeasuredWidth) - gap * (row.size - 1)
            val height = row.maxOf(View::getMeasuredHeight)
            for (child in row) {
                child.layout(x, y, x + child.measuredWidth, y + height)
                x += child.measuredWidth + gap
            }
            y += height + gap
        }
    }
}
