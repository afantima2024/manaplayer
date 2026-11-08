package com.macarmusic.app.ui

import android.graphics.Rect
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ListView
import androidx.core.view.children
import androidx.core.view.doOnLayout
import androidx.core.view.doOnNextLayout
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import com.macarmusic.app.R

interface PlayerFocusItems {
    fun focusKey(position: Int): String
}

/** Remembers controls, not checked values, with no View references in saved state. */
internal class PlayerFocusMemory(private val root: View, private val saved: Bundle) {
    private var active: View? = null
    private var pending: View? = null
    private var paused = true
    private var applying = false
    private var posted = false
    private var deferredByTouch = false
    private var interacted = false
    private val adapters = mutableMapOf<RecyclerView.Adapter<*>, RecyclerView.AdapterDataObserver>()
    private val focusListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, focused ->
        if (!paused && !applying && pending == null && !root.isInTouchMode && root.hasWindowFocus()) remember(focused)
    }
    private val touchModeListener = ViewTreeObserver.OnTouchModeChangeListener { touchMode ->
        // Android may consume the first D-pad key itself when leaving touch mode.
        if (!touchMode && !paused && !applying) {
            interacted = true
            if (pending == null) pending = active?.takeIf(::hasPosition)
            schedule()
        }
    }

    init {
        root.viewTreeObserver.addOnGlobalFocusChangeListener(focusListener)
        root.viewTreeObserver.addOnTouchModeChangeListener(touchModeListener)
    }

    fun scope(view: View, key: String): Boolean {
        if (view.getTag(R.id.playerFocusScope) == key) return false
        view.setTag(R.id.playerFocusScope, key)
        watch(view)
        return true
    }

    fun watch(view: View) {
        if (view is RecyclerView) {
            view.adapter?.let { adapter ->
                if (adapter !in adapters) {
                    val listener = object : RecyclerView.AdapterDataObserver() {
                        override fun onChanged() = schedule()
                        override fun onItemRangeChanged(start: Int, count: Int) = schedule()
                        override fun onItemRangeChanged(start: Int, count: Int, payload: Any?) = schedule()
                        override fun onItemRangeInserted(start: Int, count: Int) = schedule()
                        override fun onItemRangeRemoved(start: Int, count: Int) = schedule()
                        override fun onItemRangeMoved(from: Int, to: Int, count: Int) = schedule()
                    }
                    adapter.registerAdapterDataObserver(listener)
                    adapters[adapter] = listener
                }
            }
        } else if (view is ViewGroup) view.children.forEach(::watch)
    }

    fun hasPosition(view: View): Boolean = key(view)?.let { saved.containsKey(it) } == true

    fun hasItemPosition(view: View): Boolean = key(view)?.let(saved::getBundle)?.getString("item") != null

    fun initialItem(scope: View, list: RecyclerView, item: String, controlId: Int) {
        if (interacted) return
        val scopeKey = key(scope) ?: return
        save(scopeKey, Bundle().apply {
            putInt("list", list.id)
            putString("item", item)
            putInt("view", controlId)
            putIntArray("path", intArrayOf())
        })
        activate(scope)
    }

    fun changeScope(view: View, key: String): Boolean {
        if (view.getTag(R.id.playerFocusScope) == key) return false
        pause()
        return scope(view, key)
    }

    fun pause() {
        paused = true
        pending = null
    }

    fun activate(view: View) {
        active = view
        paused = false
        pending = view.takeIf(::hasPosition)
        deferredByTouch = pending != null && root.isInTouchMode
        schedule()
    }

    fun windowFocused() {
        if (!paused) active?.takeIf { it.isShown }?.let(::activate)
    }

    fun remember(view: View?, choice: Int? = null) {
        if (view == null || !view.isFocusable || applying) return
        val ancestors = generateSequence(view) { it.parent as? View }.takeWhile { it !== root.parent }
        for (scope in ancestors) {
            val scopeKey = key(scope) ?: continue
            val bookmark = address(scope, view) ?: continue
            if (view is ListView && choice != null) bookmark.putInt("choice", choice)
            save(scopeKey, bookmark)
        }
    }

    fun touch(event: MotionEvent) {
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return
        interacted = true
        pending = null
        paused = false
        remember(hit(root, event.rawX.toInt(), event.rawY.toInt()))
    }

    fun key(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || !event.isFocusKey()) return false
        interacted = true
        paused = false
        val touchMode = root.isInTouchMode || deferredByTouch
        val restored = restore(fromKey = true)
        deferredByTouch = false
        if (!restored) pending = null
        return restored && touchMode && event.isDirectionKey()
    }

    fun dispose() {
        pending = null
        active = null
        root.viewTreeObserver.takeIf { it.isAlive }?.apply {
            removeOnGlobalFocusChangeListener(focusListener)
            removeOnTouchModeChangeListener(touchModeListener)
        }
        adapters.forEach { (adapter, listener) -> adapter.unregisterAdapterDataObserver(listener) }
        adapters.clear()
    }

    private fun schedule() {
        if (posted || pending == null || root.isInTouchMode || !root.isAttachedToWindow) return
        posted = true
        root.post {
            posted = false
            val scope = pending ?: return@post
            if (!scope.isShown) return@post
            if (!scope.isLaidOut || scope.isLayoutRequested) scope.doOnLayout { schedule() }
            else restore(fromKey = false)
        }
    }

    private fun restore(fromKey: Boolean): Boolean {
        val scope = pending?.takeIf { it.isShown && it.isAttachedToWindow } ?: return false
        if (!root.hasWindowFocus()) return false
        if (!fromKey && root.isInTouchMode) return false
        val bookmark = key(scope)?.let(saved::getBundle) ?: return false
        val listId = bookmark.getInt("list", View.NO_ID)
        val itemKey = bookmark.getString("item")
        val anchor = if (listId != View.NO_ID && itemKey != null) {
            val list = scope.findViewById<View>(listId) as? RecyclerView ?: return false
            val position = list.adapter?.positionOf(itemKey) ?: -1
            if (position < 0) return false
            val row = list.findViewHolderForAdapterPosition(position)?.itemView
            if (row == null || row.getTag(R.id.playerFocusKey) != itemKey || list.hasPendingAdapterUpdates()) {
                list.doOnNextLayout { schedule() }
                list.scrollToPosition(position)
                list.requestLayout()
                return false
            }
            row
        } else if (itemKey != null) findKey(scope, itemKey) ?: return false else scope
        val viewId = bookmark.getInt("view", View.NO_ID)
        val target = anchor.findViewById<View>(viewId).takeIf { viewId != View.NO_ID }
            ?: follow(anchor, bookmark.getIntArray("path") ?: intArrayOf()) ?: return false
        if (!target.isShown || !target.isEnabled || !target.isFocusable) {
            pending = null
            return false
        }
        applying = true
        val restored = try {
            val focused = if (fromKey && root.isInTouchMode) target.requestFocusFromTouch() else target.requestFocus()
            if (focused) {
                if (target is ListView) {
                    bookmark.getInt("choice", -1).takeIf { it in 0 until target.count }?.let(target::setSelection)
                    // Android 6 can cache the pre-layout selector's disabled/touch state.
                    target.doOnLayout { target.refreshDrawableState() }
                }
                target.requestRectangleOnScreen(Rect(0, 0, target.width, target.height), true)
            }
            focused
        } finally { applying = false }
        if (restored) {
            pending = null
            deferredByTouch = false
        }
        return restored
    }

    private fun address(scope: View, target: View): Bundle? {
        val parents = generateSequence(target) { it.parent as? View }.takeWhile { it !== scope }.toList()
        if (scope !== target && parents.lastOrNull()?.parent !== scope) return null
        val list = parents.filterIsInstance<RecyclerView>().firstOrNull()
        val anchor = list?.findContainingItemView(target)
            ?: parents.firstOrNull { it.getTag(R.id.playerFocusKey) is String } ?: scope
        val indices = mutableListOf<Int>()
        var child = target
        while (child !== anchor) {
            val parent = child.parent as? ViewGroup ?: return null
            indices.add(parent.indexOfChild(child))
            child = parent
        }
        return Bundle().apply {
            putInt("view", target.id)
            putIntArray("path", indices.asReversed().toIntArray())
            putString("item", anchor.getTag(R.id.playerFocusKey) as? String)
            putInt("list", list?.id ?: View.NO_ID)
            if (target is ListView) putInt("choice", target.selectedItemPosition)
        }
    }

    private fun RecyclerView.Adapter<*>.positionOf(key: String): Int {
        if (this is PlayerFocusItems) return (0 until itemCount).firstOrNull { focusKey(it) == key } ?: -1
        if (this is ConcatAdapter) {
            var offset = 0
            for (adapter in adapters) {
                val position = adapter.positionOf(key)
                if (position >= 0) return offset + position
                offset += adapter.itemCount
            }
        }
        return -1
    }

    private fun follow(root: View, path: IntArray): View? {
        var view = root
        for (index in path) view = (view as? ViewGroup)?.getChildAt(index) ?: return null
        return view
    }

    private fun findKey(view: View, key: String): View? {
        if (view.getTag(R.id.playerFocusKey) == key) return view
        if (view is ViewGroup) for (child in view.children) findKey(child, key)?.let { return it }
        return null
    }

    private fun hit(view: View, x: Int, y: Int): View? {
        val bounds = Rect()
        if (!view.isShown || !view.isEnabled || !view.getGlobalVisibleRect(bounds) || !bounds.contains(x, y)) return null
        if (view is ViewGroup) for (index in view.childCount - 1 downTo 0)
            hit(view.getChildAt(index), x, y)?.let { return it }
        return view.takeIf { it.isFocusable && it.isClickable }
    }

    private fun key(view: View) = view.getTag(R.id.playerFocusScope) as? String

    private fun save(scope: String, bookmark: Bundle) {
        val recent = saved.getStringArrayList(RECENT) ?: arrayListOf()
        recent.remove(scope)
        recent.add(scope)
        while (recent.size > MAX_POSITIONS) saved.remove(recent.removeAt(0))
        saved.putStringArrayList(RECENT, recent)
        saved.putBundle(scope, bookmark)
    }

    private companion object {
        const val RECENT = "recent"
        const val MAX_POSITIONS = 64
    }
}

internal fun KeyEvent.isDirectionKey() = when (keyCode) {
    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> true
    else -> false
}

private fun KeyEvent.isFocusKey() = isDirectionKey() || keyCode == KeyEvent.KEYCODE_TAB ||
    keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
