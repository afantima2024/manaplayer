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
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.Checkable
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ListView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatDialog
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.children
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.macarmusic.app.MaCarMusicApp
import com.macarmusic.app.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PlayerDialog(context: Context) : AppCompatDialog(context, R.style.ThemeOverlay_ManaPlayer_Panel) {
    private var focusMemory: PlayerFocusMemory? = null
    private val focusPositions = Bundle()
    private var preferredFocus: View? = null
    private var preferredChoice: Int? = null
    private var transientNoticesEnabled = true
    private var noticeJob: Job? = null
    private val notice by lazy { PlayerNoticeView(this.context) }
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
        visibility = View.GONE
    }
    private val logo = androidx.appcompat.widget.AppCompatImageView(this.context).apply {
        id = R.id.playerDialogLogo
        scaleType = ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val header = LinearLayout(this.context).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, 0, dp(12))
        addView(logo, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(12) })
        addView(heading, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }
    private val message = AppCompatTextView(this.context).apply {
        id = android.R.id.message
        textSize = 18f
        setTextColor(AccentTheme.color(this, com.google.android.material.R.attr.colorOnSurface))
    }
    private val body = FrameLayout(this.context)
    internal val actions = PlayerDialogActions(this.context)
    private fun actionButton() =
        MaterialButton(this.context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            isAllCaps = false
            textSize = 16f
            minHeight = dp(48)
            minWidth = dp(64)
            insetTop = 0
            insetBottom = 0
            setSingleLine(false)
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }

    private val buttons = listOf(DialogInterface.BUTTON_NEUTRAL, DialogInterface.BUTTON_NEGATIVE,
        DialogInterface.BUTTON_POSITIVE).associateWith { which ->
        actionButton().apply {
            id = when (which) {
                DialogInterface.BUTTON_POSITIVE -> android.R.id.button1
                DialogInterface.BUTTON_NEGATIVE -> android.R.id.button2
                else -> android.R.id.button3
            }
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
            selector = playerFocusForeground()
            setDrawSelectorOnTop(true)
            clipToPadding = false
        }
    }
    private val panel = PlayerDialogLayout(this.context).apply {
        id = R.id.playerDialogPanel
        setPadding(dp(16), dp(16), dp(16), dp(16))
        addView(header)
        addView(body)
        addView(actions)
    }

    init {
        setCanceledOnTouchOutside(true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(context).apply {
            addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(notice, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP).apply { setMargins(dp(8), dp(8), dp(8), 0) })
        })
        body.installPlayerInputFocus()
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    override fun onStart() {
        super.onStart()
        if (focusMemory == null) focusMemory = PlayerFocusMemory(panel, focusPositions).also { it.scope(panel, "dialog") }
        val options = (context.applicationContext as MaCarMusicApp).displaySettings.options.value
        logo.setImageResource(Branding.icon(options.appLogo))
        panel.background = GradientDrawable().apply {
            setColor(AccentTheme.color(context, com.google.android.material.R.attr.colorSurface))
            cornerRadius = dp(if (options.controlBlocks && options.blockOutline.hasArc) options.blockOutline.arcDp else 6).toFloat()
        }
        panel.foreground = if (options.controlBlocks) PlayerBlockDrawable(options.blockOutline,
            AccentTheme.color(context, com.google.android.material.R.attr.colorOutline), context.resources.displayMetrics.density) else null
        buttonStyler.apply(options.controlBlocks, options.blockOutline)
        AccentTheme.tintControls(panel, readableOnSurface = true)
        window?.setLayout(minOf(dp(if (actions.leadingActions > 0) 960 else 560),
            context.resources.displayMetrics.widthPixels - dp(32)),
            ViewGroup.LayoutParams.WRAP_CONTENT)
        active.add(this)
        owner?.lifecycle?.addObserver(cleanup)
        if (transientNoticesEnabled) noticeJob = owner?.lifecycleScope?.launch {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                (context.applicationContext as MaCarMusicApp).notice.collect(notice::render)
            }
        }
        focusMemory?.apply {
            if (!hasPosition(panel)) remember(preferredFocus ?: selectedControl(body), preferredChoice)
            activate(panel)
        }
    }

    override fun dismiss() {
        noticeJob?.cancel()
        noticeJob = null
        focusMemory?.dispose()
        focusMemory = null
        active.remove(this)
        owner?.lifecycle?.removeObserver(cleanup)
        super.dismiss()
    }

    fun focusOn(view: View) {
        preferredFocus = view
        focusMemory?.apply {
            pause()
            remember(view)
            activate(panel)
        }
    }

    internal fun initialItemFocus(list: androidx.recyclerview.widget.RecyclerView, key: String, controlId: Int) {
        focusMemory?.initialItem(panel, list, key, controlId)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) focusMemory?.windowFocused()
    }

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        focusMemory?.touch(event)
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (focusMemory?.key(event) == true) return true
        return super.dispatchKeyEvent(event)
    }

    private fun selectedControl(view: View): View? {
        if (view.visibility != View.VISIBLE || !view.isEnabled) return null
        if (view.isFocusable && ((view as? Checkable)?.isChecked == true || view.isSelected)) return view
        if (view is ViewGroup) for (child in view.children) selectedControl(child)?.let { return it }
        return null
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

    fun makeMessageSelectable() {
        val scroll = message.parent as ScrollView
        val close = getButton(DialogInterface.BUTTON_POSITIVE)
        message.setTextIsSelectable(true)
        message.installPlayerInputFocus()
        close.nextFocusUpId = message.id
        message.setOnKeyListener { _, key, event ->
            if (event.action != KeyEvent.ACTION_DOWN || !event.hasNoModifiers()) return@setOnKeyListener false
            when (key) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> close.requestFocus()
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                    val direction = if (key == KeyEvent.KEYCODE_DPAD_DOWN) 1 else -1
                    if (scroll.canScrollVertically(direction)) {
                        scroll.scrollBy(0, direction * (scroll.height / 2).coerceAtLeast(1))
                        true
                    } else close.requestFocus()
                }
                else -> false
            }
        }
        focusOn(close)
    }

    fun getButton(which: Int): Button = buttons.getValue(which)

    private fun selectionActions(selectAll: () -> Unit, deselectAll: () -> Unit) {
        check(actions.leadingActions == 0 && listView.choiceMode == ListView.CHOICE_MODE_MULTIPLE)
        for ((index, choice) in listOf(
            Triple(R.id.playerDialogSelectAll, R.string.select_all, selectAll),
            Triple(R.id.playerDialogDeselectAll, R.string.deselect_all, deselectAll),
        ).withIndex()) {
            val (id, label, action) = choice
            actions.addView(actionButton().apply {
                this.id = id
                setText(label)
                setOnClickListener { action() }
            }, index, ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        actions.leadingActions = 2
        actions.children.filterIsInstance<MaterialButton>().forEach { it.setSingleLine(true) }
        panel.removeView(actions)
        panel.addView(HorizontalScrollView(context).apply {
            isFillViewport = true
            addView(actions, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
    }

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
            focusMemory?.remember(listView, index)
            if (mode == ListView.CHOICE_MODE_MULTIPLE) {
                val value = listView.isItemChecked(index)
                checked?.set(index, value)
                toggle?.onClick(this, index, value)
            } else {
                if (mode == ListView.CHOICE_MODE_NONE) dismiss()
                click?.onClick(this, index)
            }
        }
        listView.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (listView.hasFocus()) focusMemory?.remember(listView, position)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        setBody(listView)
        preferredChoice = selected.takeIf { it in labels.indices }
            ?: checked?.indexOfFirst { it }?.takeIf { it >= 0 } ?: 0.takeIf { labels.isNotEmpty() }
        preferredChoice?.let {
            preferredFocus = listView
            listView.setSelection(it)
        }
    }

    class Builder(private val context: Context) {
        private val dialog = PlayerDialog(context)
        fun setTitle(value: Int) = apply { dialog.setTitle(value) }
        fun setTitle(value: CharSequence) = apply { dialog.setTitle(value) }
        fun setMessage(value: Int) = setMessage(context.getText(value))
        fun setMessage(value: CharSequence) = apply { dialog.setMessage(value) }
        fun setView(view: View) = apply { dialog.setBody(view) }
        fun setCancelable(value: Boolean) = apply { dialog.setCancelable(value) }
        internal fun setTransientNoticesEnabled(value: Boolean) = apply { dialog.transientNoticesEnabled = value }
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
        fun setSelectionActions(selectAll: () -> Unit, deselectAll: () -> Unit) =
            apply { dialog.selectionActions(selectAll, deselectAll) }
        fun create() = dialog
        fun show() = dialog.also { it.show() }
    }

    companion object {
        private val active = mutableSetOf<PlayerDialog>()

        internal fun dismissOwned(owner: LifecycleOwner) {
            active.filter { it.owner === owner }.forEach { it.dismiss() }
        }

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
        // Reserve heading and footer height before measuring the scrollable body.
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
    var leadingActions = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        rows.clear()
        if (leadingActions > 0) {
            val row = children.filter { it.visibility != GONE }.toList()
            for (child in row) child.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            rows.add(row)
            setMeasuredDimension(resolveSize(row.sumOf(View::getMeasuredWidth) + gap * (row.size - 1), widthMeasureSpec),
                resolveSize((row.maxOfOrNull(View::getMeasuredHeight) ?: 0) + gap, heightMeasureSpec))
            return
        }
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
            val remaining = (width - row.sumOf(View::getMeasuredWidth) - gap * (row.size - 1)).coerceAtLeast(0)
            var x = if (leadingActions > 0) 0 else remaining
            val height = row.maxOf(View::getMeasuredHeight)
            for ((index, child) in row.withIndex()) {
                if (index == leadingActions && leadingActions > 0) x += remaining
                child.layout(x, y, x + child.measuredWidth, y + height)
                x += child.measuredWidth + gap
            }
            y += height + gap
        }
    }
}
