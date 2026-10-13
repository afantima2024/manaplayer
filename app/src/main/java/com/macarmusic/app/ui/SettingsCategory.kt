package com.macarmusic.app.ui

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import com.macarmusic.app.R

enum class SettingsCategory(@IdRes val buttonId: Int, @IdRes val panelId: Int, @StringRes val label: Int) {
    GENERAL(R.id.categoryGeneral, R.id.settingsGeneralPanel, R.string.category_general),
    APPEARANCE(R.id.categoryAppearance, R.id.settingsAppearancePanel, R.string.category_appearance),
    BACKGROUNDS(R.id.categoryBackgrounds, R.id.settingsBackgroundPanel, R.string.category_backgrounds),
    PLAYBACK(R.id.categoryPlayback, R.id.settingsPlaybackPanel, R.string.category_playback),
    SPECTRUM(R.id.categorySpectrum, R.id.settingsSpectrumPanel, R.string.spectrum),
    LYRICS(R.id.categoryLyrics, R.id.settingsLyricsPanel, R.string.lyrics),
    FOLDERS(R.id.categoryFolders, R.id.settingsFoldersPanel, R.string.folders),
}
