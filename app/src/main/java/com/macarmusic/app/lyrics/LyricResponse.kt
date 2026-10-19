package com.macarmusic.app.lyrics

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.util.ArrayDeque

internal object LyricResponse {
    private val unicode = Regex("""\\{1,2}u([0-9a-fA-F]{4})""")
    private val entity = Regex("&(#(?:x[0-9a-f]{1,6}|[0-9]{1,7})|amp|lt|gt|quot|apos|nbsp);", RegexOption.IGNORE_CASE)
    private val number = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")
    private val literals = setOf("true", "false", "null")

    fun json(text: String, singleQuoted: Boolean = false): JsonObject {
        if (text.length > 1024 * 1024) throw IOException("Lyric response is too large")
        return try {
            val result = Json.parseToJsonElement(if (singleQuoted) canonicalJson(text) else text) as? JsonObject
                ?: throw IOException("Expected a lyric JSON object")
            val pending = ArrayDeque<JsonElement>().apply { add(result) }
            while (pending.isNotEmpty()) {
                when (val value = pending.removeLast()) {
                    is JsonObject -> pending.addAll(value.values)
                    is JsonArray -> pending.addAll(value)
                    is JsonPrimitive -> if (!value.isString && value.content !in literals &&
                        !number.matches(value.content)) throw IOException("Invalid lyric JSON value")
                }
            }
            result
        } catch (error: SerializationException) {
            throw IOException("Invalid lyric JSON response", error)
        }
    }

    fun decode(text: String): String {
        var decoded = unicode.replace(text) { it.groupValues[1].toInt(16).toChar().toString() }
        repeat(2) {
            decoded = entity.replace(decoded) { match ->
                when (val name = match.groupValues[1].lowercase(java.util.Locale.ROOT)) {
                    "amp" -> "&"
                    "lt" -> "<"
                    "gt" -> ">"
                    "quot" -> "\""
                    "apos" -> "'"
                    "nbsp" -> " "
                    else -> {
                        val code = if (name.startsWith("#x")) name.drop(2).toIntOrNull(16) else name.drop(1).toIntOrNull()
                        if (code != null && code in 0..0x10ffff && code !in 0xd800..0xdfff) {
                            String(Character.toChars(code))
                        } else match.value
                    }
                }
            }
        }
        return decoded
    }

    private fun canonicalJson(text: String): String {
        // Kuwo's catalog uses single-quoted JSON strings. Only translate string delimiters;
        // the normal JSON parser still validates all structure, escapes and values.
        val result = StringBuilder(text.length)
        var quote: Char? = null
        var index = 0
        while (index < text.length) {
            val char = text[index++]
            when {
                quote == null -> if (char == '\'' || char == '"') {
                    quote = char
                    result.append('"')
                } else result.append(char)
                char == '\\' -> {
                    if (index == text.length) throw IOException("Unterminated lyric JSON escape")
                    val escaped = text[index++]
                    if (escaped == '\'' && quote == '\'') result.append('\'')
                    else result.append('\\').append(escaped)
                }
                char == quote -> { quote = null; result.append('"') }
                char == '"' -> result.append("\\\"")
                else -> result.append(char)
            }
        }
        if (quote != null) throw IOException("Unterminated lyric JSON string")
        return result.toString()
    }
}
