package com.dev.usdi_wallet.ui.main

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navigation
//import com.dev.usdi_wallet.ui.contact.ContactScreen
//import com.dev.usdi_wallet.ui.contact.ContactViewModel
import com.dev.usdi_wallet.ui.credential.CredentialScreen
import com.dev.usdi_wallet.ui.credential.CredentialViewModel
import com.dev.usdi_wallet.ui.verification.VerificationRequestScreen
import com.dev.usdi_wallet.ui.verification.VerificationRequestViewModel

@Composable
fun MainNavHost(
    navController: NavHostController,
//    contactViewModel: ContactViewModel,
    credentialViewModel: CredentialViewModel,
    verificationRequestViewModel: VerificationRequestViewModel,
) {
    NavHost(
        navController = navController,
        startDestination = WalletTab.CREDENTIALS.rootRoute
    ) {



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