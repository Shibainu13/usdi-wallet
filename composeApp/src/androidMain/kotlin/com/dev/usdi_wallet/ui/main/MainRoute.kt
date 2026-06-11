package com.dev.usdi_wallet.ui.main

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dev.usdi_wallet.ui.auth.LockScreen
import com.dev.usdi_wallet.ui.auth.LockState
import com.dev.usdi_wallet.ui.auth.LockViewModel
import com.dev.usdi_wallet.ui.contact.ContactViewModel
import com.dev.usdi_wallet.ui.credential.CredentialViewModel
import com.dev.usdi_wallet.ui.onboarding.OnboardingScreen
import com.dev.usdi_wallet.ui.onboarding.OnboardingViewModel
import com.dev.usdi_wallet.ui.verification.VerificationRequestViewModel
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainRoute(
    viewModel: MainViewModel,
    contactViewModel: ContactViewModel = composeViewModel(),
    credentialViewModel: CredentialViewModel = composeViewModel(),
    verificationRequestViewModel: VerificationRequestViewModel = composeViewModel(),
    lockViewModel: LockViewModel = composeViewModel(),
    onboardingViewModel: OnboardingViewModel = composeViewModel()
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
    } else {
        if (lockState !is LockState.Authenticated) {
            LockScreen(viewModel = lockViewModel)
            return
        }
    }

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
            onSelectCredential = { credential, disclosedClaimLabels ->
                scope.launch { it.onCredentialSelected(credential, disclosedClaimLabels) }
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