package com.dev.usdi_wallet.ui.credential

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.domain.credential.Credential
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun CredentialScreen(viewModel: CredentialViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val credentials by viewModel.credentials.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onErrorShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (credentials.isEmpty()) {
                Text(
                    text = "No credentials available.",
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items = credentials, key = { credential -> credential.id }) { credential ->
                        CredentialCard(
                            credential = credential,
                            onClick = { viewModel.onCredentialClicked(credential) },
                        )
                    }
                }
            }
        }
    }

    uiState.selectedCredential?.let { credential ->
        AlertDialog(
            onDismissRequest = viewModel::onDetailDismissed,
            title = { Text(text = credential.subject ?: credential.id) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Issuer: ${credential.issuer}")
                    Text(text = "Protocol: ${credential.protocol}")
                    Logger.d("Claims: ${credential.claims}")
                    credential.claims.forEach { claim ->
                        Text(text = "${claim.name}: ${formatClaimValue(claim.value)}")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::onDetailDismissed) {
                    Text(text = "Close")
                }
            },
        )
    }
}

@Composable
private fun CredentialCard(
    credential: Credential,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {

            credential.claims.forEach { claim ->
                Text(
                    text = "${claim.name}: ${formatClaimValue(claim.value)}",
                    style = MaterialTheme.typography.bodySmall,
                )
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
