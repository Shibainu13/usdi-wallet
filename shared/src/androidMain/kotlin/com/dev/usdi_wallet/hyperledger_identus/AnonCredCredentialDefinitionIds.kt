package com.dev.usdi_wallet.hyperledger_identus

internal fun String.normalizedCredentialDefinitionReference(): String =
    trim()
        .replace(WHITESPACE_AROUND_SLASH, "/")
        .trimEnd('/')

internal fun String.withCredentialDefinitionResourceSuffix(): String {
    val normalized = normalizedCredentialDefinitionReference()
    if (!normalized.contains(CREDENTIAL_DEFINITION_REGISTRY_DEFINITIONS_SEGMENT)) {
        return normalized
    }

    return if (normalized.endsWith(CREDENTIAL_DEFINITION_RESOURCE_SUFFIX)) {
        normalized
    } else {
        "$normalized$CREDENTIAL_DEFINITION_RESOURCE_SUFFIX"
    }
}

private const val CREDENTIAL_DEFINITION_REGISTRY_DEFINITIONS_SEGMENT =
    "credential-definition-registry/definitions/"
private const val CREDENTIAL_DEFINITION_RESOURCE_SUFFIX = "/definition"
private val WHITESPACE_AROUND_SLASH = Regex("\\s*/\\s*")
