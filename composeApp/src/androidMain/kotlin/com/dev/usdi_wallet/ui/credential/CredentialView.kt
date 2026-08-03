package com.dev.usdi_wallet.ui.credential

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dev.usdi_wallet.domain.credential.Credential
import com.dev.usdi_wallet.ui.common.ProtocolBadge
import com.dev.usdi_wallet.ui.common.ScreenHeader
import com.dev.usdi_wallet.ui.common.SectionLabel
import com.dev.usdi_wallet.ui.common.StatusBadge
import com.dev.usdi_wallet.ui.common.WalletDivider
import com.dev.usdi_wallet.ui.common.WalletListItem
import com.dev.usdi_wallet.ui.common.formatClaimDisplayValue
import com.dev.usdi_wallet.ui.common.formatClaimName
import com.dev.usdi_wallet.ui.common.isUserVisibleClaim
import com.dev.usdi_wallet.ui.theme.WalletColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialScreen(
    viewModel: CredentialViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val credentials by viewModel.credentials.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onErrorShown()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(WalletColors.Surface)) {
        Column(modifier = Modifier.fillMaxSize()) {
            val activeCount = credentials.count { !it.revoked }
            ScreenHeader(
                title = "Credentials",
                subtitle = when {
                    credentials.isEmpty() -> "No credentials yet"
                    activeCount == 1 -> "1 active credential"
                    else -> "$activeCount active credentials"
                },
            )

            if (credentials.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "No credentials yet",
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            color = WalletColors.TextSecondary,
                        )
                        Text(
                            text = "Credentials you receive will appear here",
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Active credentials
                    val active = credentials.filter { !it.revoked }
                    val revoked = credentials.filter { it.revoked }

                    if (active.isNotEmpty()) {
                        item { SectionLabel(text = "Active") }
                        items(active, key = { it.id }) { credential ->
                            CredentialListItem(
                                credential = credential,
                                onClick = { viewModel.onCredentialClicked(credential) },
                            )
                        }
                    }

                    if (revoked.isNotEmpty()) {
                        item { SectionLabel(text = "Revoked") }
                        items(revoked, key = { it.id }) { credential ->
                            CredentialListItem(
                                credential = credential,
                                onClick = { viewModel.onCredentialClicked(credential) },
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(20.dp)) }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    // Detail bottom sheet
    uiState.selectedCredential?.let { credential ->
        ModalBottomSheet(
            onDismissRequest = viewModel::onDetailDismissed,
            sheetState = sheetState,
            containerColor = WalletColors.White,
        ) {
            CredentialDetailSheet(
                credential = credential,
                onDismiss = viewModel::onDetailDismissed,
            )
        }
    }
}

@Composable
private fun CredentialListItem(
    credential: Credential,
    onClick: () -> Unit,
) {
    val isRevoked = credential.revoked
    val (iconTint, iconBg) = if (credential.protocol.contains("OPENID", ignoreCase = true)) {
        WalletColors.Primary to WalletColors.PrimaryLight
    } else {
        WalletColors.Success to WalletColors.SuccessLight
    }

    WalletListItem(
        icon = Icons.Default.Badge,
        iconTint = if (isRevoked) WalletColors.Danger else iconTint,
        iconBackground = if (isRevoked) WalletColors.DangerLight else iconBg,
        title = credential.subject ?: credential.issuer,
        subtitle = credential.issuer,
        badge = if (isRevoked) {
            { StatusBadge(text = "Revoked") }
        } else null,
        onClick = onClick,
    )
}

@Composable
private fun CredentialDetailSheet(
    credential: Credential,
    onDismiss: () -> Unit,
) {
    val isRevoked = credential.revoked
    val visibleClaims = credential.claims.filter { isUserVisibleClaim(it.name) }
    val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.85f
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxSheetHeight)
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = credential.subject ?: "Credential",
                    style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProtocolBadge(protocol = credential.protocol)
                    if (isRevoked) StatusBadge(text = "Revoked")
                }
            }
        }

        WalletDivider()

        // Issuer
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(text = "Issuer", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            Text(
                text = credential.issuer,
                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
            )
        }

        WalletDivider()

        // Claims
        Text(
            text = "Claims",
            style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isRevoked) Modifier.alpha(0.5f) else Modifier),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            visibleClaims.forEachIndexed { index, claim ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = formatClaimName(claim.name),
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = formatClaimDisplayValue(claim.name, claim.value),
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                    )
                }
                if (index < visibleClaims.lastIndex) WalletDivider()
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isRevoked) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WalletColors.DangerLight, androidx.compose.material3.MaterialTheme.shapes.medium)
                    .padding(12.dp),
            ) {
                Text(
                    text = "This credential has been revoked by the issuer and can no longer be used for verification.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = WalletColors.Danger,
                )
            }
        }

        TextButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(text = "Close", color = WalletColors.Primary)
        }
    }
}
