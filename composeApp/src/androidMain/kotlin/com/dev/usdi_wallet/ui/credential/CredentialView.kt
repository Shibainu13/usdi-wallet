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
import com.dev.usdi_wallet.credential.Credential

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
                    credential.claims.forEach { claim ->
                        Text(text = "${claim.name}: ${claim.value ?: "N/A"}")
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
                    text = "${claim.name}: ${claim.value ?: "N/A"}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
