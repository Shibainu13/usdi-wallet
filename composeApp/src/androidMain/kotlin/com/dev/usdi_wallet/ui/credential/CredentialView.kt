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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dev.usdi_wallet.contact.Contact
import com.dev.usdi_wallet.credential.Credential
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton

@Composable
fun CredentialScreen(viewModel: CredentialViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val credentials by viewModel.credentials.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error, uiState.snackbarMessage) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onErrorShown()
        }
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onSnackbarShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },

        // 👉 reuse FAB idea from ContactScreen
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::onAddContactClicked) {
                Text("+") // or your credential icon
            }
        }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // 🔹 CONTACT SECTION
            if (contacts.isNotEmpty()) {
                item {
                    Text("Contacts", style = MaterialTheme.typography.titleMedium)
                }

                items(contacts, key = { it.holder }) { contact ->
                    ContactCard(contact)
                }
            }

            // 🔹 CREDENTIAL SECTION
            if (credentials.isNotEmpty()) {
                item {
                    Text("Credentials", style = MaterialTheme.typography.titleMedium)
                }

                items(credentials, key = { it.id }) { credential ->
                    CredentialCard(
                        credential = credential,
                        onClick = { viewModel.onCredentialClicked(credential) }
                    )
                }
            }
        }

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }

    // 🔹 Invitation dialog
    if (uiState.showInvitationDialog) {
        InvitationDialog(
            onSubmit = viewModel::submitInvitation,
            onDismiss = viewModel::onInvitationDialogDismissed
        )
    }

    // 🔹 Credential detail dialog (keep existing)
    uiState.selectedCredential?.let { credential ->
        AlertDialog(
            onDismissRequest = viewModel::onDetailDismissed,
            title = { Text(credential.subject ?: credential.id) },
            text = {
                Column {
                    Text("Issuer: ${credential.issuer}")
                    credential.claims.forEach {
                        Text("${it.name}: ${it.value ?: "N/A"}")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::onDetailDismissed) {
                    Text("Close")
                }
            }
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
@Composable
private fun InvitationDialog(
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var invitation by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Add contact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Paste an Out-of-Band invitation.")
                OutlinedTextField(
                    value = invitation,
                    onValueChange = { invitation = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(text = "Paste invitation") },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(invitation) }) {
                Text(text = "Accept")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )
}

@Composable
private fun ContactCard(contact: Contact) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = contact.name)
            Text(text = contact.holder)
        }
    }
}