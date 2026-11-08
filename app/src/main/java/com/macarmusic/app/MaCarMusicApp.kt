package com.macarmusic.app

import android.app.Application
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import com.macarmusic.app.data.ArtworkRepository
import com.macarmusic.app.data.LocalizedContext
import com.macarmusic.app.data.MusicRepository
import com.macarmusic.app.data.PlaybackRepository
import com.macarmusic.app.data.PlaybackSettings
import com.macarmusic.app.data.CollectionPositionRepository
import com.macarmusic.app.data.LocalBackupRepository
import com.macarmusic.app.data.BackgroundRepository
import com.macarmusic.app.data.BackgroundTint
import com.macarmusic.app.data.DisplaySettings
import com.macarmusic.app.lyrics.LyricsRepository
import com.macarmusic.app.model.Notice
import com.macarmusic.app.model.SortOrder
import com.macarmusic.app.playback.SpectrumSource
import com.macarmusic.app.widgets.HomeWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

class MaCarMusicApp : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val noticeSequence = AtomicLong()
    private val mutableNotice = MutableStateFlow<Notice?>(null)
    private var noticeExpiry: Job? = null
    val notice = mutableNotice.asStateFlow()
    val repository by lazy { MusicRepository(localizedContext, applicationScope, ::report) }
    val artwork by lazy { ArtworkRepository(localizedContext) }
    val playback by lazy { PlaybackRepository(localizedContext, applicationScope, ::report) { collections.remember(it) } }
    val collections by lazy { CollectionPositionRepository(localizedContext, applicationScope, ::report) }
    val backups by lazy { LocalBackupRepository(this) }
    val playbackSettings by lazy { PlaybackSettings(this) }
    val radio by lazy { com.macarmusic.app.radio.RadioSettings(this) }
    val radioTraffic by lazy { com.macarmusic.app.radio.RadioTraffic() }
    val radioLogos by lazy { com.macarmusic.app.radio.RadioLogoRepository(this) }
    private val savedRadioCatalog = lazy {
        com.macarmusic.app.radio.RadioCatalogRepository(
            com.macarmusic.app.data.AtomicTextStore(java.io.File(filesDir, "radio-stations.json")), applicationScope,
            loadDefaults = { com.macarmusic.app.radio.RadioDefaults.bundled },
            loadSubscriptions = { com.macarmusic.app.radio.RadioBundledDefaults.load(::text) },
            reportDefaults = { error ->
                android.util.Log.e("RadioCatalog", "Bundled radio defaults were not applied; saved lists retained", error)
                report(text(R.string.radio_bundled_defaults_failed))
            },
        ) { error ->
            android.util.Log.e("RadioCatalog", "Cannot read or restore saved radio stations", error)
            report(text(R.string.radio_list_load_failed))
        }.also { catalog ->
            applicationScope.launch(Dispatchers.Main) {
                catalog.awaitReady()
                val state = catalog.state.value
                if (state.error == null) {
                    radio.retainStations(state.knownIds)
                    if (radio.needsBundledDefaults && state.initializeBundledSettings &&
                        state.bundledRevision == com.macarmusic.app.radio.RadioBundledDefaults.REVISION) {
                        val hidden = kotlinx.coroutines.withContext(Dispatchers.Default) {
                            com.macarmusic.app.radio.RadioGroups.defaultHiddenGroups(state.programs)
                        }
                        radio.applyBundledDefaults(hidden)
                    }
                }
            }
        }
    }
    val radioCatalog get() = savedRadioCatalog.value
    internal val radioCatalogInitialized get() = savedRadioCatalog.isInitialized()
    private var radioWarmup: Job? = null
    internal val radioCatalogWarmed = MutableStateFlow(false)

    @Synchronized internal fun warmRadioCatalog() {
        if (radioWarmup != null) return
        radioWarmup = applicationScope.launch {
            radioCatalog.awaitReady()
            radioCatalogWarmed.value = true
        }
    }

    lateinit var displaySettings: DisplaySettings
        private set
    val backgrounds by lazy { BackgroundRepository(localizedContext) }
    private val lyricRepository = lazy {
        LyricsRepository(localizedContext, repository, applicationScope, onCacheProblem = ::report,
            internalExpiry = { displaySettings.options.value.internalLyricExpiry },
            portableExpiry = { displaySettings.options.value.portableLyricExpiry })
    }
    val lyrics get() = lyricRepository.value

    fun lyricsTrackChanged(trackId: String?) {
        if (lyricRepository.isInitialized()) lyricRepository.value.trackChanged(trackId)
    }
    internal val playbackReady = MutableStateFlow(false)
    internal val playbackActive = MutableStateFlow(false)
    internal val playbackProblem = MutableStateFlow<com.macarmusic.app.model.PlaybackProblem?>(null)
    internal val radioPreparing = MutableStateFlow(false)
    internal val startupPlayback = com.macarmusic.app.playback.StartupPlayback()
    val spectrum by lazy { SpectrumSource(applicationScope) }
    private val widgets = lazy { HomeWidgets(this) }
    val homeWidgets get() = widgets.value
    val localizedContext by lazy { LocalizedContext(this) }

    override fun onCreate() {
        super.onCreate()
        displaySettings = DisplaySettings(this, ::applyTheme)
        applyTheme()
    }

    fun text(@StringRes id: Int, vararg arguments: Any): String =
        localizedContext.getString(id, *arguments)

    fun report(message: String) {
        publishNotice(message, isError = true)
    }

    fun inform(message: String) {
        publishNotice(message, isError = false)
    }

    @Synchronized private fun publishNotice(message: String, isError: Boolean) {
        noticeExpiry?.cancel()
        val notice = Notice(noticeSequence.incrementAndGet(), message, isError)
        mutableNotice.value = notice
        val durationMs = displaySettings.options.value.noticeDurationSeconds * 1_000L
        noticeExpiry = applicationScope.launch {
            delay(durationMs)
            dismissNotice(notice.id)
        }
    }

    @Synchronized fun dismissNotice(id: Long) {
        val current = mutableNotice.value
        if (current?.id == id && mutableNotice.compareAndSet(current, null)) {
            noticeExpiry?.cancel()
            noticeExpiry = null
        }
    }

    var themeMode: Int
        get() = if (displaySettings.options.value.customTheme) DisplaySettings.CUSTOM_THEME
            else getSharedPreferences("settings", MODE_PRIVATE).getInt("theme", AppCompatDelegate.MODE_NIGHT_YES)
        set(value) {
            require(value in setOf(DisplaySettings.CUSTOM_THEME, AppCompatDelegate.MODE_NIGHT_YES,
                AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))
            if (value != DisplaySettings.CUSTOM_THEME) {
                getSharedPreferences("settings", MODE_PRIVATE).edit().putInt("theme", value).apply()
            }
            displaySettings.update { it.copy(customTheme = value == DisplaySettings.CUSTOM_THEME) }
            applyTheme()
        }

    private fun applyTheme() {
        val appearance = displaySettings.options.value
        val mode = if (!appearance.customTheme) themeMode
            else if (appearance.tint == BackgroundTint.LIGHT) AppCompatDelegate.MODE_NIGHT_NO
            else AppCompatDelegate.MODE_NIGHT_YES
        val changed = AppCompatDelegate.getDefaultNightMode() != mode
        AppCompatDelegate.setDefaultNightMode(mode)
        if (changed && widgets.isInitialized()) applicationScope.launch(Dispatchers.Main.immediate) { homeWidgets.refresh() }
    }

    var sortOrder: SortOrder
        get() {
            val name = getSharedPreferences("settings", MODE_PRIVATE).getString("sort", SortOrder.TITLE.name)
            return SortOrder.entries.firstOrNull { it.name == name } ?: SortOrder.TITLE
        }
        set(value) {
            getSharedPreferences("settings", MODE_PRIVATE).edit().putString("sort", value.name).apply()
        }

}
