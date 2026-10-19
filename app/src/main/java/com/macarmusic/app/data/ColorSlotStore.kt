package com.macarmusic.app.data

import android.content.Context
import com.macarmusic.app.R
import com.macarmusic.app.model.ColorSlots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

class ColorSlotStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun read(): ColorSlots = preferences.getString(KEY, null)?.let { Json.decodeFromString<ColorSlots>(it) }
        ?: ColorSlots()

    suspend fun save(slots: ColorSlots) = withContext(Dispatchers.IO) {
        val original = preferences.getString(KEY, null)
        val encoded = Json.encodeToString(slots)
        if (encoded != original && !preferences.edit().putString(KEY, encoded).commit()) {
            preferences.edit().putString(KEY, original).apply()
            throw IOException(context.getString(R.string.color_slots_save_failed))
        }
    }

    private companion object { const val KEY = "customColorSlots" }
}
