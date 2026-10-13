package com.macarmusic.app

import android.app.Application
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import com.macarmusic.app.data.ArtworkRepository
import com.macarmusic.app.data.LocalizedContext
import com.macarmusic.app.data.MusicRepository
import com.macarmusic.app.data.PlaybackRepository
import com.macarmusic.app.data.PlaybackSettings
import com.macarmusic.app.data.BackgroundRepository
import com.macarmusic.app.data.BackgroundTint
import com.macarmusic.app.data.DisplaySettings
import com.macarmusic.app.lyrics.LyricsRepository
import com.macarmusic.app.model.Notice
import com.macarmusic.app.model.SortOrder
import com.macarmusic.app.playback.SpectrumSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

class MaCarMusicApp : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val noticeSequence = AtomicLong()
    private val mutableNotice = MutableStateFlow<Notice?>(null)
    val notice = mutableNotice.asStateFlow()
    val repository by lazy { MusicRepository(localizedContext, applicationScope, ::report) }
    val artwork by lazy { ArtworkRepository(localizedContext) }
    val playback by lazy { PlaybackRepository(localizedContext, applicationScope, ::report) }
    val playbackSettings by lazy { PlaybackSettings(this) }
    lateinit var displaySettings: DisplaySettings
        private set
    val backgrounds by lazy { BackgroundRepository(localizedContext) }
    val lyrics by lazy { LyricsRepository(localizedContext, repository, applicationScope) }
    internal val playbackReady = MutableStateFlow(false)
    val spectrum by lazy { SpectrumSource(applicationScope) }
    val localizedContext by lazy { LocalizedContext(this) }

    override fun onCreate() {
        super.onCreate()
        displaySettings = DisplaySettings(this, ::applyTheme)
        applyTheme()
    }

    fun text(@StringRes id: Int, vararg arguments: Any): String =
        localizedContext.getString(id, *arguments)

    fun report(message: String) {
        mutableNotice.value = Notice(noticeSequence.incrementAndGet(), message)
    }

    fun inform(message: String) {
        mutableNotice.value = Notice(noticeSequence.incrementAndGet(), message, isError = false)
    }

    fun dismissNotice(id: Long) {
        val current = mutableNotice.value
        if (current?.id == id) mutableNotice.compareAndSet(current, null)
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
        AppCompatDelegate.setDefaultNightMode(
            if (!appearance.customTheme) themeMode
            else if (appearance.tint == BackgroundTint.LIGHT) AppCompatDelegate.MODE_NIGHT_NO
            else AppCompatDelegate.MODE_NIGHT_YES,
        )
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
