package com.macarmusic.app.radio

enum class RadioNavigationMode { GLOBAL, PINNED_ONLY }
enum class RadioButtonLogo { BRANDED_RADIO, CLASSIC_RADIO }

data class RadioNavigationOptions(
    val mode: RadioNavigationMode,
    val pins: List<String>,
    val hiddenGroups: Set<String>,
) {
    constructor(preferences: RadioPreferences) : this(preferences.navigationMode, preferences.pins, preferences.hiddenGroups)
}

object RadioNavigation {
    fun ordered(stations: List<RadioProgram>, options: RadioNavigationOptions): List<RadioProgram> {
        val byId = stations.associateBy { it.id }
        val pinned = options.pins.distinct().mapNotNull(byId::get)
        if (options.mode == RadioNavigationMode.PINNED_ONLY) return pinned
        val pinIds = pinned.mapTo(hashSetOf()) { it.id }
        return pinned + RadioGroups.grouped(stations).flatMap { (group, programs) ->
            if (group.id in options.hiddenGroups) emptyList() else programs.filter { it.id !in pinIds }
        }
    }

    fun adjacent(ids: List<String>, current: String, direction: Int): String? {
        require(direction == -1 || direction == 1)
        if (ids.isEmpty()) return null
        val index = ids.indexOf(current)
        if (index < 0) return if (direction > 0) ids.first() else ids.last()
        return ids[(index + direction + ids.size) % ids.size]
    }
}
