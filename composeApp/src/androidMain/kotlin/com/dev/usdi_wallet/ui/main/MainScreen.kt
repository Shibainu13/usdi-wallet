package com.dev.usdi_wallet.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dev.usdi_wallet.domain.credential.Credential
import org.json.JSONArray
import org.json.JSONObject


@Composable
fun MainScreen(
    isReady: Boolean,
    currentTab: WalletTab,
    onTabSelected: (WalletTab) -> Unit,
    navHost: @Composable () -> Unit
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                WalletTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { onTabSelected(tab) },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) }
                    )
                }
            }
        }
    ) { padding ->

        if (!isReady) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Box(modifier = Modifier.padding(padding)) {
                navHost()
            }
        }
    }
}


@Composable
fun RevokedCredentialDialog(
    subject: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Credential Revoked") },
        text = {
            Text("Your credential $subject has been revoked by the issuer.")
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProofRequestSheet(
    request: PendingProofRequest,
    onDismiss: () -> Unit,
    onSelectCredential: (Credential) -> Unit,
    onDeny: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Proof request")
            Text("Protocol: ${request.protocolId}")
            request.details.name?.let { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = "Verifier: ${request.details.verifier}",
                style = MaterialTheme.typography.bodyMedium,
            )

            Text("Required fields", style = MaterialTheme.typography.titleSmall)
            if (request.details.requestedFields.isEmpty()) {
                Text(
                    text = "No requested fields found in this request.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    request.details.requestedFields.forEach { field ->
                        val requirement = field.requirement?.let { " - $it" }.orEmpty()
                        Text(
                            text = "${field.name}$requirement",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            if (request.credentials.isEmpty()) {
                Text(
                    text = "No credentials available for this request.",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(request.credentials, key = { it.id }) { credential ->
                        ListItem(
                            headlineContent = {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(credential.issuer)
                                    credential.claims.forEach { claim ->
                                        Text(
                                            text = "${claim.name}: ${formatClaimValue(claim.value)}",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            },
                            trailingContent = {
                                AssistChip(
                                    onClick = { onSelectCredential(credential) },
                                    label = { Text("Accept") },
                                )
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onDeny,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Deny")
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Later")
                }
            }
        }
    }
}

private fun formatClaimValue(value: Any?): String {
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
