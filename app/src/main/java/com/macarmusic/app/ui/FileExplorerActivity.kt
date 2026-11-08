package com.macarmusic.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.Settings
import android.text.TextUtils
import android.text.format.Formatter
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.HorizontalScrollView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Space
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.PermissionChecker
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.macarmusic.app.MaCarMusicApp
import com.macarmusic.app.R
import com.macarmusic.app.data.LegacyStorage
import com.macarmusic.app.data.MediaStoreAccess
import com.macarmusic.app.model.BackgroundImages
import com.macarmusic.app.model.ExplorerMode
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class FileExplorerActivity : AppCompatActivity() {
    private val model: FileExplorerViewModel by viewModels()
    private val app get() = application as MaCarMusicApp
    private lateinit var root: LinearLayout
    private lateinit var notice: PlayerNoticeView
    private lateinit var recovery: PlayerRecoveryView
    private lateinit var focusMemory: PlayerFocusMemory
    private lateinit var path: AppCompatTextView
    private lateinit var status: AppCompatTextView
    private lateinit var progress: ProgressBar
    private lateinit var select: MaterialButton
    private lateinit var up: MaterialButton
    private lateinit var locations: MaterialButton
    private lateinit var refresh: MaterialButton
    private lateinit var androidPicker: MaterialButton
    private lateinit var cancel: MaterialButton
    private lateinit var filename: AppCompatEditText
    private lateinit var list: RecyclerView
    private val rows = ExplorerRows(::open, ::removeLocation) { model.up() }
    private var shownItems: List<ExplorerItem>? = null
    private var focusLocationPending = false

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionResult()
    }
    private val permissionSettings = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permissionResult()
    }
    // Keep separate channels: a failed primary launch can deliver a late cancellation.
    private val externalPickers = List(3) {
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val data = result.data
            if ((data?.clipData?.itemCount ?: 0) > BackgroundImages.MAX_COUNT) {
                model.error(if (model.mode == ExplorerMode.IMAGES)
                    getString(R.string.explorer_selection_limit, BackgroundImages.MAX_COUNT)
                    else getString(R.string.picker_no_document))
                return@registerForActivityResult
            }
            val uris = buildList {
                data?.clipData?.let { clip ->
                    for (index in 0 until clip.itemCount) clip.getItemAt(index).uri?.let(::add)
                }
                data?.data?.let(::add)
            }.distinct()
            if (model.externalGrant) {
                if (uris.size == 1) model.addGrant(uris.single(), data?.flags ?: 0)
                else model.error(getString(R.string.picker_no_document))
            } else model.externalResult(uris, data?.flags ?: 0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AccentTheme.apply(this, app.displaySettings.options.value.themeAccent)
        volumeControlStream = AudioManager.STREAM_MUSIC
        buildUi()
        onBackPressedDispatcher.addCallback(this) { if (!model.up()) finish() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { model.state.collect(::render) }
                launch { app.notice.collect(notice::render) }
                launch {
                    combine(app.displaySettings.options.map { it.keepScreenOn }.distinctUntilChanged(),
                        app.playbackActive) { enabled, active -> enabled && active }
                        .collect { root.keepScreenOn = it }
                }
            }
        }
    }

    override fun onStop() {
        root.keepScreenOn = false
        super.onStop()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun text(size: Float) = AppCompatTextView(this).apply {
        textSize = size
        setTextColor(AccentTheme.color(this, com.google.android.material.R.attr.colorOnSurface))
    }

    private fun button(parent: LinearLayout, id: Int, label: Int, action: () -> Unit) =
        MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            this.id = id
            setText(label)
            isAllCaps = false
            textSize = 16f
            minHeight = dp(48)
            minWidth = dp(64)
            minimumWidth = dp(64)
            insetTop = 0
            insetBottom = 0
            setSingleLine(false)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { action() }
            parent.addView(this, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                if (parent.childCount > 0) marginStart = dp(8)
            })
        }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            id = R.id.explorerRoot
            orientation = LinearLayout.VERTICAL
            isFocusableInTouchMode = true
            setBackgroundColor(AccentTheme.color(this, com.google.android.material.R.attr.colorSurface))
        }
        notice = PlayerNoticeView(this)
        setContentView(FrameLayout(this).apply {
            addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(notice, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        })
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(dp(12) + bars.left, dp(8) + bars.top, dp(12) + bars.right,
                dp(8) + maxOf(bars.bottom, keyboard.bottom))
            notice.setPadding(dp(12) + bars.left, dp(8) + bars.top, dp(12) + bars.right, 0)
            WindowInsetsCompat.CONSUMED
        }
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT
        val light = ColorUtils.calculateLuminance(AccentTheme.color(this,
            com.google.android.material.R.attr.colorSurface)) > 0.5
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
        val title = when (model.mode) {
            ExplorerMode.MUSIC_FOLDER -> R.string.add_folder
            ExplorerMode.MUSIC_FILE -> R.string.open_file
            ExplorerMode.IMAGES -> R.string.background_choose
            ExplorerMode.BACKUP_IMPORT -> R.string.backup_restore
            ExplorerMode.RADIO_IMPORT -> R.string.radio_import
            ExplorerMode.BACKUP_EXPORT -> R.string.backup_export
            ExplorerMode.RADIO_EXPORT -> R.string.radio_export
            ExplorerMode.LYRIC_CACHE -> R.string.explorer_lyric_folder
        }
        root.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(AppCompatImageView(context).apply {
                id = R.id.playerDialogLogo
                setImageResource(Branding.icon(context))
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(12) })
            addView(text(22f).apply {
                id = R.id.explorerTitle
                setText(title)
                setTypeface(typeface, Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        })
        path = text(16f).apply {
            id = R.id.explorerPath
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.START
            setPadding(0, dp(8), 0, dp(4))
        }
        root.addView(path)
        recovery = PlayerRecoveryView(this).apply {
            id = R.id.recoveryStatus
            bindActions({ model.refresh() }, { systemPicker(grant = true) })
        }
        root.addView(recovery, LinearLayout.LayoutParams(-1, -2))
        filename = AppCompatEditText(this).apply {
            id = R.id.explorerFilename
            setHint(R.string.explorer_filename)
            textSize = 18f
            minHeight = dp(48)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine(true)
            setText(model.filename)
            doAfterTextChanged { model.filename = it?.toString().orEmpty() }
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_DONE) {
                    WindowCompat.getInsetsController(window, root).hide(WindowInsetsCompat.Type.ime())
                    root.requestFocus()
                    true
                } else false
            }
            isVisible = model.mode.export
        }
        root.addView(filename, LinearLayout.LayoutParams(-1, -2))
        val information = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        progress = ProgressBar(this).apply { id = R.id.explorerProgress }
        information.addView(progress, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(8) })
        status = text(16f).apply {
            id = R.id.explorerStatus
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            setOnClickListener {
                model.state.value.error?.let {
                    AccentTheme.dialog(this@FileExplorerActivity).setTitle(R.string.file_explorer).setMessage(it)
                        .setPositiveButton(R.string.close, null).show()
                }
            }
        }
        information.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(information)
        list = RecyclerView(this).apply {
            id = R.id.explorerList
            isFocusable = true
            layoutManager = LinearLayoutManager(this@FileExplorerActivity)
            adapter = rows
            itemAnimator = null
            clipToPadding = false
            setOnKeyListener { _, key, event ->
                key == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN && event.hasNoModifiers() &&
                    (event.repeatCount > 0 || model.up())
            }
        }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        androidPicker = button(actions, R.id.explorerAndroidPicker, R.string.android_file_picker) { systemPicker() }
        cancel = button(actions, R.id.explorerCancel, R.string.cancel) { if (!model.state.value.saving) finish() }
        select = button(actions, R.id.explorerSelect,
            when (model.mode) {
                ExplorerMode.BACKUP_EXPORT -> R.string.backup_export
                ExplorerMode.RADIO_EXPORT -> R.string.radio_export
                else -> R.string.explorer_select_folder
            },
            model::select)
        actions.addView(Space(this), LinearLayout.LayoutParams(dp(16), ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        locations = button(actions, R.id.explorerLocations, R.string.explorer_locations, model::home)
        up = button(actions, R.id.explorerUp, R.string.up_folder) { model.up() }
        refresh = button(actions, R.id.explorerRefresh, R.string.explorer_refresh, model::refresh)
        val controls = listOf(androidPicker, cancel, select, locations, up, refresh)
        val footer = object : HorizontalScrollView(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val maximum = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(dp(64))
                controls.forEach { if (it.maxWidth != maximum) it.maxWidth = maximum }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply {
            id = R.id.explorerActions
            isFillViewport = true
            isHorizontalScrollBarEnabled = false
            // A pending resize animation can otherwise scroll a newly focused button out of view.
            isSmoothScrollingEnabled = false
            isFocusable = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            setPadding(0, dp(8), 0, 0)
            addView(actions, ViewGroup.LayoutParams(-2, -2))
        }
        root.addView(footer, LinearLayout.LayoutParams(-1, -2))
        val display = app.displaySettings.options.value
        BlockButtonStyler(root, preserveMargins = true).apply(display.controlBlocks, display.blockOutline)
        AccentTheme.tintControls(root)
        root.requestFocus()
        focusMemory = PlayerFocusMemory(root, model.focusPositions)
        ViewCompat.requestApplyInsets(root)
    }

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        if (::focusMemory.isInitialized) focusMemory.touch(event)
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (::focusMemory.isInitialized && focusMemory.key(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::focusMemory.isInitialized) focusMemory.windowFocused()
    }

    override fun onDestroy() {
        if (::focusMemory.isInitialized) focusMemory.dispose()
        super.onDestroy()
    }

    private fun render(state: ExplorerState) {
        state.result?.let {
            if (!isFinishing) {
                val uris = it.uris.map(Uri::parse)
                val output = Intent().setData(uris.first()).addFlags(it.flags)
                output.clipData = ClipData.newRawUri("", uris.first()).apply {
                    uris.drop(1).forEach { uri -> addItem(ClipData.Item(uri)) }
                }
                setResult(Activity.RESULT_OK, output)
                finish()
            }
            return
        }
        val location = state.path.lastOrNull()
        val changedLocation = focusMemory.changeScope(root, "explorer:${location?.place}:${location?.uri.orEmpty()}")
        if (changedLocation) focusLocationPending = true
        if (changedLocation && list.hasFocus() && !list.isInTouchMode) {
            // A fast load can replace the folder before its intermediate loading state is rendered.
            list.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            list.requestFocus()
        }
        path.text = state.path.joinToString(" / ") { it.name }.ifEmpty { getString(R.string.explorer_locations) }
        progress.isVisible = state.loading || state.saving
        recovery.render(state.error, state.loading || state.saving,
            R.string.recovery_reselect_folder.takeIf { location?.place == ExplorerPlace.FOLDER })
        status.text = state.error ?: when {
            state.loading || state.saving -> getString(R.string.loading)
            model.mode.writable && state.path.lastOrNull()?.place == ExplorerPlace.FOLDER && !state.canSelectFolder ->
                getString(R.string.explorer_read_only)
            state.items.isEmpty() -> getString(R.string.explorer_empty)
            else -> ""
        }
        status.isVisible = state.error == null && status.text.isNotEmpty()
        status.isClickable = state.error != null
        status.setTextColor(AccentTheme.color(status, if (state.error == null)
            com.google.android.material.R.attr.colorOnSurface else com.google.android.material.R.attr.colorError))
        up.isEnabled = state.path.isNotEmpty() && !state.saving
        locations.isEnabled = state.path.isNotEmpty() && !state.saving
        refresh.isEnabled = !state.saving
        androidPicker.isEnabled = !state.saving
        cancel.isEnabled = !state.saving
        select.isVisible = model.mode.folder || model.mode == ExplorerMode.IMAGES
        select.isEnabled = !state.loading && !state.saving &&
            (state.canSelectFolder || model.mode == ExplorerMode.IMAGES && state.selected.isNotEmpty())
        if (model.mode == ExplorerMode.IMAGES) select.text = getString(R.string.explorer_select_images, state.selected.size)
        if (shownItems !== state.items) {
            // Keep keyboard focus in the list while an asynchronous folder load removes its rows.
            if (state.loading && list.hasFocus() && !list.isInTouchMode) {
                list.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                list.requestFocus()
            }
            shownItems = state.items
            rows.submitList(state.items) {
                if (!state.loading) restoreListFocus(state.items)
            }
        } else if (!state.loading && focusLocationPending && rows.currentList == state.items) {
            restoreListFocus(state.items)
        }
        rows.selection(state.selected, model.mode == ExplorerMode.IMAGES, state.loading || state.saving)
    }

    private fun restoreListFocus(items: List<ExplorerItem>) {
        // Android 6 can refocus an old row if descendants are enabled before the diff is laid out.
        list.doOnNextLayout {
            if (shownItems !== items || model.state.value.loading) return@doOnNextLayout
            val firstRow = list.isFocused && !list.isInTouchMode && !focusMemory.hasItemPosition(root)
            if (focusLocationPending) {
                focusLocationPending = false
                focusMemory.activate(root)
            }
            list.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            if (firstRow && list.hasFocus() && !list.isInTouchMode) list.getChildAt(0)?.requestFocus()
        }
        list.requestLayout()
    }

    private fun open(item: ExplorerItem) {
        if (model.state.value.saving || model.state.value.loading) return
        if (item.grant) systemPicker(grant = true)
        else item.location?.let {
            if (it.place == ExplorerPlace.FOLDER) model.navigate(it) else permission(it.place)
        } ?: item.document?.let(model::choose)
    }

    private fun removeLocation(item: ExplorerItem) {
        if (!item.removable || model.state.value.saving || model.state.value.loading) return
        AccentTheme.dialog(this).setTitle(R.string.explorer_remove_location)
            .setMessage(getString(R.string.explorer_remove_location_confirm, item.name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.remove) { _, _ -> model.removeLocation(item) }.show()
    }

    private fun permission(place: ExplorerPlace) {
        model.permissionTarget = place.name
        val requested = requestedPermissions(place)
        if (requested.all(::allowed)) {
            permissionResult()
            return
        }
        val preferences = getSharedPreferences("settings", MODE_PRIVATE)
        val settingsRequired = requested.any { !allowed(it) && !shouldShowRequestPermissionRationale(it) &&
            (preferences.getBoolean("explorerAsked.$it", false) || preferences.getBoolean("musicPermissionBlocked.$it", false)) }
        AccentTheme.dialog(this).setTitle(if (place == ExplorerPlace.INDEX) R.string.device_music else R.string.explorer_device_storage)
            .setMessage(if (settingsRequired) R.string.explorer_permission_denied
                else if (place == ExplorerPlace.INDEX) R.string.music_permission_explanation
                else if (model.mode.writable) R.string.explorer_write_access else R.string.explorer_read_access)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(if (settingsRequired) R.string.open_app_settings
                else if (place == ExplorerPlace.DEVICE) R.string.explorer_allow_storage else R.string.allow_music_access) { _, _ ->
                try {
                    if (settingsRequired) permissionSettings.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", packageName, null)))
                    else {
                        preferences.edit().apply { requested.forEach { putBoolean("explorerAsked.$it", true) } }.apply()
                        permissions.launch(requested.toTypedArray())
                    }
                } catch (error: ActivityNotFoundException) { pickerFailure(error, R.string.permission_ui_missing) }
                catch (error: SecurityException) { pickerFailure(error, R.string.explorer_permission_denied) }
            }.show()
    }

    private fun requestedPermissions(place: ExplorerPlace) = if (place == ExplorerPlace.DEVICE)
        LegacyStorage.permissions(model.mode.writable) else listOf(MediaStoreAccess.requiredPermission())

    private fun allowed(permission: String) =
        PermissionChecker.checkSelfPermission(this, permission) == PermissionChecker.PERMISSION_GRANTED

    private fun permissionResult() {
        val place = model.permissionTarget?.let(ExplorerPlace::valueOf) ?: return
        model.permissionTarget = null
        val granted = if (place == ExplorerPlace.DEVICE) LegacyStorage.permitted(this, model.mode.writable)
            else app.repository.documents.deviceMusic.hasPermission()
        if (granted) model.navigate(ExplorerLocation(place, getString(
            if (place == ExplorerPlace.DEVICE) R.string.explorer_device_storage else R.string.device_music)))
        else model.error(getString(R.string.explorer_permission_denied))
    }

    private fun systemPicker(grant: Boolean = false) {
        model.externalGrant = grant
        try {
            if (grant || model.mode == ExplorerMode.MUSIC_FOLDER || model.mode == ExplorerMode.LYRIC_CACHE) {
                externalPickers[0].launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or
                        if (model.mode.writable) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
                    if (Build.VERSION.SDK_INT >= 26) model.state.value.path.lastOrNull()?.uri?.takeIf(String::isNotEmpty)?.let {
                        if (!LegacyStorage.owns(this@FileExplorerActivity, Uri.parse(it)))
                            putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(it))
                    }
                })
            } else if (model.mode.export) {
                val name = model.validFilename() ?: return
                externalPickers[0].launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    type = model.mode.exportMime
                    addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                    putExtra(Intent.EXTRA_TITLE, name)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                })
            } else if (!DocumentPicker.launch(if (model.mode == ExplorerMode.IMAGES) "image/*" else "*/*",
                    multiple = model.mode == ExplorerMode.IMAGES) {
                    externalPickers[when {
                        it.action == Intent.ACTION_OPEN_DOCUMENT -> 0
                        it.hasCategory(Intent.CATEGORY_OPENABLE) -> 1
                        else -> 2
                    }].launch(it)
                }) model.error(getString(R.string.picker_missing))
        } catch (error: ActivityNotFoundException) {
            pickerFailure(error, R.string.picker_missing)
            if (model.mode == ExplorerMode.BACKUP_EXPORT && !grant) {
                AccentTheme.dialog(this).setTitle(R.string.backup_export).setMessage(R.string.backup_share_help)
                    .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.backup_share) { _, _ ->
                        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_SHARE_BACKUP, true))
                        finish()
                    }.show()
            }
        } catch (error: SecurityException) { pickerFailure(error, R.string.picker_denied) }
    }

    private fun pickerFailure(error: Exception, message: Int) {
        android.util.Log.w("FileExplorer", "Android picker or permission UI unavailable", error)
        model.error(getString(message))
    }

    companion object {
        const val EXTRA_SHARE_BACKUP = "explorerShareBackup"
        fun intent(context: Context, mode: ExplorerMode, filename: String = ""): Intent =
            Intent(context, FileExplorerActivity::class.java)
                .putExtra(FileExplorerViewModel.EXTRA_MODE, mode.name)
                .putExtra(FileExplorerViewModel.EXTRA_FILENAME, filename)
    }
}

