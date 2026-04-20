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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import com.dev.usdi_wallet.credential.Credential
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
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    val currentTab = WalletTab.entries.find { tab ->
        currentRoute?.startsWith(tab.rootRoute.substringBefore("_root")) == true
    } ?: WalletTab.CONTACTS

    MainScreen(
        isReady = uiState.isReady,
        currentTab = currentTab,
        onTabSelected = { tab ->
            navController.navigate(tab.rootRoute) {
                popUpTo(navController.graph.startDestinationId)
                launchSingleTop = true
            }
        },
        navHost = {
            MainNavHost(
                navController = navController,
                contactViewModel = contactViewModel,
                credentialViewModel = credentialViewModel,
                verificationRequestViewModel = verificationRequestViewModel
            )
        }
    )

    // Side effects (unchanged)
    uiState.pendingProofRequests.firstOrNull()?.let {
        ProofRequestSheet(
            request = it,
            onDismiss = viewModel::dismissProofRequest,
            onSelectCredential = { credential ->
                scope.launch { it.onCredentialSelected(credential) }
            }
        )
    }

    uiState.revokedCredentialAlerts.firstOrNull()?.let {
        RevokedCredentialDialog(
            subject = it.subject,
            onDismiss = viewModel::dismissRevokedCredentialAlert
        )
    }
}

@Composable
fun MainScreen(
    isReady: Boolean,
    currentTab: WalletTab,
    onTabSelected: (WalletTab) -> Unit,
    navHost: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            PrimaryTabRow(selectedTabIndex = currentTab.ordinal) {
                WalletTab.entries.forEach { tab ->
                    Tab(
                        selected = currentTab == tab,
                        onClick = { onTabSelected(tab) },
                        text = { Text(tab.title) },
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
fun MainNavHost(
    navController: NavHostController,
    contactViewModel: ContactViewModel,
    credentialViewModel: CredentialViewModel,
    verificationRequestViewModel: VerificationRequestViewModel,
) {
    NavHost(
        navController = navController,
        startDestination = WalletTab.CONTACTS.rootRoute
    ) {

        // ===== CONTACTS GRAPH =====
        navigation(
            startDestination = "contacts/list",
            route = WalletTab.CONTACTS.rootRoute
        ) {
            composable("contacts/list") {
                ContactScreen(contactViewModel)
            }

//            composable("contacts/detail/{id}") { backStack ->
//                val id = backStack.arguments?.getString("id")
//                ContactDetailScreen(id = id!!)
//            }
        }

        // ===== CREDENTIALS GRAPH =====
        navigation(
            startDestination = "credentials/list",
            route = WalletTab.CREDENTIALS.rootRoute
        ) {
            composable("credentials/list") {
                CredentialScreen(credentialViewModel)
            }

//            composable("credentials/detail/{id}") { backStack ->
//                val id = backStack.arguments?.getString("id")
//                CredentialDetailScreen(id = id!!)
//            }
        }

        // ===== VERIFY GRAPH =====
        navigation(
            startDestination = "verify/list",
            route = WalletTab.VERIFY.rootRoute
        ) {
            composable("verify/list") {
                VerificationRequestScreen(verificationRequestViewModel)
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
