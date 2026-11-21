package com.teamwork.pricehunter.core.provider

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * Safe Gson extraction extensions to guard against JsonNull instances,
 * ClassCastExceptions, and UnsupportedOperationExceptions.
 */
fun JsonObject.getSafe(key: String): JsonElement? =
    if (has(key) && !get(key).isJsonNull) get(key) else null

fun JsonObject.getSafeObject(key: String): JsonObject? =
    if (has(key) && get(key).isJsonObject) getAsJsonObject(key) else null

fun JsonObject.getSafeArray(key: String): JsonArray? =
    if (has(key) && get(key).isJsonArray) getAsJsonArray(key) else null

fun JsonObject.getSafeLong(key: String): Long? =
    if (has(key) && !get(key).isJsonNull) runCatching { get(key).asLong }.getOrNull() else null

fun JsonObject.getSafeFloat(key: String): Float? =
    if (has(key) && !get(key).isJsonNull) runCatching { get(key).asFloat }.getOrNull() else null

fun JsonObject.getSafeDouble(key: String): Double? =
    if (has(key) && !get(key).isJsonNull) runCatching { get(key).asDouble }.getOrNull() else null

fun JsonObject.getSafeString(key: String): String? =
    if (has(key) && !get(key).isJsonNull) runCatching { get(key).asString }.getOrNull() else null

fun JsonObject.getSafeInt(key: String): Int? =
    if (has(key) && !get(key).isJsonNull) runCatching { get(key).asInt }.getOrNull() else null

fun JsonObject.getSafeBoolean(key: String): Boolean? =
    if (has(key) && !get(key).isJsonNull) runCatching { get(key).asBoolean }.getOrNull() else null

fun JsonElement?.asSafeObject(): JsonObject? =
    if (this != null && this.isJsonObject) this.asJsonObject else null

fun JsonElement?.asSafeArray(): JsonArray? =
    if (this != null && this.isJsonArray) this.asJsonArray else null

fun JsonElement?.asSafeString(): String? =
    if (this != null && !this.isJsonNull) runCatching { this.asString }.getOrNull() else null

fun JsonElement?.asSafeLong(): Long? =
    if (this != null && !this.isJsonNull) runCatching { this.asLong }.getOrNull() else null

fun JsonElement?.asSafeFloat(): Float? =
    if (this != null && !this.isJsonNull) runCatching { this.asFloat }.getOrNull() else null

fun JsonElement?.asSafeDouble(): Double? =
    if (this != null && !this.isJsonNull) runCatching { this.asDouble }.getOrNull() else null

fun JsonElement?.asSafeInt(): Int? =
    if (this != null && !this.isJsonNull) runCatching { this.asInt }.getOrNull() else null

fun JsonElement?.asSafeBoolean(): Boolean? =
    if (this != null && !this.isJsonNull) runCatching { this.asBoolean }.getOrNull() else null
