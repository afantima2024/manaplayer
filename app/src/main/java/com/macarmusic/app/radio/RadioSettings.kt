package com.macarmusic.app.radio

import android.content.Context
import android.content.SharedPreferences
import com.macarmusic.app.model.PlaybackSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException
import java.io.IOException

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
    val stations: Set<String> = emptySet(), val favoritesOnly: Boolean = false,
    @SerialName("enabled") val showPanel: Boolean = true,
    val pins: List<String>? = null, val tuning: RadioTuning = RadioTuning(),
    val pinColor: Int? = null,
    val openPlayerOnSelection: Boolean = false,
    val hiddenGroups: Set<String> = emptySet(),
    val navigationMode: RadioNavigationMode = RadioNavigationMode.GLOBAL,
    val buttonLogo: RadioButtonLogo = RadioButtonLogo.BRANDED_RADIO,
    val stationLogos: Map<String, RadioStationLogo> = emptyMap(),
    val refreshInterval: RadioRefreshInterval = RadioRefreshInterval.MANUAL,
) {
    init {
        require(pinColor == null || pinColor in 0..0xffffff)
        require(hiddenGroups.size <= 5000 && hiddenGroups.all(RadioGroup::validId))
        require(stationLogos.size <= RadioList.MAX_PROGRAMS && stationLogos.keys.all(RadioProgram::validId))
    }
}

data class RadioPreferences(
    val stationId: String? = null,
    val favorites: Set<String> = emptySet(),
    val favoritesOnly: Boolean = false,
    val active: Boolean = false,
    val showPanel: Boolean = true,
    val pins: List<String> = RadioDefaults.pins,
    val tuning: RadioTuning = RadioTuning(),
    val pinColor: Int? = null,
    val openPlayerOnSelection: Boolean = false,
    val hiddenGroups: Set<String> = emptySet(),
    val navigationMode: RadioNavigationMode = RadioNavigationMode.GLOBAL,
    val buttonLogo: RadioButtonLogo = RadioButtonLogo.BRANDED_RADIO,
    val stationLogos: Map<String, RadioStationLogo> = emptyMap(),
    val refreshInterval: RadioRefreshInterval = RadioRefreshInterval.MANUAL,
)

internal fun reorderedRadioPins(current: List<String>, original: List<String>, reordered: List<String>): List<String>? {
    val ids = original.toSet()
    if (original.isEmpty() || ids.size != original.size || reordered.size != original.size ||
        reordered.toSet() != ids || current.filter(ids::contains) != original) return null
    var index = 0
    return current.map { if (it in ids) reordered[index++] else it }
}

