package com.macarmusic.app.ui

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import com.macarmusic.app.R

enum class SettingsCategory(@IdRes val buttonId: Int, @IdRes val panelId: Int, @StringRes val label: Int) {
    GENERAL(R.id.categoryGeneral, R.id.settingsGeneralPanel, R.string.category_general),
    BACKGROUNDS(R.id.categoryBackgrounds, R.id.settingsBackgroundPanel, R.string.category_backgrounds),
    PLAYBACK(R.id.categoryPlayback, R.id.settingsPlaybackPanel, R.string.category_playback),
    SPECTRUM(R.id.categorySpectrum, R.id.settingsSpectrumPanel, R.string.spectrum),
    LYRICS(R.id.categoryLyrics, R.id.settingsLyricsPanel, R.string.lyrics),
    FOLDERS(R.id.categoryFolders, R.id.settingsFoldersPanel, R.string.folders),
    ABOUT(R.id.categoryAbout, R.id.settingsAboutPanel, R.string.about),
    // Retain the serialized name for activity state saved by versions before the merge.
    APPEARANCE(R.id.categoryGeneral, R.id.settingsGeneralPanel, R.string.category_general);

    val current: SettingsCategory get() = if (this == APPEARANCE) GENERAL else this

    companion object {
        val pages = entries.filter { it != APPEARANCE }
    }
}
