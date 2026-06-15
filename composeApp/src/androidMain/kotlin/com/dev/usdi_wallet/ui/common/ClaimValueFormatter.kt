package com.dev.usdi_wallet.ui.common

import org.json.JSONArray
import org.json.JSONObject

fun formatClaimName(name: String): String =
    name.removeKnownTypeSuffix().ifBlank { name }

fun formatClaimValue(value: Any?): String {
    return when (value) {
        null -> "N/A"
        is JSONObject -> formatJsonObjectClaimValue(value)
        is JSONArray -> formatJsonArrayClaimValue(value)
        is Map<*, *> -> formatMapClaimValue(value)
        is List<*> -> value.joinToString(", ") { formatClaimValue(it) }
        is String -> rawValueFromJsonString(value) ?: value
        else -> rawMemberValue(value)?.toString() ?: value.toString()
    }
}

private fun String.removeKnownTypeSuffix(): String {
    val suffix = substringAfterLast("_", missingDelimiterValue = "")
    return if (suffix in setOf("str", "num", "bool", "date")) {
        substringBeforeLast("_")
    } else {
        this
    }
}

private fun formatJsonObjectClaimValue(value: JSONObject): String {
    value.opt("raw")?.let { return it.toString() }

    return value.keys().asSequence().joinToString(", ") { key ->
        "$key: ${formatClaimValue(value.opt(key))}"
    }
}

private fun formatJsonArrayClaimValue(value: JSONArray): String {
    return (0 until value.length()).joinToString(", ") { index ->
        formatClaimValue(value.opt(index))
    }
}

private fun formatMapClaimValue(value: Map<*, *>): String {
    value["raw"]?.let { return it.toString() }

    return value.entries.joinToString(", ") { (key, itemValue) ->
        "$key: ${formatClaimValue(itemValue)}"
    }
}

private fun rawValueFromJsonString(value: String): String? {
    return runCatching {
        formatJsonObjectClaimValue(JSONObject(value))
    }.getOrNull()
}

private fun rawMemberValue(value: Any): Any? {
    value.javaClass.methods
        .firstOrNull { method -> method.name == "getRaw" && method.parameterTypes.isEmpty() }
        ?.let { method -> return runCatching { method.invoke(value) }.getOrNull() }

    return value.javaClass.declaredFields
        .firstOrNull { field -> field.name == "raw" }
        ?.let { field ->
            runCatching {
                field.isAccessible = true
                field.get(value)
            }.getOrNull()
        }
}
