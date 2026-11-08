package com.macarmusic.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.macarmusic.app.R
import com.macarmusic.app.model.BlockOutline
import com.macarmusic.app.radio.RadioProgram
import com.macarmusic.app.MaCarMusicApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.math.roundToInt

class RadioButtonAdapter(
    private val scope: CoroutineScope,
    private val onPlay: (RadioProgram) -> Unit,
    private val onOptions: (RadioProgram) -> PlayerDialog?,
    private val onReorder: (List<String>, List<String>) -> Boolean,
    private val compact: Boolean = false,
) : RecyclerView.Adapter<RadioButtonAdapter.Holder>(), PlayerFocusItems {
    override fun focusKey(position: Int) = "pin:${getItem(position).id}"
    class Holder(val button: MaterialButton) : RecyclerView.ViewHolder(button) {
        val styler = BlockButtonStyler(button, preserveMargins = true)
        var logoJob: Job? = null
        var logoLoaded = false
    }

    private var selected: String? = null
    private var favorites = emptySet<String>()
    private var pinColor = 0
    private var highlightColor: Int? = null
    private var positions = emptyMap<String, Int>()
    private var blocks = false to BlockOutline()
    private var logoVersion = -1L
    private var buttonLogo = com.macarmusic.app.radio.RadioButtonLogo.BRANDED_RADIO
    private var stationLogos = emptyMap<String, com.macarmusic.app.radio.RadioStationLogo>()
    private val warnedLogos = hashSetOf<String>()
    private var programs = emptyList<RadioProgram>()
    val currentList: List<RadioProgram> get() = programs
    private var dragOriginal: List<RadioProgram>? = null
    private var pendingList: List<RadioProgram>? = null
    private var moved = false
    private var detaching = false
    private var dragCancelled = false
    private var touchHelper: ItemTouchHelper? = null
    private var heldHolder: Holder? = null
    private var heldDialog: PlayerDialog? = null
    private var heldX = 0f
    private var heldY = 0f

    override fun getItemCount() = programs.size
    private fun getItem(position: Int) = programs[position]

    fun submitList(next: List<RadioProgram>) {
        if (dragOriginal != null) {
            pendingList = next
            return
        }
        if (next == programs) return
        if (heldHolder?.button?.tag?.let { id -> next.none { it.id == id } } == true) finishHeldOptions(cancelled = true)
        val previous = programs
        val result = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = previous.size
            override fun getNewListSize() = next.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                previous[oldItemPosition].id == next[newItemPosition].id
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                previous[oldItemPosition] == next[newItemPosition]
        })
        programs = next.toList()
        indexPositions()
        result.dispatchUpdatesTo(this)
    }

    private fun indexPositions() { positions = programs.withIndex().associate { it.value.id to it.index } }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        (recyclerView as? RadioPinList)?.apply {
            val slop = ViewConfiguration.get(context).scaledTouchSlop
            onTouchCancelled = { dragCancelled = true }
            onTouchMoved = { x, y ->
                val holder = heldHolder
                if (holder != null && itemCount > 1 && touchActive && dragOriginal == null &&
                    (kotlin.math.abs(x - heldX) > slop || kotlin.math.abs(y - heldY) > slop)) {
                    finishHeldOptions(cancelled = true)
                    dragOriginal = programs
                    pendingList = null
                    moved = false
                    dragCancelled = false
                    touchHelper?.startDrag(holder)
                }
            }
            onTouchFinished = ::finishHeldOptions
        }
        touchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
            override fun isLongPressDragEnabled() = false
            override fun isItemViewSwipeEnabled() = false
            override fun getMovementFlags(view: RecyclerView, holder: RecyclerView.ViewHolder): Int =
                if (holder.bindingAdapter === this@RadioButtonAdapter && itemCount > 1)
                    makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0)
                else 0

            override fun canDropOver(view: RecyclerView, current: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) =
                target.bindingAdapter === this@RadioButtonAdapter

            override fun onMove(view: RecyclerView, source: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                if (source.bindingAdapter !== this@RadioButtonAdapter || target.bindingAdapter !== this@RadioButtonAdapter) return false
                val from = source.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from !in programs.indices || to !in programs.indices || from == to) return false
                programs = programs.toMutableList().apply { add(to, removeAt(from)) }
                indexPositions()
                moved = true
                notifyItemMoved(from, to)
                return true
            }

            override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun clearView(view: RecyclerView, holder: RecyclerView.ViewHolder) {
                super.clearView(view, holder)
                finishDrag(view)
            }
        }).also { it.attachToRecyclerView(recyclerView) }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        detaching = true
        finishHeldOptions(cancelled = true)
        (recyclerView as? RadioPinList)?.apply {
            onTouchCancelled = null
            onTouchMoved = null
            onTouchFinished = null
        }
        touchHelper?.attachToRecyclerView(null)
        touchHelper = null
        finishDrag(recyclerView)
        detaching = false
        super.onDetachedFromRecyclerView(recyclerView)
    }

    private fun finishDrag(view: RecyclerView) {
        val original = dragOriginal ?: return
        val orderedIds = programs.map { it.id }
        val visible = !detaching && !dragCancelled && view.isAttachedToWindow && view.isShown && view.hasWindowFocus()
        val accepted = moved && visible && onReorder(original.map { it.id }, orderedIds)
        val latest = pendingList ?: original
        dragOriginal = null
        pendingList = null
        if (accepted) {
            val byId = latest.associateBy { it.id }
            val ids = orderedIds.toSet()
            submitList(orderedIds.mapNotNull(byId::get) + latest.filter { it.id !in ids })
        } else submitList(latest)
    }

    private fun showOptions(holder: Holder, station: RadioProgram) {
        finishHeldOptions(cancelled = true)
        val dialog = onOptions(station) ?: return
        val list = holder.button.parent as? RadioPinList
        if (list?.touchActive == true || (holder.button as? RadioPinButton)?.confirmHeld == true) {
            heldHolder = holder
            heldDialog = dialog
            heldX = list?.touchX ?: 0f
            heldY = list?.touchY ?: 0f
            // Keep the original DOWN/UP sequence in the grid, not in the newly visible menu.
            dialog.window?.addFlags(HELD_MENU_FLAGS)
        }
        dialog.show()
    }

    private fun finishHeldOptions(cancelled: Boolean) {
        val dialog = heldDialog ?: return
        val button = heldHolder?.button
        heldDialog = null
        heldHolder = null
        if (cancelled || button == null) {
            dialog.dismiss()
            return
        }
        button.post {
            if (dialog.isShowing && button.isAttachedToWindow && button.isShown && button.hasWindowFocus()) {
                dialog.window?.clearFlags(HELD_MENU_FLAGS)
            } else dialog.dismiss()
        }
    }

    fun logosChanged(version: Long) {
        if (logoVersion == version) return
        logoVersion = version
        notifyItemRangeChanged(0, itemCount, LOGO)
    }

    fun configureLogo(logo: com.macarmusic.app.radio.RadioButtonLogo,
        overrides: Map<String, com.macarmusic.app.radio.RadioStationLogo>) {
        if (buttonLogo == logo && stationLogos == overrides) return
        buttonLogo = logo
        stationLogos = overrides
        notifyItemRangeChanged(0, itemCount, LOGO)
    }

    fun selection(id: String?, pinnedIds: List<String>, color: Int, highlight: Int?) {
        val favoriteIds = pinnedIds.toSet()
        if (selected == id && favorites == favoriteIds && pinColor == color && highlightColor == highlight) return
        val changed = mutableSetOf<String>()
        if (selected != id) {
            selected?.let(changed::add)
            id?.let(changed::add)
        }
        changed.addAll(favorites - favoriteIds)
        changed.addAll(favoriteIds - favorites)
        if (color != pinColor) changed.addAll(favoriteIds)
        if (highlight != highlightColor) id?.let(changed::add)
        selected = id
        favorites = favoriteIds
        pinColor = color
        highlightColor = highlight
        changed.forEach { key -> positions[key]?.let { notifyItemChanged(it, STATE) } }
    }

    fun configureBlocks(enabled: Boolean, outline: BlockOutline) {
        if (blocks == (enabled to outline)) return
        blocks = enabled to outline
        notifyItemRangeChanged(0, itemCount, STATE)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val context = parent.context
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).roundToInt()
        val button = RadioPinButton(context).apply {
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            isAllCaps = false
            isFocusable = true
            gravity = Gravity.CENTER
            textSize = if (compact) 13f else 16f
            minLines = if (compact) 1 else 2
            maxLines = if (compact) 2 else 3
            ellipsize = TextUtils.TruncateAt.END
            minHeight = dp(if (compact) 56 else 104)
            minimumWidth = dp(48)
            setPaddingRelative(dp(8), dp(if (compact) 4 else 8), dp(8), dp(if (compact) 4 else 8))
            insetTop = 0
            insetBottom = 0
            iconGravity = if (compact) MaterialButton.ICON_GRAVITY_TEXT_START else MaterialButton.ICON_GRAVITY_TEXT_TOP
            iconSize = dp(if (compact) 28 else 40)
            iconPadding = dp(4)
        }
        return Holder(button).also { holder ->
            fun station() = holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let(::getItem)
            button.setOnClickListener { station()?.let(onPlay) }
            button.setOnLongClickListener {
                station()?.let { showOptions(holder, it) }
                true
            }
            button.onHoldFinished = { cancelled ->
                if (heldHolder === holder) finishHeldOptions(cancelled)
            }
            button.setOnKeyListener { _, key, event ->
                if (key == KeyEvent.KEYCODE_MENU && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    station()?.let { showOptions(holder, it) }
                    true
                } else false
            }
            ViewCompat.replaceAccessibilityAction(button, AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK,
                context.getString(R.string.radio_button_options)) { _, _ ->
                station()?.let { showOptions(holder, it) }
                true
            }
        }
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.button.setTag(R.id.playerFocusKey, focusKey(position))
        bindLogo(holder, getItem(position))
        bindState(holder, getItem(position))
    }

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) onBindViewHolder(holder, position) else {
            if (LOGO in payloads) bindLogo(holder, getItem(position))
            bindState(holder, getItem(position))
        }
    }

    override fun onViewRecycled(holder: Holder) {
        if (heldHolder === holder) finishHeldOptions(cancelled = true)
        (holder.button as? RadioPinButton)?.cancelConfirm()
        holder.logoJob?.cancel()
        holder.logoJob = null
        super.onViewRecycled(holder)
    }

    private fun bindLogo(holder: Holder, station: RadioProgram) {
        holder.logoJob?.cancel()
        holder.logoLoaded = buttonLogo == com.macarmusic.app.radio.RadioButtonLogo.BRANDED_RADIO
        holder.button.setIconResource(Branding.radio(buttonLogo))
        holder.button.iconTint = if (holder.logoLoaded) null else
            ColorStateList.valueOf(AccentTheme.color(holder.button, com.google.android.material.R.attr.colorPrimary))
        val app = holder.button.context.applicationContext as MaCarMusicApp
        holder.logoJob = scope.launch {
            try {
                app.radioLogos.cached(station)?.let {
                    holder.logoLoaded = true
                    holder.button.icon = BitmapDrawable(holder.button.resources, it)
                    holder.button.iconTint = null
                }
            } catch (error: IOException) {
                android.util.Log.w("RadioButtons", "Cannot read cached radio logo", error)
                if (warnedLogos.add(station.imageUri)) app.report(app.text(R.string.radio_logo_cache_failed))
            }
        }
    }

    private fun bindState(holder: Holder, station: RadioProgram) {
        val button = holder.button
        if (button.tag != null && button.tag != station.id) {
            if (heldHolder === holder) finishHeldOptions(cancelled = true)
            (button as? RadioPinButton)?.cancelConfirm()
        }
        val context = button.context
        val active = station.id == selected
        val favorite = station.id in favorites
        val title = station.title(context)
        val highlight = highlightColor?.takeIf { active }
        holder.styler.apply(blocks.first, blocks.second, highlight)
        button.text = title
        button.contentDescription = title
        button.tag = station.id
        button.isSelected = active
        ViewCompat.setStateDescription(button, listOfNotNull(
            context.getString(R.string.radio_selected_station).takeIf { active },
            context.getString(R.string.radio_pinned).takeIf { favorite },
        ).joinToString(", ").ifEmpty { null })
        button.typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        val ink = if (highlight != null) AccentTheme.primaryControlInk(button, highlight) else if (favorite) pinColor
            else MaterialColors.getColor(button, com.google.android.material.R.attr.colorOnSurface)
        button.setTextColor(ink)
        button.iconTint = if (!holder.logoLoaded)
            ColorStateList.valueOf(if (active || favorite) ink
                else AccentTheme.color(button, com.google.android.material.R.attr.colorPrimary)) else null
    }

    companion object {
        private val STATE = Any()
        private val LOGO = Any()
        private const val HELD_MENU_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        fun configureGrid(view: RecyclerView, tileCount: (() -> Int)? = null, isChannel: ((Int) -> Boolean)? = null) {
            view.layoutManager = RadioGridLayoutManager(view.context, isChannel != null).apply {
                if (tileCount != null) spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    init {
                        isSpanIndexCacheEnabled = true
                        isSpanGroupIndexCacheEnabled = true
                    }
                    override fun getSpanSize(position: Int): Int {
                        val tiles = tileCount()
                        return when {
                            position < tiles -> columns.tileSpan
                            isChannel?.invoke(position - tiles) == true -> columns.channelSpan
                            else -> spanCount
                        }
                    }
                }
            }
            view.itemAnimator = null
            view.clipToPadding = false
            val inset = (4 * view.resources.displayMetrics.density).roundToInt()
            view.setPadding(inset, inset, inset, inset)
            view.addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                    outRect.set(inset, inset, inset, inset)
                }
            })
        }
    }
}

