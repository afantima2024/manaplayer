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
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.ProgressBar
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

class FileExplorerActivity : AppCompatActivity() {
    private val model: FileExplorerViewModel by viewModels()
    private val app get() = application as MaCarMusicApp
    private lateinit var root: LinearLayout
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
    private val rows = ExplorerRows(::open)
    private var shownItems: List<ExplorerItem>? = null

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
            repeatOnLifecycle(Lifecycle.State.STARTED) { model.state.collect(::render) }
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun text(size: Float) = AppCompatTextView(this).apply {
        textSize = size
        setTextColor(AccentTheme.color(this, com.google.android.material.R.attr.colorOnSurface))
    }

    private fun button(parent: PlayerDialogActions, id: Int, label: Int, action: () -> Unit) =
        MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            this.id = id
            setText(label)
            isAllCaps = false
            textSize = 16f
            minHeight = dp(48)
            minWidth = dp(64)
            insetTop = 0
            insetBottom = 0
            setSingleLine(false)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { action() }
            parent.addView(this, ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            id = R.id.explorerRoot
            orientation = LinearLayout.VERTICAL
            isFocusableInTouchMode = true
            setBackgroundColor(AccentTheme.color(this, com.google.android.material.R.attr.colorSurface))
        }
        setContentView(root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(dp(12) + bars.left, dp(8) + bars.top, dp(12) + bars.right,
                dp(8) + maxOf(bars.bottom, keyboard.bottom))
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
            ExplorerMode.BACKUP_EXPORT -> R.string.backup_export
            ExplorerMode.LYRIC_CACHE -> R.string.explorer_lyric_folder
        }
        root.addView(text(22f).apply {
            id = R.id.explorerTitle
            setText(title)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        val navigation = PlayerDialogActions(this)
        locations = button(navigation, R.id.explorerLocations, R.string.explorer_locations, model::home)
        up = button(navigation, R.id.explorerUp, R.string.up_folder) { model.up() }
        refresh = button(navigation, R.id.explorerRefresh, R.string.explorer_refresh, model::refresh)
        root.addView(navigation)
        path = text(16f).apply {
            id = R.id.explorerPath
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.START
            setPadding(0, dp(8), 0, dp(4))
        }
        root.addView(path)
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
            isVisible = model.mode == ExplorerMode.BACKUP_EXPORT
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
        }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        val actions = PlayerDialogActions(this)
        androidPicker = button(actions, R.id.explorerAndroidPicker, R.string.android_file_picker) { systemPicker() }
        cancel = button(actions, R.id.explorerCancel, R.string.cancel) { if (!model.state.value.saving) finish() }
        select = button(actions, R.id.explorerSelect,
            if (model.mode == ExplorerMode.BACKUP_EXPORT) R.string.backup_export else R.string.explorer_select_folder,
            model::select)
        root.addView(actions)
        val display = app.displaySettings.options.value
        BlockButtonStyler(root, preserveMargins = true).apply(display.controlBlocks, display.blockOutline)
        AccentTheme.tintControls(root)
        root.requestFocus()
        ViewCompat.requestApplyInsets(root)
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
        path.text = state.path.joinToString(" / ") { it.name }.ifEmpty { getString(R.string.explorer_locations) }
        progress.isVisible = state.loading || state.saving
        status.text = state.error ?: when {
            state.loading || state.saving -> getString(R.string.loading)
            model.mode.writable && state.path.lastOrNull()?.place == ExplorerPlace.FOLDER && !state.canSelectFolder ->
                getString(R.string.explorer_read_only)
            state.items.isEmpty() -> getString(R.string.explorer_empty)
            else -> ""
        }
        status.isVisible = status.text.isNotEmpty()
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
            val restoreFocus = !state.loading && list.isFocused && !list.isInTouchMode
            if (!state.loading) list.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            shownItems = state.items
            rows.submitList(state.items) {
                if (restoreFocus && state.items.isNotEmpty()) {
                    list.doOnNextLayout {
                        if (shownItems === state.items && list.isFocused && !list.isInTouchMode)
                            list.getChildAt(0)?.requestFocus()
                    }
                    list.requestLayout()
                }
            }
        }
        rows.selection(state.selected, model.mode == ExplorerMode.IMAGES)
    }

    private fun open(item: ExplorerItem) {
        if (model.state.value.saving || model.state.value.loading) return
        if (item.grant) systemPicker(grant = true)
        else item.location?.let {
            if (it.place == ExplorerPlace.FOLDER) model.navigate(it) else permission(it.place)
        } ?: item.document?.let(model::choose)
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
            } else if (model.mode == ExplorerMode.BACKUP_EXPORT) {
                val name = model.validFilename() ?: return
                externalPickers[0].launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    type = "application/zip"
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

private class ExplorerRows(private val click: (ExplorerItem) -> Unit) :
    ListAdapter<ExplorerItem, ExplorerRows.Holder>(object : DiffUtil.ItemCallback<ExplorerItem>() {
        override fun areItemsTheSame(old: ExplorerItem, new: ExplorerItem) = old.key == new.key
        override fun areContentsTheSame(old: ExplorerItem, new: ExplorerItem) = old == new
    }) {
    private var selected = emptySet<String>()
    private var multiple = false

    init { stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY }

    fun selection(value: Set<String>, enabled: Boolean) {
        if (selected == value && multiple == enabled) return
        selected = value
        multiple = enabled
        notifyItemRangeChanged(0, itemCount, "selection")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(parent)
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(parent: ViewGroup) : RecyclerView.ViewHolder(LinearLayout(parent.context)) {
        private val row = itemView as LinearLayout
        private val context = row.context
        private fun dp(value: Int) = (value * context.resources.displayMetrics.density + 0.5f).toInt()
        private val icon = AppCompatImageView(context)
        private val name = AppCompatTextView(context)
        private val detail = AppCompatTextView(context)
        private val checked = AppCompatCheckBox(context)

        init {
            row.layoutParams = RecyclerView.LayoutParams(-1, -2)
            row.minimumHeight = dp(64)
            row.gravity = Gravity.CENTER_VERTICAL
            row.isFocusable = true
            row.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
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
            AccentTheme.tintControls(row)
            row.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) click(getItem(position))
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
            name.text = item.name
            detail.text = item.detail.ifEmpty {
                item.document?.let { document ->
                    if (document.isDirectory) context.getString(R.string.folder_kind)
                    else document.sizeBytes?.let { Formatter.formatShortFileSize(context, it) } ?: document.mimeType
                }.orEmpty()
            }
            detail.isVisible = detail.text.isNotEmpty()
            icon.setImageResource(if (item.location != null || item.grant) R.drawable.ic_folder
                else if (item.document?.mimeType?.startsWith("audio/") == true) R.drawable.ic_note else R.drawable.ic_library)
            checked.isVisible = multiple && item.document?.isDirectory == false
            checked.isChecked = item.document?.uri in selected
            row.isActivated = checked.isVisible && checked.isChecked
        }
    }
}
