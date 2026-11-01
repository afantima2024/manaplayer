package com.macarmusic.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
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
    private val onOptions: (RadioProgram) -> Unit,
) : ListAdapter<RadioProgram, RadioButtonAdapter.Holder>(Diff) {
    class Holder(val button: MaterialButton) : RecyclerView.ViewHolder(button) {
        val styler = BlockButtonStyler(button, preserveMargins = true)
        var logoJob: Job? = null
        var logoLoaded = false
    }

    private var selected: String? = null
    private var favorites = emptySet<String>()
    private var pinColor = 0
    private var positions = emptyMap<String, Int>()
    private var blocks = false to BlockOutline()
    private var logoVersion = -1L
    private val warnedLogos = hashSetOf<String>()

    fun logosChanged(version: Long) {
        if (logoVersion == version) return
        logoVersion = version
        notifyItemRangeChanged(0, itemCount, LOGO)
    }

    fun selection(id: String?, favoriteIds: Set<String>, color: Int) {
        if (selected == id && favorites == favoriteIds && pinColor == color) return
        val changed = mutableSetOf<String>()
        if (selected != id) {
            selected?.let(changed::add)
            id?.let(changed::add)
        }
        changed.addAll(favorites - favoriteIds)
        changed.addAll(favoriteIds - favorites)
        if (color != pinColor) changed.addAll(favoriteIds)
        selected = id
        favorites = favoriteIds
        pinColor = color
        changed.forEach { key -> positions[key]?.let { notifyItemChanged(it, STATE) } }
    }

    fun configureBlocks(enabled: Boolean, outline: BlockOutline) {
        if (blocks == (enabled to outline)) return
        blocks = enabled to outline
        notifyItemRangeChanged(0, itemCount, STATE)
    }

    override fun onCurrentListChanged(previousList: MutableList<RadioProgram>, currentList: MutableList<RadioProgram>) {
        positions = currentList.withIndex().associate { it.value.id to it.index }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val context = parent.context
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).roundToInt()
        val button = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            isAllCaps = false
            isFocusable = true
            gravity = Gravity.CENTER
            textSize = 16f
            minLines = 2
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            minHeight = dp(104)
            minimumWidth = dp(48)
            setPaddingRelative(dp(8), dp(8), dp(8), dp(8))
            insetTop = 0
            insetBottom = 0
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_TOP
            iconSize = dp(40)
            iconPadding = dp(4)
        }
        return Holder(button).also { holder ->
            fun station() = holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let(::getItem)
            button.setOnClickListener { station()?.let(onPlay) }
            button.setOnLongClickListener { station()?.let(onOptions); true }
            button.setOnKeyListener { _, key, event ->
                if (key == KeyEvent.KEYCODE_MENU && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    station()?.let(onOptions)
                    true
                } else false
            }
            ViewCompat.replaceAccessibilityAction(button, AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK,
                context.getString(R.string.radio_button_options)) { _, _ ->
                station()?.let(onOptions)
                true
            }
        }
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
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
        holder.logoJob?.cancel()
        holder.logoJob = null
        super.onViewRecycled(holder)
    }

    private fun bindLogo(holder: Holder, station: RadioProgram) {
        holder.logoJob?.cancel()
        holder.logoLoaded = false
        holder.button.setIconResource(R.drawable.ic_radio)
        holder.button.iconTint = ColorStateList.valueOf(AccentTheme.color(holder.button, com.google.android.material.R.attr.colorPrimary))
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
        val context = button.context
        val active = station.id == selected
        val favorite = station.id in favorites
        val title = station.title(context)
        holder.styler.apply(blocks.first, blocks.second)
        button.text = if (station.sources.size > 1) "$title\n${context.getString(R.string.radio_source_count, station.sources.size)}" else title
        button.contentDescription = title
        button.isSelected = active
        ViewCompat.setStateDescription(button, listOfNotNull(
            context.getString(R.string.radio_selected_station).takeIf { active },
            context.getString(R.string.radio_pinned).takeIf { favorite },
        ).joinToString(", ").ifEmpty { null })
        button.typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        button.setTextColor(if (favorite) pinColor else if (active) AccentTheme.color(button, com.google.android.material.R.attr.colorPrimary)
            else MaterialColors.getColor(button, com.google.android.material.R.attr.colorOnSurface))
        button.iconTint = if (!holder.logoLoaded)
            ColorStateList.valueOf(if (favorite) pinColor else AccentTheme.color(button, com.google.android.material.R.attr.colorPrimary)) else null
    }

    companion object {
        private val STATE = Any()
        private val LOGO = Any()
        private val Diff = object : DiffUtil.ItemCallback<RadioProgram>() {
            override fun areItemsTheSame(oldItem: RadioProgram, newItem: RadioProgram) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: RadioProgram, newItem: RadioProgram) = oldItem == newItem
        }

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