internal class RadioPinList(context: Context) : RecyclerView(context) {
    var touchActive = false
        private set
    var onTouchCancelled: (() -> Unit)? = null
    var onTouchMoved: ((Float, Float) -> Unit)? = null
    var onTouchFinished: ((Boolean) -> Unit)? = null
    var touchX = 0f
        private set
    var touchY = 0f
        private set

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        touchX = event.x
        touchY = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> touchActive = true
            MotionEvent.ACTION_MOVE -> onTouchMoved?.invoke(event.x, event.y)
            MotionEvent.ACTION_UP -> touchActive = false
            MotionEvent.ACTION_CANCEL -> {
                touchActive = false
                // Observe the original gesture, not RecyclerView's synthetic cancellation of a dragged button.
                onTouchCancelled?.invoke()
            }
        }
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            onTouchFinished?.invoke(event.actionMasked == MotionEvent.ACTION_CANCEL)
        }
        return handled
    }
}

private class RadioPinButton(context: Context) :
    MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle) {
    var onHoldFinished: ((Boolean) -> Unit)? = null
    private var confirmKey = KeyEvent.KEYCODE_UNKNOWN
    val confirmHeld get() = confirmKey != KeyEvent.KEYCODE_UNKNOWN
    private var heldOptions = false
    private val hold = Runnable {
        if (confirmHeld && isEnabled && isShown && hasFocus() && hasWindowFocus()) {
            heldOptions = performLongClick()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode !in CONFIRM_KEYS || !isEnabled) return super.dispatchKeyEvent(event)
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (!confirmHeld && event.repeatCount == 0) {
                confirmKey = event.keyCode
                heldOptions = false
                isPressed = true
                postDelayed(hold, ViewConfiguration.getLongPressTimeout().toLong())
            }
            KeyEvent.ACTION_UP -> if (event.keyCode == confirmKey) {
                val click = !heldOptions && !event.isCanceled
                removeCallbacks(hold)
                confirmKey = KeyEvent.KEYCODE_UNKNOWN
                heldOptions = false
                isPressed = false
                onHoldFinished?.invoke(event.isCanceled)
                if (click) performClick()
            }
        }
        return true
    }

    fun cancelConfirm() {
        removeCallbacks(hold)
        confirmKey = KeyEvent.KEYCODE_UNKNOWN
        heldOptions = false
        isPressed = false
        onHoldFinished?.invoke(true)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        if (!gainFocus) cancelConfirm()
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        if (!hasWindowFocus) cancelConfirm()
        super.onWindowFocusChanged(hasWindowFocus)
    }

    override fun onDetachedFromWindow() {
        cancelConfirm()
        super.onDetachedFromWindow()
    }

    companion object {
        private val CONFIRM_KEYS = setOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_SPACE)
    }
}