private class ExplorerRows(private val click: (ExplorerItem) -> Unit, private val remove: (ExplorerItem) -> Unit,
    private val up: () -> Boolean) :
    ListAdapter<ExplorerItem, ExplorerRows.Holder>(object : DiffUtil.ItemCallback<ExplorerItem>() {
        override fun areItemsTheSame(old: ExplorerItem, new: ExplorerItem) = old.key == new.key
        override fun areContentsTheSame(old: ExplorerItem, new: ExplorerItem) = old == new
    }), PlayerFocusItems {
    override fun focusKey(position: Int) = "explorer:${getItem(position).key}"
    private var selected = emptySet<String>()
    private var multiple = false
    private var busy = false

    init { stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY }

    fun selection(value: Set<String>, enabled: Boolean, loading: Boolean) {
        if (selected == value && multiple == enabled && busy == loading) return
        selected = value
        multiple = enabled
        busy = loading
        notifyItemRangeChanged(0, itemCount, "selection")
    }

    override fun getItemViewType(position: Int) = if (getItem(position).removable) 1 else 0
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(parent, viewType == 1)
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(parent: ViewGroup, removable: Boolean) : RecyclerView.ViewHolder(LinearLayout(parent.context)) {
        private val row = itemView as LinearLayout
        private val context = row.context
        private fun dp(value: Int) = (value * context.resources.displayMetrics.density + 0.5f).toInt()
        private val icon = AppCompatImageView(context)
        private val name = AppCompatTextView(context)
        private val detail = AppCompatTextView(context)
        private val checked = AppCompatCheckBox(context)
        private val removeButton = if (removable) MaterialButton(context, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle) else null

        init {
            row.layoutParams = RecyclerView.LayoutParams(-1, -2)
            row.minimumHeight = dp(64)
            row.gravity = Gravity.CENTER_VERTICAL
            row.isFocusable = true
            row.descendantFocusability = if (removable) ViewGroup.FOCUS_BEFORE_DESCENDANTS
                else ViewGroup.FOCUS_BLOCK_DESCENDANTS
            row.setPadding(dp(8), dp(8), dp(8), dp(8))
            row.background = androidx.appcompat.content.res.AppCompatResources.getDrawable(context,
                android.R.drawable.list_selector_background)
            row.foreground = row.playerFocusForeground()
            icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            icon.imageTintList = android.content.res.ColorStateList.valueOf(
                AccentTheme.color(row, com.google.android.material.R.attr.colorPrimary))
            row.addView(icon, LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(12) })
            val labels = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            name.textSize = 20f
            name.maxLines = 2
            name.ellipsize = TextUtils.TruncateAt.MIDDLE
            name.setTextColor(AccentTheme.color(row, com.google.android.material.R.attr.colorOnSurface))
            detail.textSize = 14f
            detail.maxLines = 1
            detail.ellipsize = TextUtils.TruncateAt.MIDDLE
            detail.setTextColor(AccentTheme.color(row, com.google.android.material.R.attr.colorOnSurfaceVariant))
            labels.addView(name)
            labels.addView(detail)
            row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            checked.isClickable = false
            checked.isFocusable = false
            checked.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            row.addView(checked, LinearLayout.LayoutParams(dp(48), dp(48)))
            removeButton?.let { button ->
                row.id = View.generateViewId()
                button.id = R.id.explorerRemoveLocation
                button.setText(R.string.remove)
                button.isAllCaps = false
                button.textSize = 16f
                button.minWidth = dp(64)
                button.minHeight = dp(48)
                button.insetTop = 0
                button.insetBottom = 0
                button.setSingleLine(false)
                button.setPadding(dp(12), dp(8), dp(12), dp(8))
                button.setOnClickListener {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) remove(getItem(position))
                }
                row.addView(button, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(12) })
                row.nextFocusRightId = button.id
                button.nextFocusLeftId = row.id
                val display = (context.applicationContext as MaCarMusicApp).displaySettings.options.value
                BlockButtonStyler(row, preserveMargins = true).apply(display.controlBlocks, display.blockOutline)
            }
            AccentTheme.tintControls(row)
            row.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) click(getItem(position))
            }
            row.setOnKeyListener { _, key, event ->
                if (event.action != KeyEvent.ACTION_DOWN || !event.hasNoModifiers()) return@setOnKeyListener false
                val item = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let(::getItem)
                    ?: return@setOnKeyListener false
                when (key) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> event.repeatCount > 0 || up()
                    KeyEvent.KEYCODE_DPAD_RIGHT -> if (item.location != null || item.document?.isDirectory == true) {
                        if (event.repeatCount == 0) click(item)
                        true
                    } else false
                    KeyEvent.KEYCODE_MENU -> if (item.removable) {
                        if (event.repeatCount == 0) remove(item)
                        true
                    } else false
                    else -> false
                }
            }
            ViewCompat.setAccessibilityDelegate(row, object : androidx.core.view.AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(host: View,
                    info: androidx.core.view.accessibility.AccessibilityNodeInfoCompat,
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.isCheckable = checked.isVisible
                    info.isChecked = checked.isChecked
                }
            })
        }

        fun bind(item: ExplorerItem) {
            itemView.setTag(R.id.playerFocusKey, "explorer:${item.key}")
            name.text = item.name
            detail.text = item.detail.ifEmpty {
                item.document?.let { document ->
                    if (document.isDirectory) context.getString(R.string.folder_kind)
                    else document.sizeBytes?.let { Formatter.formatShortFileSize(context, it) } ?: document.mimeType
                }.orEmpty()
            }
            detail.isVisible = detail.text.isNotEmpty()
            PlayerIcons.image(icon, if (item.location != null || item.grant) R.drawable.ic_folder
                else if (item.document?.mimeType?.startsWith("audio/") == true) R.drawable.ic_note else R.drawable.ic_library)
            checked.isVisible = multiple && item.document?.isDirectory == false
            checked.isChecked = item.document?.uri in selected
            removeButton?.apply {
                isEnabled = !busy
                contentDescription = context.getString(R.string.explorer_remove_location_named, item.name)
            }
            row.isActivated = checked.isVisible && checked.isChecked
        }
    }
}
