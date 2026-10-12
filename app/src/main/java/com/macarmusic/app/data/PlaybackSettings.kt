package com.macarmusic.app.data

import android.content.Context
import android.util.Log
import com.macarmusic.app.model.LoopMode
import com.macarmusic.app.model.PlayOrder
import com.macarmusic.app.model.PlaybackOptions
import com.macarmusic.app.model.PlaybackSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlaybackSettings(context: Context) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val mutableOptions = MutableStateFlow(PlaybackOptions(
        loop = readEnum("loopMode", LoopMode.OFF, LoopMode.entries),
        order = readEnum("playOrder", PlayOrder.SEQUENTIAL, PlayOrder.entries),
    ))
    val options = mutableOptions.asStateFlow()

    fun migrate(snapshot: PlaybackSnapshot) {
        update(PlaybackOptions(
            loop = if (preferences.contains("loopMode")) options.value.loop else LoopMode.fromRepeatMode(snapshot.repeat),
            order = if (preferences.contains("playOrder")) options.value.order
            else if (snapshot.shuffle) PlayOrder.RANDOM else PlayOrder.SEQUENTIAL,
        ))
    }

    fun setLoop(mode: LoopMode) = update(options.value.copy(loop = mode))

    fun setOrder(order: PlayOrder) = update(options.value.copy(order = order))

    private fun update(value: PlaybackOptions) {
        preferences.edit()
            .putString("loopMode", value.loop.name)
            .putString("playOrder", value.order.name)
            .apply()
        mutableOptions.value = value
    }

    private fun <T : Enum<T>> readEnum(key: String, default: T, entries: List<T>): T {
        val name = preferences.getString(key, null) ?: return default
        return entries.firstOrNull { it.name == name } ?: default.also {
            Log.w("PlaybackSettings", "Unknown saved $key: $name; using ${default.name}")
        }
    }
}
