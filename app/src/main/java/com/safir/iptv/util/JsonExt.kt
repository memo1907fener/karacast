package com.safir.iptv.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Xtream panels are wildly inconsistent: the same field comes back as `123`,
 * `"123"`, `null` or `""` depending on the panel version. Everything is therefore
 * read out of a [JsonElement] tree by hand instead of through typed serializers.
 */
val LenientJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

fun JsonElement?.asStringOrNull(): String? = when (this) {
    null, JsonNull -> null
    is JsonPrimitive -> content.takeIf { it.isNotBlank() && it != "null" }
    else -> null
}

fun JsonObject.str(vararg keys: String): String? {
    for (key in keys) {
        this[key].asStringOrNull()?.let { return it }
    }
    return null
}

fun JsonObject.int(vararg keys: String): Int? {
    for (key in keys) {
        val raw = this[key].asStringOrNull() ?: continue
        raw.trim().toIntOrNull()?.let { return it }
        raw.trim().toDoubleOrNull()?.let { return it.toInt() }
    }
    return null
}

fun JsonObject.long(vararg keys: String): Long? {
    for (key in keys) {
        val raw = this[key].asStringOrNull() ?: continue
        raw.trim().toLongOrNull()?.let { return it }
        raw.trim().toDoubleOrNull()?.let { return it.toLong() }
    }
    return null
}

/** Truthy for 1, "1", true, "true", "yes". */
fun JsonObject.flag(vararg keys: String): Boolean {
    for (key in keys) {
        val raw = this[key].asStringOrNull()?.lowercase() ?: continue
        return raw == "1" || raw == "true" || raw == "yes"
    }
    return false
}

/** Reads a value that may be an array, or an object that wraps the array. */
fun JsonElement.asArrayOrEmpty(vararg wrapperKeys: String): JsonArray = when (this) {
    is JsonArray -> this
    is JsonObject -> {
        wrapperKeys.firstNotNullOfOrNull { this[it] as? JsonArray }
            ?: (values.firstOrNull { it is JsonArray } as? JsonArray)
            ?: JsonArray(emptyList())
    }
    else -> JsonArray(emptyList())
}