internal data class RadioGridColumns(val tiles: Int, val channels: Int) {
    val spans = if (channels == 2 && tiles % 2 != 0) tiles * 2 else tiles
    val tileSpan = spans / tiles
    val channelSpan = spans / channels

    companion object {
        fun forWidth(widthDp: Float, fontScale: Float, hasChannels: Boolean): RadioGridColumns {
            val scale = fontScale.coerceAtLeast(1f)
            return RadioGridColumns((widthDp / (136 * scale)).toInt().coerceAtLeast(1),
                if (hasChannels && widthDp >= 520 * scale) 2 else 1)
        }
    }
}

private class RadioGridLayoutManager(context: Context, private val hasChannels: Boolean) : GridLayoutManager(context, 1) {
    private val density = context.resources.displayMetrics.density
    private val fontScale = context.resources.configuration.fontScale
    var columns = RadioGridColumns(1, 1)
        private set

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        val next = RadioGridColumns.forWidth((width - paddingLeft - paddingRight) / density, fontScale, hasChannels)
        if (columns != next) {
            columns = next
            spanCount = next.spans
            spanSizeLookup.invalidateSpanIndexCache()
            spanSizeLookup.invalidateSpanGroupIndexCache()
        }
        super.onLayoutChildren(recycler, state)
    }
}
