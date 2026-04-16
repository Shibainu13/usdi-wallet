package com.dev.usdi_wallet.ui.contact

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dev.usdi_wallet.contact.Contact
@Composable
fun ContactScreen(
    viewModel: ContactViewModel,
    onNavigate: () -> Unit
) {

    val contacts by viewModel.contacts.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // 🔔 Snackbar handling
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
        floatingActionButton = {
            FloatingActionButton(onClick = {
                viewModel.onAddContactClicked()
                onNavigate()
            }) {
                Text("+")
            }
        }
    ) { padding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            // 📋 Contact list
            if (contacts.isEmpty()) {
                Text(
                    text = "No contacts",
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(contacts) { contact ->
                        ContactItem(contact)
                    }
                }
            }

            // ⏳ Loading
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(androidx.compose.ui.Alignment.Center)
                )
            }
        }

        // 📥 Invitation Dialog
        if (uiState.showInvitationDialog) {
            InvitationDialog(
                onSubmit = { viewModel.submitInvitation(it) },
                onDismiss = { viewModel.onInvitationDialogDismissed() }
            )
        }
    }
}
@Composable
private fun ContactItem(contact: Contact) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = contact.name ?: "Unknown")
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = contact.holder ?: "")
        }
    }
}
@Composable
fun InvitationDialog(
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onSubmit(text) }) {
                Text("Accept")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = { Text("Add contact") },
        text = {
            Column {
                Text("Paste an Out-of-Band invitation")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Paste invitation...") }
                )
            }
        }
    )
}
