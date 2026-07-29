package com.dev.usdi_wallet.hyperledger_identus

import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Base64

internal fun extractAnonCredRevealedAttributes(text: String): Map<String, String> {
    val json = runCatching { JSONObject(text) }.getOrNull() ?: return emptyMap()
    return extractAnonCredRevealedAttributes(json)
}

internal fun extractAnonCredRevealedAttributes(json: JSONObject): Map<String, String> {
    val attributes = linkedMapOf<String, String>()
    collectRawAttributes(json, attributes)
    return attributes
}

private fun collectRawAttributes(
    value: Any?,
    attributes: MutableMap<String, String>,
    parentKey: String? = null,
) {
    when (value) {
        is JSONObject -> collectRawAttributesFromObject(value, attributes, parentKey)
        is JSONArray -> {
            for (index in 0 until value.length()) {
                collectRawAttributes(value.opt(index), attributes, parentKey)
            }
        }
    }
}

private fun collectRawAttributesFromObject(
    json: JSONObject,
    attributes: MutableMap<String, String>,
    parentKey: String?,
) {
    collectRevealedAttributes(json.optJSONObject("revealed_attrs"), attributes)
    collectRevealedAttributeGroups(json.optJSONObject("revealed_attr_groups"), attributes)
    collectCredentialValues(json.optJSONObject("values"), attributes)
    collectDecodedAttachmentData(json.optString("base64"), attributes)

    if (parentKey != null && json.has("raw")) {
        putRawAttribute(attributes, parentKey, json.opt("raw"))
    }

    val keys = json.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        if (key == "encoded" || key == "raw") continue
        collectRawAttributes(json.opt(key), attributes, key)
    }
}

private fun collectDecodedAttachmentData(
    base64Value: String,
    attributes: MutableMap<String, String>,
) {
    val decoded = base64Value
        .takeIf { it.isNotBlank() }
        ?.base64DecodedString()
        ?: return
    val json = runCatching { JSONObject(decoded) }.getOrNull() ?: return
    collectRawAttributes(json, attributes)
}

private fun String.base64DecodedString(): String? {
    val normalized = trim().withBase64Padding()
    return listOf(
        Base64.getUrlDecoder(),
        Base64.getDecoder(),
    ).firstNotNullOfOrNull { decoder ->
        runCatching {
            String(decoder.decode(normalized), StandardCharsets.UTF_8)
        }.getOrNull()
    }
}

private fun String.withBase64Padding(): String {
    val missingPadding = (4 - length % 4) % 4
    return this + "=".repeat(missingPadding)
}

private fun collectRevealedAttributes(
    revealedAttributes: JSONObject?,
    attributes: MutableMap<String, String>,
) {
    if (revealedAttributes == null) return

    val keys = revealedAttributes.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val attribute = revealedAttributes.optJSONObject(key) ?: continue
        putRawAttribute(attributes, key, attribute.opt("raw"))
    }
}

private fun collectRevealedAttributeGroups(
    revealedAttributeGroups: JSONObject?,
    attributes: MutableMap<String, String>,
) {
    if (revealedAttributeGroups == null) return

    val groupKeys = revealedAttributeGroups.keys()
    while (groupKeys.hasNext()) {
        val group = revealedAttributeGroups.optJSONObject(groupKeys.next()) ?: continue
        val values = group.optJSONObject("values") ?: continue
        collectCredentialValues(values, attributes)
    }
}

private fun collectCredentialValues(
    values: JSONObject?,
    attributes: MutableMap<String, String>,
) {
    if (values == null) return

    val keys = values.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val attribute = values.optJSONObject(key) ?: continue
        putRawAttribute(attributes, key, attribute.opt("raw"))
    }
}

private fun putRawAttribute(
    attributes: MutableMap<String, String>,
    key: String,
    rawValue: Any?,
) {
    if (rawValue == null || rawValue == JSONObject.NULL) return
    attributes.putIfAbsent(displayAttributeName(key), rawValue.toString())
}

private fun displayAttributeName(key: String): String =
    key.removeSuffix("_attr")
        .removeSuffix("_attribute")
        .removeSuffix("_referent")
        .removeKnownTypeSuffix()

private fun String.removeKnownTypeSuffix(): String {
    val suffix = substringAfterLast("_", missingDelimiterValue = "")
    return if (suffix in setOf("str", "num", "bool", "date")) {
        substringBeforeLast("_")
    } else {
        this
    }
}
