package com.macarmusic.app.model

import java.io.DataInputStream

internal object PinyinInitials {
    private class Table {
        val common: IntArray
        val codes: IntArray
        val masks: IntArray

        init {
            DataInputStream(requireNotNull(PinyinInitials::class.java.getResourceAsStream("/pinyin-initials.bin")) {
                "Missing bundled pinyin initial data"
            }.buffered()).use { input ->
                check(input.readInt() == 0x50494e31 && input.readInt() == 0x3400)
                val count = input.readInt()
                val extra = input.readInt()
                check(count == 0x6c00 && extra in 1..100_000)
                common = IntArray(count) { input.readInt() }
                codes = IntArray(extra)
                masks = IntArray(extra)
                for (index in codes.indices) {
                    codes[index] = input.readInt()
                    masks[index] = input.readInt()
                }
                check(input.read() == -1)
            }
        }
    }

    // Load only when a Chinese-initial fallback is needed, never during startup or an empty search.
    private val table by lazy { Table() }

    fun mask(code: Int): Int = when (code) {
        in 'a'.code..'z'.code -> 1 shl (code - 'a'.code)
        in 'A'.code..'Z'.code -> 1 shl (code - 'A'.code)
        in 0x3400..0x9fff -> table.common[code - 0x3400]
        0x3007, in 0xf900..0xfaff, in 0x20000..0x3ffff ->
            table.codes.binarySearch(code).let { if (it < 0) 0 else table.masks[it] }
        else -> 0
    }

    private fun separator(code: Int): Boolean = Character.isWhitespace(code) || when (Character.getType(code)) {
        Character.SPACE_SEPARATOR.toInt(), Character.CONNECTOR_PUNCTUATION.toInt(),
        Character.DASH_PUNCTUATION.toInt(), Character.START_PUNCTUATION.toInt(),
        Character.END_PUNCTUATION.toInt(), Character.OTHER_PUNCTUATION.toInt(),
        Character.INITIAL_QUOTE_PUNCTUATION.toInt(), Character.FINAL_QUOTE_PUNCTUATION.toInt() -> true
        else -> false
    }

    class Query(private val text: String) {
        private val positions = LongArray(26).also { values ->
            if (text.length <= 64) text.forEachIndexed { index, char ->
                values[char - 'a'] = values[char - 'a'] or (1L shl index)
            }
        }

        fun matches(value: String): Boolean {
            if (text.length > value.length) return false
            val longQuery = if (text.length > 64) BooleanArray(text.length) else null
            val longChinese = longQuery?.let { BooleanArray(it.size) }
            var state = 0L
            var chineseState = 0L
            var index = 0
            while (index < value.length) {
                val code = value.codePointAt(index)
                index += Character.charCount(code)
                var mask = mask(code)
                if (mask == 0 && separator(code)) continue
                val chinese = code > 127 && mask != 0
                if (longQuery != null) {
                    val withChinese = requireNotNull(longChinese)
                    for (position in text.indices.reversed()) {
                        val continues = (position == 0 || longQuery[position - 1]) &&
                            mask and (1 shl (text[position] - 'a')) != 0
                        withChinese[position] = continues && (chinese || (position > 0 && withChinese[position - 1]))
                        longQuery[position] = continues
                    }
                    if (withChinese.last()) return true
                } else {
                    var matching = 0L
                    while (mask != 0) {
                        val bit = Integer.numberOfTrailingZeros(mask)
                        matching = matching or positions[bit]
                        mask = mask and (mask - 1)
                    }
                    // Shift-And preserves overlapping matches and alternative polyphonic initials.
                    state = ((state shl 1) or 1L) and matching
                    chineseState = if (chinese) state else (chineseState shl 1) and matching
                    if (chineseState and (1L shl (text.length - 1)) != 0L) return true
                }
            }
            return false
        }
    }
}
