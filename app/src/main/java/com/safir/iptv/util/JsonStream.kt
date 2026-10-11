package com.safir.iptv.util

import android.util.JsonReader
import android.util.JsonToken
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.EOFException
import java.io.InputStream

/**
 * Reads a long JSON list one entry at a time, straight off the wire.
 *
 * A provider's film catalogue can be 30 MB of JSON. Read as one string and then
 * parsed into one tree, that is several times its size in memory at once — more
 * than a cheap box with 1 GB of RAM gives the whole app, which then simply dies
 * while loading. Here only the entry being looked at exists as a tree; it is
 * turned into its small result by [map] and dropped before the next one is read.
 *
 * Accepts the same shapes as [asArrayOrEmpty]: a bare array, or an object that
 * wraps it — under one of [wrapperKeys] if present, otherwise the first array.
 * Anything else, including an empty answer, is an empty list.
 */
fun <T : Any> streamJsonArray(
    input: InputStream,
    vararg wrapperKeys: String,
    map: (JsonElement) -> T?
): List<T> {
    val reader = JsonReader(input.reader(Charsets.UTF_8)).apply { isLenient = true }
    val token = try {
        reader.peek()
    } catch (_: EOFException) {
        return emptyList()
    }
    return when (token) {
        JsonToken.BEGIN_ARRAY -> readArray(reader, map)
        JsonToken.BEGIN_OBJECT -> {
            var fallback: List<T>? = null
            reader.beginObject()
            while (reader.hasNext()) {
                val key = reader.nextName()
                when {
                    reader.peek() != JsonToken.BEGIN_ARRAY -> reader.skipValue()
                    key in wrapperKeys -> return readArray(reader, map)
                    fallback == null -> fallback = readArray(reader, map)
                    else -> reader.skipValue()
                }
            }
            fallback.orEmpty()
        }
        else -> emptyList()
    }
}

private fun <T : Any> readArray(reader: JsonReader, map: (JsonElement) -> T?): List<T> {
    val out = ArrayList<T>()
    reader.beginArray()
    while (reader.hasNext()) {
        map(readElement(reader))?.let(out::add)
    }
    reader.endArray()
    return out
}

/** Numbers stay text, as a lenient parse leaves them; the readers in JsonExt cope. */
private fun readElement(reader: JsonReader): JsonElement = when (reader.peek()) {
    JsonToken.BEGIN_OBJECT -> {
        val map = LinkedHashMap<String, JsonElement>()
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            map[key] = readElement(reader)
        }
        reader.endObject()
        JsonObject(map)
    }
    JsonToken.BEGIN_ARRAY -> {
        val list = ArrayList<JsonElement>()
        reader.beginArray()
        while (reader.hasNext()) list.add(readElement(reader))
        reader.endArray()
        JsonArray(list)
    }
    JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
    JsonToken.NULL -> {
        reader.nextNull()
        JsonNull
    }
    else -> JsonPrimitive(reader.nextString())
}
