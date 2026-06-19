package com.dev.usdi_wallet.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dev.usdi_wallet.ui.common.SectionLabel
import com.dev.usdi_wallet.ui.common.ScreenHeader
import com.dev.usdi_wallet.ui.common.SettingsRow
import com.dev.usdi_wallet.ui.common.WalletCard
import com.dev.usdi_wallet.ui.common.WalletDivider
import com.dev.usdi_wallet.ui.theme.WalletColors

@Composable
fun SettingsScreen(
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

        // Backup & Restore
        SectionLabel(
            text = "Backup & Restore",
            modifier = Modifier.padding(horizontal = 20.dp),
        )
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

        // About
        SectionLabel(
            text = "About",
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        WalletCard(modifier = Modifier.padding(horizontal = 20.dp)) {
            Column {
                SettingsRow(
                    title = "About USDI Wallet",
                    subtitle = "Version, thesis info",
                    onClick = onAboutClick,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Danger zone
        SectionLabel(
            text = "Danger Zone",
            modifier = Modifier.padding(horizontal = 20.dp),
        )
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