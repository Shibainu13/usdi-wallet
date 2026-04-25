package com.dev.usdi_wallet.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dev.usdi_wallet.domain.credential.Credential


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
    onSelectCredential: (Credential) -> Unit
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

            if (request.credentials.isEmpty()) {
                Text(
                    text = "No credentials available for this request.",
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(request.credentials, key = { it.id }) { credential ->
                        ListItem(
                            headlineContent = {
                                Text(credential.subject ?: credential.id)
                            },
                            supportingContent = {
                                Text(credential.issuer)
                            },
                            trailingContent = {
                                AssistChip(
                                    onClick = { onSelectCredential(credential) },
                                    label = { Text("Select") },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
