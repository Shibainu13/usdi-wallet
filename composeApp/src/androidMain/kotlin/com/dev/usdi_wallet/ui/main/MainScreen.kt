package com.dev.usdi_wallet.ui.main

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dev.usdi_wallet.domain.credential.ClaimType
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
    onSelectCredential: (Credential, List<String>) -> Unit
) {
    var selectedCredential by remember { mutableStateOf<Credential?>(null) }
    var selectedClaims by remember { mutableStateOf<Set<String>>(emptySet()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Proof request", style = MaterialTheme.typography.titleMedium)
            Text("Protocol: ${request.protocolId}")

            if (selectedCredential == null) {
                // Step 1: credential selection
                if (request.credentials.isEmpty()) {
                    Text(
                        text = "No credentials available for this request.",
                        modifier = Modifier.padding(bottom = 24.dp),
                    )
                } else {
                    Text("Select a credential to present:")
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
                                        onClick = {
                                            selectedCredential = credential
                                            // pre-select all non-binary claims
                                            selectedClaims = credential.claims
                                                .filter { it.type != ClaimType.BYTEARRAY }
                                                .map { it.name }
                                                .toSet()
                                        },
                                        label = { Text("Select") },
                                    )
                                },
                            )
                        }
                    }
                }
            } else {
                // Step 2: claim selection
                val credential = selectedCredential!!
                Text("Select claims to disclose:")
                Text(
                    text = credential.subject ?: credential.id,
                    style = MaterialTheme.typography.labelMedium
                )

                LazyColumn(
                    contentPadding = PaddingValues(bottom = 8.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    items(credential.claims.filter { it.type != ClaimType.BYTEARRAY }) { claim ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedClaims = if (claim.name in selectedClaims) {
                                        selectedClaims - claim.name
                                    } else {
                                        selectedClaims + claim.name
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = claim.name in selectedClaims,
                                onCheckedChange = { checked ->
                                    selectedClaims = if (checked) {
                                        selectedClaims + claim.name
                                    } else {
                                        selectedClaims - claim.name
                                    }
                                }
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(claim.name)
                                claim.value?.let {
                                    Text(
                                        text = it.toString(),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = { selectedCredential = null }) {
                        Text("Back")
                    }
                    Button(
                        onClick = {
                            onSelectCredential(credential, selectedClaims.toList())
                        },
                        enabled = selectedClaims.isNotEmpty()
                    ) {
                        Text("Present")
                    }
                }
            }
        }
    }
}
