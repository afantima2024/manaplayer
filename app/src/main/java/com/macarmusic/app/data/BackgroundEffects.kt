package com.macarmusic.app.data

object BackgroundEffects {
    fun radius(amount: Int): Int {
        require(amount in 0..100)
        return if (amount == 0) 0 else (amount * 24 / 100).coerceAtLeast(1)
    }

    fun tintColor(tint: BackgroundTint, strength: Int): Int {
        require(strength in 0..100)
        if (tint == BackgroundTint.ORIGINAL) return 0
        return ((strength * 255 / 100) shl 24) or (if (tint == BackgroundTint.LIGHT) 0x00ffffff else 0)
    }

    fun blur(pixels: IntArray, width: Int, height: Int, radius: Int, checkCancelled: () -> Unit = {}): IntArray {
        require(width > 0 && height > 0 && pixels.size.toLong() == width.toLong() * height && radius in 0..24)
        if (radius == 0) return pixels.copyOf()
        var source = pixels.copyOf()
        var target = IntArray(pixels.size)
        repeat(3) {
            pass(source, target, width, height, radius, true, checkCancelled)
            val swap = source
            source = target
            target = swap
            pass(source, target, width, height, radius, false, checkCancelled)
            val next = source
            source = target
            target = next
        }
        return source
    }

    private fun pass(
        input: IntArray, output: IntArray, width: Int, height: Int, radius: Int,
        horizontal: Boolean, checkCancelled: () -> Unit,
    ) {
        val outer = if (horizontal) height else width
        val inner = if (horizontal) width else height
        val count = radius * 2 + 1
        for (line in 0 until outer) {
            checkCancelled()
            fun index(offset: Int): Int {
                val bounded = offset.coerceIn(0, inner - 1)
                return if (horizontal) line * width + bounded else bounded * width + line
            }
            var alpha = 0
            var red = 0
            var green = 0
            var blue = 0
            fun add(pixel: Int, direction: Int) {
                alpha += (pixel ushr 24) * direction
                red += (pixel ushr 16 and 255) * direction
                green += (pixel ushr 8 and 255) * direction
                blue += (pixel and 255) * direction
            }
            for (sample in -radius..radius) add(input[index(sample)], 1)
            for (position in 0 until inner) {
                output[index(position)] = ((alpha / count) shl 24) or ((red / count) shl 16) or
                    ((green / count) shl 8) or (blue / count)
                add(input[index(position - radius)], -1)
                add(input[index(position + radius + 1)], 1)
            }
        }
    }
}
