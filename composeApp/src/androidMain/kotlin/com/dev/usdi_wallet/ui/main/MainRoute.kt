package com.dev.usdi_wallet.ui.main

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dev.usdi_wallet.ui.auth.LockScreen
import com.dev.usdi_wallet.ui.auth.LockState
import com.dev.usdi_wallet.ui.auth.LockViewModel
import com.dev.usdi_wallet.ui.common.QrScannerResultSource
import com.dev.usdi_wallet.ui.contact.ContactViewModel
import com.dev.usdi_wallet.ui.credential.CredentialViewModel
import com.dev.usdi_wallet.ui.onboarding.OnboardingScreen
import com.dev.usdi_wallet.ui.onboarding.OnboardingViewModel
import com.dev.usdi_wallet.ui.settings.SettingsViewModel
import com.dev.usdi_wallet.ui.verification.VerificationViewModel
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel

private data class ScannerErrorMessage(
    val title: String,
    val detail: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainRoute(
    viewModel: MainViewModel,
    contactViewModel: ContactViewModel = composeViewModel(),
    credentialViewModel: CredentialViewModel = composeViewModel(),
    verificationViewModel: VerificationViewModel = composeViewModel(),
    settingsViewModel: SettingsViewModel = composeViewModel(),
    lockViewModel: LockViewModel = composeViewModel(),
    onboardingViewModel: OnboardingViewModel = composeViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lockState by lockViewModel.state.collectAsStateWithLifecycle()
    val isOnboardingComplete by viewModel.isOnboardingComplete.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    if (isOnboardingComplete == null) return
    else if (isOnboardingComplete == false) {
        OnboardingScreen(
            viewModel = onboardingViewModel,
            onCompleted = {  }
        )
        return
    }
    if (lockState !is LockState.Authenticated) {
        LockScreen(viewModel = lockViewModel)
        return
    }

    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route
    val startDestinationRoute = WalletTab.CREDENTIALS.rootRoute
    val snackbarHostState = remember { SnackbarHostState() }
    var scanError by remember { mutableStateOf<ScannerErrorMessage?>(null) }
    val isScanRoute = currentRoute == SCAN_ROUTE
    val currentTab = WalletTab.entries.find { tab ->
        currentRoute?.startsWith(tab.rootRoute.substringBefore("_root")) == true
    } ?: WalletTab.CREDENTIALS
    val navigateToCredentials = {
        navController.navigate(WalletTab.CREDENTIALS.rootRoute) {
            popUpTo(startDestinationRoute) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    MainScreen(
        serviceNotice = if (isScanRoute) null else uiState.serviceNotice,
        currentTab = currentTab,
        snackbarHostState = snackbarHostState,
        showBottomBar = !isScanRoute,
        onTabSelected = { tab ->
            navController.navigate(tab.rootRoute) {
                popUpTo(startDestinationRoute)
                launchSingleTop = true
            }
        },
        onScanSelected = {
            scanError = null
            navController.navigate(SCAN_ROUTE) {
                launchSingleTop = true
            }
        },
        navHost = {
            MainNavHost(
                navController = navController,
                contactViewModel = contactViewModel,
                credentialViewModel = credentialViewModel,
                verificationViewModel = verificationViewModel,
                settingsViewModel = settingsViewModel,
                scanErrorTitle = scanError?.title,
                scanErrorMessage = scanError?.detail,
                onScanErrorDismiss = { scanError = null },
                onScanResult = { content, source ->
                    scanError = null
                    DeepLinkRouter.getInstance().handle(
                        link = content,
                        onSuccess = { result ->
                            scanError = null
                            navController.popBackStack()
                            if (result.contentType == DeepLinkContentType.Credential) {
                                navigateToCredentials()
                            }
                        },
                        onError = { message -> scanError = scannerErrorMessage(source, message) },
                    )
                },
                onScanClose = {
                    scanError = null
                    navController.popBackStack()
                },
            )
        }
    )

    if (!isScanRoute) {
        uiState.pendingProofRequests.firstOrNull()?.let {
            key(it.id) {
                ProofRequestSheet(
                    request = it,
                    onDismiss = viewModel::dismissProofRequest,
                    onSelectCredential = { credential, disclosedClaimLabels ->
                        scope.launch { it.onCredentialSelected(credential, disclosedClaimLabels) }
                    },
                    onDeny = {
                        scope.launch { it.onDenied() }
                    },
                )
            }
        }

        uiState.revokedCredentialAlerts.firstOrNull()?.let {
            RevokedCredentialDialog(
                subject = it.subject,
                onDismiss = viewModel::dismissRevokedCredentialAlert
            )
        }
    }
}

private fun scannerErrorMessage(
    source: QrScannerResultSource,
    routerMessage: String,
): ScannerErrorMessage {
    val details = routerMessage.ifBlank { "Unable to handle this content." }
    return when (source) {
        QrScannerResultSource.QrCode -> ScannerErrorMessage(
            title = "Can't scan QR code",
            detail = "Please try again.",
        )
        QrScannerResultSource.Url -> ScannerErrorMessage(
            title = "URL problem",
            detail = "The pasted URL could not be opened. Check that it is a valid URL.",
        )
    }
}
