package com.dev.usdi_wallet.ui.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dev.usdi_wallet.ui.common.PrimaryButton
import com.dev.usdi_wallet.ui.common.SecondaryButton
import com.dev.usdi_wallet.ui.theme.WalletColors

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onCompleted: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(WalletColors.Surface)) {
        AnimatedContent(targetState = state.step) { step ->
            when (step) {
                is OnboardingStep.Welcome -> WelcomeStep(
                    onCreateNewWallet = viewModel::onCreateNewWallet,
                    onRestoreWallet = viewModel::onRestoreWallet,
                )
                is OnboardingStep.RestoreWallet -> RestoreWalletStep(
                    isLoading = state.isLoading,
                    error = state.restoreError,
                    onRestoreConfirmed = viewModel::onRestoreConfirmed,
                )
                is OnboardingStep.BiometricSetup -> BiometricSetupStep(
                    isLoading = state.isLoading,
                    onSetup = viewModel::onBiometricSetup,
                )
                is OnboardingStep.Complete -> CompleteStep(
                    skippedProtocols = state.restoreSkippedProtocols,
                    onGetStarted = {
                        onCompleted()
                    },
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep(
    onCreateNewWallet: () -> Unit,
    onRestoreWallet: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(88.dp).background(WalletColors.PrimaryLight, RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Badge,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = WalletColors.Primary,
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "USDI Wallet", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Your secure digital identity wallet",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(48.dp))
        PrimaryButton(text = "Create new wallet", onClick = onCreateNewWallet)
        Spacer(modifier = Modifier.height(12.dp))
        SecondaryButton(text = "Restore from backup", onClick = onRestoreWallet)
    }
}

@Composable
private fun RestoreWalletStep(
    isLoading: Boolean,
    error: String?,
    onRestoreConfirmed: (Uri?, String) -> Unit,
) {
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var passphrase by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri -> selectedUri = uri }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(72.dp).background(WalletColors.PrimaryLight, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Restore, null, tint = WalletColors.Primary, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Restore your wallet",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Select your backup file and enter your passphrase to restore your wallet.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(32.dp))
        SecondaryButton(
            text = if (selectedUri != null) "File selected" else "Select backup file",
            onClick = { filePicker.launch("*/*") },
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = { Text("Passphrase") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = error != null,
            supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(24.dp))
        if (isLoading) {
            CircularProgressIndicator(color = WalletColors.Primary)
        } else {
            PrimaryButton(
                text = "Restore wallet",
                onClick = { onRestoreConfirmed(selectedUri, passphrase) },
                enabled = passphrase.isNotEmpty(),
            )
        }
    }
}

@Composable
private fun BiometricSetupStep(
    isLoading: Boolean,
    onSetup: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(88.dp).background(WalletColors.PrimaryLight, RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Fingerprint, null, tint = WalletColors.Primary, modifier = Modifier.size(40.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Set up biometric authentication",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Secure your wallet with biometric authentication. " +
                    "You'll need this every time you open the app.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(32.dp))
        if (isLoading) {
            CircularProgressIndicator(color = WalletColors.Primary)
        } else {
            PrimaryButton(text = "Set up biometric", onClick = onSetup)
        }
    }
}

@Composable
private fun CompleteStep(
    skippedProtocols: List<String>,
    onGetStarted: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(88.dp).background(WalletColors.SuccessLight, RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.CheckCircle, null, tint = WalletColors.Success, modifier = Modifier.size(40.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "You're all set!", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Your wallet is ready to use. Remember to back up your wallet from Settings.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        if (skippedProtocols.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WalletColors.WarningLight, RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                Text(
                    text = "Some credentials could not be restored automatically " +
                            "(${skippedProtocols.joinToString()}). You may need to re-request them from their issuers.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WalletColors.Warning,
                )
            }
        }
        Spacer(modifier = Modifier.height(48.dp))
        PrimaryButton(text = "Get started", onClick = onGetStarted)
    }
}