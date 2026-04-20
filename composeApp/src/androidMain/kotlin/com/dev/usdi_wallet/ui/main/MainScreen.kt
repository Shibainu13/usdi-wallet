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
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.dev.usdi_wallet.ui.contact.ContactScreen
import com.dev.usdi_wallet.ui.contact.ContactViewModel
import com.dev.usdi_wallet.ui.credential.CredentialScreen
import com.dev.usdi_wallet.ui.credential.CredentialViewModel
import com.dev.usdi_wallet.ui.verification.VerificationRequestScreen
import com.dev.usdi_wallet.ui.verification.VerificationRequestViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainRoute(
    viewModel: MainViewModel,
    contactViewModel: ContactViewModel = composeViewModel(),
    credentialViewModel: CredentialViewModel = composeViewModel(),
    verificationRequestViewModel: VerificationRequestViewModel = composeViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TabRow(selectedTabIndex = uiState.selectedTab.ordinal) {
                WalletTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { viewModel.onTabSelected(tab) },
                        text = { Text(tab.title) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!uiState.isReady) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                when (uiState.selectedTab) {
                    WalletTab.CONTACTS -> ContactScreen(viewModel = contactViewModel)
                    WalletTab.CREDENTIALS -> CredentialScreen(viewModel = credentialViewModel)
                    WalletTab.VERIFY -> VerificationRequestScreen(viewModel = verificationRequestViewModel)
                }
            }
        }
    }

    uiState.pendingProofRequests.firstOrNull()?.let { pendingRequest ->
        ModalBottomSheet(onDismissRequest = viewModel::dismissProofRequest) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = "Proof request")
                Text(text = "Protocol: ${pendingRequest.protocolId}")

                if (pendingRequest.credentials.isEmpty()) {
                    Text(
                        text = "No credentials available for this request.",
                        modifier = Modifier.padding(bottom = 24.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            items = pendingRequest.credentials,
                            key = { credential -> credential.id },
                        ) { credential ->
                            ListItem(
                                headlineContent = { Text(credential.subject ?: credential.id) },
                                supportingContent = { Text(credential.issuer) },
                                trailingContent = {
                                    AssistChip(
                                        onClick = {
                                            scope.launch {
                                                pendingRequest.onCredentialSelected(credential)
                                            }
                                        },
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

    uiState.revokedCredentialAlerts.firstOrNull()?.let { revokedCredentialAlert ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRevokedCredentialAlert,
            title = { Text(text = "Credential Revoked") },
            text = {
                Text(
                    text = "Your credential ${revokedCredentialAlert.subject} has been revoked by the issuer.",
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissRevokedCredentialAlert) {
                    Text(text = "OK")
                }
            },
        )
    }
}
