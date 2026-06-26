package com.dev.usdi_wallet.ui.main

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.dev.usdi_wallet.ui.contact.ContactScreen
import com.dev.usdi_wallet.ui.contact.ContactViewModel
import com.dev.usdi_wallet.ui.credential.CredentialScreen
import com.dev.usdi_wallet.ui.credential.CredentialViewModel
import com.dev.usdi_wallet.ui.settings.SettingsScreen
import com.dev.usdi_wallet.ui.settings.SettingsViewModel
import com.dev.usdi_wallet.ui.verification.VerificationScreen
import com.dev.usdi_wallet.ui.verification.VerificationViewModel

@Composable
fun MainNavHost(
    navController: NavHostController,
    contactViewModel: ContactViewModel,
    credentialViewModel: CredentialViewModel,
    verificationViewModel: VerificationViewModel,
    settingsViewModel: SettingsViewModel,
) {
    NavHost(
        navController = navController,
        startDestination = WalletTab.CREDENTIALS.rootRoute,
    ) {
        navigation(
            startDestination = "credentials/list",
            route = WalletTab.CREDENTIALS.rootRoute,
        ) {
            composable("credentials/list") {
                CredentialScreen(credentialViewModel)
            }
        }

        navigation(
            startDestination = "contacts/list",
            route = WalletTab.CONTACTS.rootRoute,
        ) {
            composable("contacts/list") {
                ContactScreen(contactViewModel)
            }
        }

        navigation(
            startDestination = "verify/list",
            route = WalletTab.VERIFY.rootRoute,
        ) {
            composable("verify/list") {
                VerificationScreen(verificationViewModel)
            }
        }

        navigation(
            startDestination = "settings/main",
            route = WalletTab.SETTINGS.rootRoute,
        ) {
            composable("settings/main") {
                SettingsScreen(settingsViewModel)
            }
        }
    }
}