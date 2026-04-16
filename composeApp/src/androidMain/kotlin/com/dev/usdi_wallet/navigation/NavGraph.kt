//package com.dev.usdi_wallet.navigation
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.lazy.LazyColumn
//import androidx.compose.foundation.lazy.items
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.unit.dp
//import androidx.lifecycle.viewmodel.compose.viewModel
//import com.dev.usdi_wallet.credential.Credential
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.text.font.FontWeight
//import androidx.navigation.NavController
//import androidx.navigation.NavGraphBuilder
//import androidx.navigation.NavHostController
//import androidx.navigation.compose.NavHost
//import androidx.compose.runtime.Composable
//import androidx.navigation.compose.composable
//import androidx.navigation.compose.rememberNavController
//
//import com.dev.usdi_wallet.ui.contact.ContactScreen
//import com.dev.usdi_wallet.ui.contact.CredentialScreen
//
//
//@Composable
//fun NavGraph() {
//    val navController = rememberNavController()
//
//    NavHost(navController, startDestination = Routes.CONTACT) {
//
//        composable(Routes.CONTACT) {
//            ContactScreen(
//                onNavigate = {navController.navigate(Routes.CREDENTIAL)}
//            )
//        }
//
//        composable(Routes.CREDENTIAL) {
//            CredentialScreen()
//        }
//
//        composable(Routes.VERIFY) {
//            VerificationScreen()
//        }
//    }
//}
