package com.dev.usdi_wallet.ui.common

import org.json.JSONArray
import org.json.JSONObject

enum class ClaimInputFormat {
    STRING,
    INTEGER,
    DATE,
    BOOLEAN,
    UNKNOWN,
}

fun formatClaimName(name: String): String =
    claimNameWithoutKnownTypeSuffix(name)
        .ifBlank { name.trim() }
        .toDisplayClaimName()

fun claimNameWithoutKnownTypeSuffix(name: String): String {
    val trimmed = name.trim()
    val suffix = trimmed.substringAfterLast("_", missingDelimiterValue = "")
    return if (suffix.lowercase() in KNOWN_TYPE_SUFFIXES) {
        trimmed.substringBeforeLast("_")
    } else {
        trimmed
    }
}

fun claimInputFormat(name: String): ClaimInputFormat =
    when (name.trim().substringAfterLast("_", missingDelimiterValue = "").lowercase()) {
        "str" -> ClaimInputFormat.STRING
        "num" -> ClaimInputFormat.INTEGER
        "date" -> ClaimInputFormat.DATE
        "bool" -> ClaimInputFormat.BOOLEAN
        else -> ClaimInputFormat.UNKNOWN
    }

fun predicateInputFormatForClaim(name: String): ClaimInputFormat =
    when (claimInputFormat(name)) {
        ClaimInputFormat.DATE -> ClaimInputFormat.DATE
        else -> ClaimInputFormat.INTEGER
    }

fun isClaimInputCandidate(value: String, format: ClaimInputFormat): Boolean =
    when (format) {
        ClaimInputFormat.INTEGER -> PARTIAL_INTEGER_REGEX.matches(value)
        ClaimInputFormat.DATE -> value.isPartialIsoDateInput()
        else -> true
    }

fun isFinalClaimInputValid(value: String, format: ClaimInputFormat): Boolean =
    when (format) {
        ClaimInputFormat.INTEGER -> value.trim().toIntOrNull() != null
        ClaimInputFormat.DATE -> value.trim().isValidIsoDate()
        ClaimInputFormat.BOOLEAN -> value.trim().lowercase() in setOf("true", "false")
        ClaimInputFormat.STRING -> value.isNotEmpty()
        ClaimInputFormat.UNKNOWN -> value.isNotEmpty()
    }

fun claimPredicateWireValue(name: String, value: String): String {
    val trimmed = value.trim()
    return if (predicateInputFormatForClaim(name) == ClaimInputFormat.DATE) {
        trimmed.replace("-", "")
    } else {
        trimmed
    }
}

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

fun formatClaimDisplayValue(name: String, value: Any?): String {
    val formatted = formatClaimValue(value)
    return if (claimInputFormat(name) == ClaimInputFormat.DATE) {
        formatted.asIsoDateFromCompactDate() ?: formatted
    } else {
        formatted
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

private fun String.toDisplayClaimName(): String {
    val spaced = replace('_', ' ')
        .replace('-', ' ')
        .trim()
        .replace(WHITESPACE_REGEX, " ")
    return spaced.replaceFirstChar { it.uppercase() }
}

private fun String.asIsoDateFromCompactDate(): String? {
    val trimmed = trim()
    if (!COMPACT_DATE_REGEX.matches(trimmed)) return null

    val isoDate = "${trimmed.substring(0, 4)}-${trimmed.substring(4, 6)}-${trimmed.substring(6, 8)}"
    return isoDate.takeIf { it.isValidIsoDate() }
}

private fun String.isValidIsoDate(): Boolean {
    if (!DATE_REGEX.matches(this)) return false
    val year = substring(0, 4).toIntOrNull() ?: return false
    val month = substring(5, 7).toIntOrNull() ?: return false
    val day = substring(8, 10).toIntOrNull() ?: return false
    if (month !in 1..12) return false
    val maxDay = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (year.isLeapYear()) 29 else 28
        else -> return false
    }
    return day in 1..maxDay
}

private fun String.isPartialIsoDateInput(): Boolean {
    if (length > 10) return false
    return allIndexed { index, character ->
        when (index) {
            4, 7 -> character == '-'
            else -> character.isDigit()
        }
    }
}

private fun String.allIndexed(predicate: (Int, Char) -> Boolean): Boolean {
    forEachIndexed { index, character ->
        if (!predicate(index, character)) return false
    }
    return true
}

private fun Int.isLeapYear(): Boolean =
    this % 4 == 0 && (this % 100 != 0 || this % 400 == 0)

private val KNOWN_TYPE_SUFFIXES = setOf("str", "num", "bool", "date")
private val WHITESPACE_REGEX = Regex("\\s+")
private val PARTIAL_INTEGER_REGEX = Regex("-?\\d*")
private val DATE_REGEX = Regex("\\d{4}-\\d{2}-\\d{2}")
private val COMPACT_DATE_REGEX = Regex("\\d{8}")
