package com.macarmusic.app

import android.app.Application
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import com.macarmusic.app.data.ArtworkRepository
import com.macarmusic.app.data.LocalizedContext
import com.macarmusic.app.data.MusicRepository
import com.macarmusic.app.data.PlaybackRepository
import com.macarmusic.app.data.PlaybackSettings
import com.macarmusic.app.model.Notice
import com.macarmusic.app.model.SortOrder
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
    lateinit var repository: MusicRepository
        private set
    lateinit var artwork: ArtworkRepository
        private set
    lateinit var playback: PlaybackRepository
        private set
    lateinit var playbackSettings: PlaybackSettings
        private set
    val localizedContext by lazy { LocalizedContext(this) }

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(themeMode)
        artwork = ArtworkRepository(localizedContext)
        repository = MusicRepository(localizedContext, applicationScope, ::report)
        playback = PlaybackRepository(localizedContext, applicationScope, ::report)
        playbackSettings = PlaybackSettings(this)
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
        get() = getSharedPreferences("settings", MODE_PRIVATE)
            .getInt("theme", AppCompatDelegate.MODE_NIGHT_YES)
        set(value) {
            getSharedPreferences("settings", MODE_PRIVATE).edit().putInt("theme", value).apply()
            AppCompatDelegate.setDefaultNightMode(value)
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
