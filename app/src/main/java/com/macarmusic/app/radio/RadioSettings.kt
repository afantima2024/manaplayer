package com.macarmusic.app.radio

import android.content.Context
import android.content.SharedPreferences
import com.macarmusic.app.model.PlaybackSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException

@Serializable
data class RadioTuning(
    val bufferSeconds: Int = 20,
    val startSeconds: Int = 2,
    val retries: Int = 2,
    val autoSource: Boolean = false,
    val downloadLogos: Boolean = true,
    val showStatus: Boolean = true,
) {
    init {
        require(bufferSeconds in 5..60 && startSeconds in 1..10 && startSeconds <= bufferSeconds)
        require(retries in 0..3)
    }
}

@Serializable
data class RadioFavorites(
    val stations: Set<String> = emptySet(), val favoritesOnly: Boolean = false, val enabled: Boolean = true,
    val pins: List<String>? = null, val tuning: RadioTuning = RadioTuning(),
    val pinColor: Int? = null,
    val openPlayerOnSelection: Boolean = false,
) {
    init { require(pinColor == null || pinColor in 0..0xffffff) }
}

data class RadioPreferences(
    val stationId: String? = null,
    val favorites: Set<String> = emptySet(),
    val favoritesOnly: Boolean = false,
    val active: Boolean = false,
    val enabled: Boolean = true,
    val pins: List<String> = RadioDefaults.pins,
    val tuning: RadioTuning = RadioTuning(),
    val pinColor: Int? = null,
    val openPlayerOnSelection: Boolean = false,
)

class RadioSettings(context: Context) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val appContext = context.applicationContext
    private val mutableOptions = MutableStateFlow(read())
    val options = mutableOptions.asStateFlow()
    private val mutableMusic = MutableStateFlow<PlaybackSnapshot?>(null)
    val savedMusic = mutableMusic.asStateFlow()

    private fun read(): RadioPreferences {
        val station = preferences.getString("radioStation", null)?.takeIf(RadioProgram::validId)
        return RadioPreferences(
            stationId = station,
            favorites = preferences.getStringSet("radioFavorites", emptySet()).orEmpty()
                .filterTo(linkedSetOf(), RadioProgram::validId),
            favoritesOnly = preferences.getBoolean("radioFavoritesOnly", false),
            active = station != null && preferences.getBoolean("radioActive", false),
            enabled = preferences.getBoolean("radioEnabled", true),
            pins = if (preferences.contains("radioPins")) {
                preferences.getString("radioPins", "").orEmpty().split('\n').filter(RadioProgram::validId)
                    .distinct().take(RadioDefaults.MAX_PINS)
            } else (RadioDefaults.pins + preferences.getStringSet("radioFavorites", emptySet()).orEmpty().sorted())
                .filter(RadioProgram::validId).distinct().take(RadioDefaults.MAX_PINS),
            tuning = readTuning(),
            pinColor = readPinColor(),
            openPlayerOnSelection = preferences.getBoolean("radioOpenPlayerOnSelection", false),
        )
    }

    private fun readTuning(): RadioTuning {
        val saved = preferences.getString("radioTuning", null) ?: return RadioTuning()
        try { return Json.decodeFromString<RadioTuning>(saved) }
        catch (error: SerializationException) { reportInvalid(error) }
        catch (error: IllegalArgumentException) { reportInvalid(error) }
        return RadioTuning()
    }

    private fun readPinColor(): Int? {
        try {
            val color = preferences.getInt("radioPinColor", -1)
            require(color in -1..0xffffff)
            return color.takeUnless { it == -1 }
        } catch (error: ClassCastException) { reportInvalid(error) }
        catch (error: IllegalArgumentException) { reportInvalid(error) }
        return null
    }

    private fun reportInvalid(error: Exception) {
        android.util.Log.e("RadioSettings", "Invalid saved radio settings; original preference retained", error)
        (appContext as com.macarmusic.app.MaCarMusicApp).report(appContext.getString(com.macarmusic.app.R.string.radio_settings_invalid))
    }

    fun favorite(station: RadioStation) = update {
        it.copy(favorites = if (station.id in it.favorites) it.favorites - station.id else it.favorites + station.id)
    }

    fun favorite(station: RadioProgram) = update {
        it.copy(favorites = if (station.id in it.favorites) it.favorites - station.id else it.favorites + station.id)
    }

    fun filter(favoritesOnly: Boolean) = update { it.copy(favoritesOnly = favoritesOnly) }
    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }
    fun setOpenPlayerOnSelection(enabled: Boolean) = update { it.copy(openPlayerOnSelection = enabled) }
    fun tune(value: RadioTuning) = update { it.copy(tuning = value) }
    fun setPinColor(color: Int?) {
        require(color == null || color in 0..0xffffff)
        update { it.copy(pinColor = color) }
    }
    fun retainStations(ids: Set<String>) = update {
        it.copy(favorites = it.favorites.intersect(ids), pins = it.pins.filter(ids::contains))
    }
    fun pin(station: RadioProgram): Boolean {
        val current = mutableOptions.value.pins
        if (station.id !in current && current.size == RadioDefaults.MAX_PINS) return false
        update { it.copy(pins = if (station.id in current) current - station.id else current + station.id) }
        return true
    }
    fun select(station: RadioStation) = update { it.copy(stationId = station.id, active = true) }
    fun select(station: RadioProgram) = update { it.copy(stationId = station.id, active = true) }
    fun leave() = update { it.copy(active = false) }
    fun rememberMusic(snapshot: PlaybackSnapshot?) { mutableMusic.value = snapshot }
    fun backup(): RadioFavorites = mutableOptions.value.backup()
    fun publishRestored() { mutableOptions.value = read() }

    fun writeBackup(editor: SharedPreferences.Editor, value: RadioFavorites): SharedPreferences.Editor =
        editor.putStringSet("radioFavorites", value.stations).putBoolean("radioFavoritesOnly", value.favoritesOnly)
            .putBoolean("radioEnabled", value.enabled)
            .putString("radioPins", (value.pins ?: (RadioDefaults.pins + value.stations.sorted())
                .distinct().take(RadioDefaults.MAX_PINS)).joinToString("\n"))
            .putString("radioTuning", Json.encodeToString(value.tuning))
            .putInt("radioPinColor", value.pinColor ?: -1)
            .putBoolean("radioOpenPlayerOnSelection", value.openPlayerOnSelection)

    private fun update(change: (RadioPreferences) -> RadioPreferences) {
        val next = change(mutableOptions.value)
        if (next == mutableOptions.value) return
        writeBackup(preferences.edit(), next.backup())
            .putString("radioStation", next.stationId).putBoolean("radioActive", next.active).apply()
        mutableOptions.value = next
    }

    private fun RadioPreferences.backup() =
        RadioFavorites(favorites, favoritesOnly, enabled, pins, tuning, pinColor, openPlayerOnSelection)
}
