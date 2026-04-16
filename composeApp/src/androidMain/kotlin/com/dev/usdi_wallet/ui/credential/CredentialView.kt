package com.dev.usdi_wallet.ui.credential


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dev.usdi_wallet.credential.Credential
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
data class ClaimUiModel(
    val label: String,
    val displayValue: String
)
@Composable
fun CredentialScreen(
    viewModel: CredentialViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val credentials by viewModel.credentials.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {

        // Credential List
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp)
        ) {
            items(credentials) { credential ->
                CredentialItem(
                    credential = credential,
                    onClick = { viewModel.onCredentialClicked(credential) }
                )
            }
        }

        // Error Snackbar
        uiState.error?.let { error ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Text(error)
            }

            LaunchedEffect(error) {
                viewModel.onErrorShown()
            }
        }

        // Detail Dialog
        uiState.selectedCredential?.let { credential ->
            AlertDialog(
                onDismissRequest = { viewModel.onDetailDismissed() },
                confirmButton = {
                    Button(onClick = { viewModel.onDetailDismissed() }) {
                        Text("Close")
                    }
                },
                title = { Text("Credential Detail") },
                text = {
                    Text(credential.toString()) // customize later
                }
            )
        }
    }
}
@Composable
private fun CredentialItem(
    credential: Credential,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            credential.claims.forEach { claim ->

                val value = claim.value?.toString() ?: "N/A"

                Text(
                    text = "${claim.name}: $value",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
