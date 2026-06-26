package com.dev.usdi_wallet.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dev.usdi_wallet.ui.common.PrimaryButton
import com.dev.usdi_wallet.ui.common.ScreenHeader
import com.dev.usdi_wallet.ui.common.SecondaryButton
import com.dev.usdi_wallet.ui.common.SectionLabel
import com.dev.usdi_wallet.ui.common.SettingsRow
import com.dev.usdi_wallet.ui.common.WalletCard
import com.dev.usdi_wallet.ui.common.WalletDivider
import com.dev.usdi_wallet.ui.theme.WalletColors
import java.io.File

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (uiState.screen) {
        is SettingsScreenState.Main -> SettingsMainScreen(
            onBackupClick = viewModel::onBackupClicked,
            onRestoreClick = viewModel::onRestoreClicked,
            onAboutClick = viewModel::onAboutClicked,
        )
        is SettingsScreenState.Backup -> BackupScreen(viewModel = viewModel, onBack = viewModel::onBackToMain)
        is SettingsScreenState.Restore -> RestoreScreen(viewModel = viewModel, onBack = viewModel::onBackToMain)
        is SettingsScreenState.About -> AboutScreen(onBack = viewModel::onBackToMain)
    }
}

@Composable
private fun SettingsMainScreen(
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onAboutClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WalletColors.Surface)
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(title = "Settings")

        SectionLabel(text = "Backup & Restore", modifier = Modifier.padding(horizontal = 20.dp))
        WalletCard(modifier = Modifier.padding(horizontal = 20.dp)) {
            Column {
                SettingsRow(
                    title = "Back up wallet",
                    subtitle = "Export encrypted backup file",
                    onClick = onBackupClick,
                )
                WalletDivider()
                SettingsRow(
                    title = "Restore wallet",
                    subtitle = "Import from backup file",
                    onClick = onRestoreClick,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        SectionLabel(text = "About", modifier = Modifier.padding(horizontal = 20.dp))
        WalletCard(modifier = Modifier.padding(horizontal = 20.dp)) {
            SettingsRow(
                title = "About USDI Wallet",
                subtitle = "Version, thesis info",
                onClick = onAboutClick,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        SectionLabel(text = "Danger Zone", modifier = Modifier.padding(horizontal = 20.dp))
        WalletCard(modifier = Modifier.padding(horizontal = 20.dp)) {
            SettingsRow(
                title = "Reset wallet",
                subtitle = "Removes all data from this device",
                titleColor = WalletColors.Danger,
                onClick = { /* TODO */ },
            )
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

// ── Backup screen ─────────────────────────────────────────────────────────

@Composable
private fun BackupScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri: Uri? ->
        val payload = uiState.backupEncryptedPayload
        if (uri != null && payload != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(payload.toByteArray()) }
            viewModel.onBackupSaved()
        }
    }

    LaunchedEffect(uiState.backupEncryptedPayload) {
        if (uiState.backupEncryptedPayload != null && !uiState.backupComplete) {
            saveFileLauncher.launch("usdi-wallet-backup-${System.currentTimeMillis()}.bak")
        }
    }

    LaunchedEffect(uiState.backupError) {
        uiState.backupError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onErrorShown()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(WalletColors.Surface)) {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsSubHeader(title = "Back up wallet", onBack = onBack)

            if (uiState.backupComplete) {
                BackupCompleteContent()
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    IconBanner(
                        icon = Icons.Default.CloudUpload,
                        text = "Choose a passphrase to encrypt this backup. " +
                                "You'll need this exact passphrase to restore your wallet later.",
                    )

                    OutlinedTextField(
                        value = uiState.backupPassphrase,
                        onValueChange = viewModel::onBackupPassphraseChanged,
                        label = { Text("Passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = uiState.backupError != null,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = uiState.backupPassphraseConfirm,
                        onValueChange = viewModel::onBackupPassphraseConfirmChanged,
                        label = { Text("Confirm passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = uiState.backupError != null,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    if (uiState.isLoading) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = WalletColors.Primary)
                        }
                    } else {
                        PrimaryButton(
                            text = "Create backup",
                            onClick = viewModel::onCreateBackup,
                            enabled = uiState.backupPassphrase.isNotEmpty() &&
                                    uiState.backupPassphraseConfirm.isNotEmpty(),
                        )
                    }
                }
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun BackupCompleteContent() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(72.dp).background(WalletColors.SuccessLight, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.CheckCircle, null, tint = WalletColors.Success, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Backup saved", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Keep this file and your passphrase somewhere safe. Both are required to restore your wallet.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

// ── Restore screen ────────────────────────────────────────────────────────

@Composable
private fun RestoreScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri -> viewModel.onRestoreFileSelected(uri) }

    LaunchedEffect(uiState.restoreError) {
        uiState.restoreError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onErrorShown()
        }
    }

    if (uiState.restoreConfirmPending) {
        AlertDialog(
            onDismissRequest = viewModel::onRestoreCancelled,
            title = { Text("Overwrite wallet data?") },
            text = {
                Text("Restoring from a backup will overwrite the wallet currently on this device. This cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = viewModel::onRestoreConfirmed) {
                    Text("Restore", color = WalletColors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onRestoreCancelled) { Text("Cancel") }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(WalletColors.Surface)) {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsSubHeader(title = "Restore wallet", onBack = onBack)

            if (uiState.restoreComplete) {
                RestoreCompleteContent(
                    succeeded = uiState.restoreSucceeded,
                    skipped = uiState.restoreSkipped,
                    failed = uiState.restoreFailed,
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    IconBanner(
                        icon = Icons.Default.CloudDownload,
                        text = "This will overwrite the wallet on this device with the contents of the backup file.",
                    )

                    SecondaryButton(
                        text = if (uiState.pendingRestoreUri != null) "File selected" else "Select backup file",
                        onClick = { filePicker.launch("*/*") },
                    )

                    OutlinedTextField(
                        value = uiState.restorePassphrase,
                        onValueChange = viewModel::onRestorePassphraseChanged,
                        label = { Text("Passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = uiState.restoreError != null,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    if (uiState.isLoading) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = WalletColors.Primary)
                        }
                    }
                }
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun RestoreCompleteContent(
    succeeded: List<String>,
    skipped: List<String>,
    failed: List<String>,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val allOk = failed.isEmpty()
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(
                    if (allOk) WalletColors.SuccessLight else WalletColors.DangerLight,
                    RoundedCornerShape(18.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.CheckCircle,
                null,
                tint = if (allOk) WalletColors.Success else WalletColors.Danger,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (allOk) "Wallet restored" else "Restore completed with errors",
            style = MaterialTheme.typography.headlineSmall,
        )
        if (succeeded.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Restored: ${succeeded.joinToString()}", style = MaterialTheme.typography.bodyMedium)
        }
        if (skipped.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Re-request needed: ${skipped.joinToString()}",
                style = MaterialTheme.typography.bodyMedium,
                color = WalletColors.Warning,
            )
        }
        if (failed.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Failed: ${failed.joinToString()}",
                style = MaterialTheme.typography.bodyMedium,
                color = WalletColors.Danger,
            )
        }
    }
}

// ── About screen ──────────────────────────────────────────────────────────

@Composable
private fun AboutScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(WalletColors.Surface)) {
        SettingsSubHeader(title = "About", onBack = onBack)
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "USDI Wallet", style = MaterialTheme.typography.titleLarge)
            Text(text = "Version 1.0 (thesis build)", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "A self-sovereign identity wallet supporting EUDI (OpenID4VCI/VP) and " +
                        "Hyperledger Identus (AnonCreds) credential protocols.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

// ── Shared sub-components ─────────────────────────────────────────────────

@Composable
private fun SettingsSubHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
        }
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun IconBanner(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WalletColors.PrimaryLight, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, null, tint = WalletColors.Primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = WalletColors.Primary)
    }
}