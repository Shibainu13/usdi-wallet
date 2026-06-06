package com.dev.usdi_wallet.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.dev.usdi_wallet.ui.auth.LockScreen
import com.dev.usdi_wallet.ui.auth.LockState
import com.dev.usdi_wallet.ui.auth.LockViewModel
import com.dev.usdi_wallet.ui.contact.ContactScreen
import com.dev.usdi_wallet.ui.contact.ContactViewModel
import com.dev.usdi_wallet.ui.credential.CredentialScreen
import com.dev.usdi_wallet.ui.credential.CredentialViewModel
import com.dev.usdi_wallet.ui.verification.VerificationRequestScreen
import com.dev.usdi_wallet.ui.verification.VerificationRequestViewModel

@Composable
fun MainNavHost(
    navController: NavHostController,
    lockViewModel: LockViewModel,
    contactViewModel: ContactViewModel,
    credentialViewModel: CredentialViewModel,
    verificationRequestViewModel: VerificationRequestViewModel,
) {
    val lockState = lockViewModel.state.collectAsStateWithLifecycle()
    NavHost(
        navController = navController,
        startDestination = WalletTab.AUTH.rootRoute
    ) {

        composable(route = WalletTab.AUTH.rootRoute) {
            LockScreen(viewModel = lockViewModel)
            LaunchedEffect(lockState) {
                if (lockState == LockState.Authenticated) {
                    navController.navigate(WalletTab.CONTACTS.rootRoute) {
                        popUpTo(WalletTab.AUTH.rootRoute) { inclusive = true }
                    }
                }
            }
        }

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