class RadioSettings(context: Context) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val appContext = context.applicationContext
    private val mutableOptions = MutableStateFlow(read())
    val options = mutableOptions.asStateFlow()
    private val mutableMusic = MutableStateFlow<PlaybackSnapshot?>(null)
    val savedMusic = mutableMusic.asStateFlow()
    var currentStation: RadioProgram? = null
        private set
    var lastAutomaticRefreshAttemptAtMs = readAutomaticRefreshAttempt()
        private set

    private fun read(): RadioPreferences {
        val station = preferences.getString("radioStation", null)?.takeIf(RadioProgram::validId)
        return RadioPreferences(
            stationId = station,
            favorites = preferences.getStringSet("radioFavorites", emptySet()).orEmpty()
                .filterTo(linkedSetOf(), RadioProgram::validId),
            favoritesOnly = preferences.getBoolean("radioFavoritesOnly", false),
            active = station != null && preferences.getBoolean("radioActive", false),
            showPanel = preferences.getBoolean("radioEnabled", true),
            pins = if (preferences.contains("radioPins")) {
                preferences.getString("radioPins", "").orEmpty().split('\n').filter(RadioProgram::validId)
                    .distinct().take(RadioDefaults.MAX_PINS)
            } else (RadioDefaults.pins + preferences.getStringSet("radioFavorites", emptySet()).orEmpty().sorted())
                .filter(RadioProgram::validId).distinct().take(RadioDefaults.MAX_PINS),
            tuning = readTuning(),
            pinColor = readPinColor(),
            openPlayerOnSelection = preferences.getBoolean("radioOpenPlayerOnSelection", false),
            hiddenGroups = readHiddenGroups(),
            navigationMode = readChoice("radioNavigationMode", RadioNavigationMode.GLOBAL),
            buttonLogo = readChoice("radioButtonLogo", RadioButtonLogo.BRANDED_RADIO),
            stationLogos = readStationLogos(),
            refreshInterval = readChoice("radioRefreshInterval", RadioRefreshInterval.MANUAL),
        )
    }

    private fun readAutomaticRefreshAttempt(): Long {
        try {
            return preferences.getLong("radioAutomaticRefreshAttempt", 0).also { require(it >= 0) }
        } catch (error: ClassCastException) { reportInvalid(error) }
        catch (error: IllegalArgumentException) { reportInvalid(error) }
        return 0
    }

    suspend fun recordAutomaticRefreshAttempt(nowMs: Long) {
        require(nowMs > 0)
        lastAutomaticRefreshAttemptAtMs = nowMs
        withContext(Dispatchers.IO) {
            if (!preferences.edit().putLong("radioAutomaticRefreshAttempt", nowMs).commit())
                throw IOException("Cannot save the radio subscription refresh time")
        }
    }

    private inline fun <reified T : Enum<T>> readChoice(key: String, fallback: T): T {
        try {
            val name = preferences.getString(key, null) ?: return fallback
            return enumValues<T>().firstOrNull { it.name == name }
                ?: throw IllegalArgumentException("Invalid radio choice: $key")
        } catch (error: ClassCastException) { reportInvalid(error) }
        catch (error: IllegalArgumentException) { reportInvalid(error) }
        return fallback
    }

    private fun readHiddenGroups(): Set<String> {
        try {
            return preferences.getStringSet("radioHiddenGroups", emptySet()).orEmpty().toSet().also {
                require(it.size <= 5000 && it.all(RadioGroup::validId))
            }

        } catch (error: ClassCastException) { reportInvalid(error) }
        catch (error: IllegalArgumentException) { reportInvalid(error) }
        return emptySet()
    }

    private fun readStationLogos(): Map<String, RadioStationLogo> {
        try {
            val saved = preferences.getString("radioStationLogos", null) ?: return emptyMap()
            return Json.decodeFromString<Map<String, RadioStationLogo>>(saved).also {
                require(it.size <= RadioList.MAX_PROGRAMS && it.keys.all(RadioProgram::validId))
            }
        } catch (error: ClassCastException) { reportInvalid(error) }
        catch (error: SerializationException) { reportInvalid(error) }
        catch (error: IllegalArgumentException) { reportInvalid(error) }
        return emptyMap()
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
    fun setPanelVisible(visible: Boolean) = update { it.copy(showPanel = visible) }
    fun setOpenPlayerOnSelection(enabled: Boolean) = update { it.copy(openPlayerOnSelection = enabled) }
    fun setNavigationMode(mode: RadioNavigationMode) = update { it.copy(navigationMode = mode) }
    fun setRefreshInterval(interval: RadioRefreshInterval) = update { it.copy(refreshInterval = interval) }
    fun setButtonLogo(logo: RadioButtonLogo) = update { it.copy(buttonLogo = logo) }
    fun setStationLogo(id: String, logo: RadioStationLogo?) {
        require(RadioProgram.validId(id))
        update { it.copy(stationLogos = if (logo == null) it.stationLogos - id else it.stationLogos + (id to logo)) }
    }
    fun hideGroups(groups: Set<String>) = update { it.copy(hiddenGroups = it.hiddenGroups + groups) }
    fun showGroups(groups: Set<String>) = update { it.copy(hiddenGroups = it.hiddenGroups - groups) }
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
    fun reorderPins(original: List<String>, reordered: List<String>): Boolean {
        val pins = reorderedRadioPins(mutableOptions.value.pins, original, reordered) ?: return false
        update { it.copy(pins = pins) }
        return true
    }
    fun select(station: RadioStation) = select(station.program)
    fun select(station: RadioProgram) {
        currentStation = station
        update { it.copy(stationId = station.id, active = true) }
    }
    fun leave() {
        currentStation = null
        update { it.copy(active = false) }
    }
    fun rememberMusic(snapshot: PlaybackSnapshot?) { mutableMusic.value = snapshot }
    fun backup(): RadioFavorites = mutableOptions.value.backup()
    fun publishRestored() {
        preferences.edit().putInt("radioDefaultsApplied", RadioBundledDefaults.REVISION).apply()
        lastAutomaticRefreshAttemptAtMs = readAutomaticRefreshAttempt()
        mutableOptions.value = read()
    }

    val needsBundledDefaults get() = preferences.getInt("radioDefaultsApplied", 0) < RadioBundledDefaults.REVISION

    fun applyBundledDefaults(hidden: Set<String>) {
        if (!needsBundledDefaults) return
        val current = mutableOptions.value
        val next = if (preferences.getInt("radioDefaultsApplied", 0) > 0) {
            current.copy(hiddenGroups = current.hiddenGroups - RadioGroups.national.id)
        } else current.copy(
            pins = (listOf(RadioDefaults.CCTV_ID) + current.pins).distinct().take(RadioDefaults.MAX_PINS),
            hiddenGroups = hidden,
            stationLogos = mapOf(RadioDefaults.CCTV_ID to RadioStationLogo.CCTV) + current.stationLogos,
        )
        writeBackup(preferences.edit(), next.backup())
            .putInt("radioDefaultsApplied", RadioBundledDefaults.REVISION).apply()
        mutableOptions.value = next
    }

    fun writeBackup(editor: SharedPreferences.Editor, value: RadioFavorites): SharedPreferences.Editor =
        editor.putStringSet("radioFavorites", value.stations).putBoolean("radioFavoritesOnly", value.favoritesOnly)
            .putBoolean("radioEnabled", value.showPanel)
            .putString("radioPins", (value.pins ?: (RadioDefaults.pins + value.stations.sorted())
                .distinct().take(RadioDefaults.MAX_PINS)).joinToString("\n"))
            .putString("radioTuning", Json.encodeToString(value.tuning))
            .putInt("radioPinColor", value.pinColor ?: -1)
            .putBoolean("radioOpenPlayerOnSelection", value.openPlayerOnSelection)
            .putStringSet("radioHiddenGroups", value.hiddenGroups)
            .putString("radioNavigationMode", value.navigationMode.name)
            .putString("radioButtonLogo", value.buttonLogo.name)
            .putString("radioStationLogos", Json.encodeToString(value.stationLogos))
            .putString("radioRefreshInterval", value.refreshInterval.name)

    private fun update(change: (RadioPreferences) -> RadioPreferences) {
        val next = change(mutableOptions.value)
        if (next == mutableOptions.value) return
        writeBackup(preferences.edit(), next.backup())
            .putString("radioStation", next.stationId).putBoolean("radioActive", next.active).apply()
        mutableOptions.value = next
    }

    private fun RadioPreferences.backup() =
        RadioFavorites(favorites, favoritesOnly, showPanel, pins, tuning, pinColor, openPlayerOnSelection, hiddenGroups,
            navigationMode, buttonLogo, stationLogos, refreshInterval)
}
