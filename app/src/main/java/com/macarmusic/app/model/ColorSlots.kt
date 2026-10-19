package com.macarmusic.app.model

import kotlinx.serialization.Serializable

@Serializable
data class ColorSlots(val colors: List<Int?> = List(5) { null }) {
    init {
        require(colors.size >= 5)
        require(colors.all { it == null || it in 0..0xffffff })
    }

    fun define(index: Int, color: Int): ColorSlots {
        require(index in 0..colors.size && color in 0..0xffffff)
        return copy(colors = if (index == colors.size) colors + color
            else colors.mapIndexed { slot, previous -> if (slot == index) color else previous })
    }

    companion object {
        val RAINBOW = listOf(0xff0000, 0xff7f00, 0xffff00, 0x00ff00, 0x0000ff, 0x4b0082, 0x8b00ff)
    }
}
