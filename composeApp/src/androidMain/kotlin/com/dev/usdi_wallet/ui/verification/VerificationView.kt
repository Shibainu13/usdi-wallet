package com.dev.usdi_wallet.ui.verification

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dev.usdi_wallet.common.QrCodeView
import com.dev.usdi_wallet.domain.credential.PredicateOperator
import com.dev.usdi_wallet.domain.verification.VerifiableCredentialType
import com.dev.usdi_wallet.domain.verification.VerificationPollResult
import com.dev.usdi_wallet.domain.verification.VerificationProtocol
import com.dev.usdi_wallet.ui.common.PrimaryButton
import com.dev.usdi_wallet.ui.common.ProtocolBadge
import com.dev.usdi_wallet.ui.common.ScreenHeader
import com.dev.usdi_wallet.ui.common.SecondaryButton
import com.dev.usdi_wallet.ui.common.SectionLabel
import com.dev.usdi_wallet.ui.common.WalletListItem
import com.dev.usdi_wallet.ui.theme.WalletColors

@Composable
fun VerificationScreen(viewModel: VerificationViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onErrorShown()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(WalletColors.Surface)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Verify",
                subtitle = when (uiState.step) {
                    is VerificationStep.SelectCredentialType -> "Choose a credential to verify"
                    is VerificationStep.SelectFields -> "Choose fields to request"
                    is VerificationStep.ShowQrWaiting -> "Waiting for response"
                    is VerificationStep.Result -> "Verification result"
                },
            )

            when (uiState.step) {
                is VerificationStep.SelectCredentialType -> SelectCredentialTypeStep(
                    credentialTypes = uiState.availableCredentialTypes,
                    onSelected = viewModel::onCredentialTypeSelected,
                )
                is VerificationStep.SelectFields -> SelectFieldsStep(
                    credentialType = uiState.selectedCredentialType,
                    fieldSelections = uiState.fieldSelections,
                    isLoading = uiState.isLoading,
                    onFieldChecked = viewModel::onFieldChecked,
                    onPredicateOperatorChanged = viewModel::onFieldPredicateOperatorChanged,
                    onPredicateValueChanged = viewModel::onFieldPredicateValueChanged,
                    onBack = viewModel::onBackToCredentialTypes,
                    onContinue = viewModel::onStartVerification,
                )
                is VerificationStep.ShowQrWaiting -> ShowQrWaitingStep(
                    qrContent = uiState.qrContent,
                    onCancel = viewModel::onCancelVerification,
                )
                is VerificationStep.Result -> ResultStep(
                    result = uiState.pollResult,
                    onStartNew = viewModel::onStartNewVerification,
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun SelectCredentialTypeStep(
    credentialTypes: List<VerifiableCredentialType>,
    onSelected: (VerifiableCredentialType) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SectionLabel(text = "Available credentials") }
        items(credentialTypes, key = { it.id }) { credentialType ->
            val (tint, bg) = if (credentialType.protocol == VerificationProtocol.EUDI) {
                WalletColors.Primary to WalletColors.PrimaryLight
            } else {
                WalletColors.Success to WalletColors.SuccessLight
            }
            WalletListItem(
                icon = Icons.Default.Badge,
                iconTint = tint,
                iconBackground = bg,
                title = credentialType.label,
                subtitle = "${credentialType.fields.size} verifiable fields",
                badge = { ProtocolBadge(protocol = credentialType.protocol.name) },
                onClick = { onSelected(credentialType) },
            )
        }
        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
private fun SelectFieldsStep(
    credentialType: VerifiableCredentialType?,
    fieldSelections: List<FieldSelection>,
    isLoading: Boolean,
    onFieldChecked: (String, Boolean) -> Unit,
    onPredicateOperatorChanged: (String, PredicateOperator?) -> Unit,
    onPredicateValueChanged: (String, String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SectionLabel(text = credentialType?.label.orEmpty()) }
            items(fieldSelections, key = { it.schema.name }) { selection ->
                FieldSelectionRow(
                    selection = selection,
                    onChecked = { onFieldChecked(selection.schema.name, it) },
                    onPredicateOperatorChanged = { onPredicateOperatorChanged(selection.schema.name, it) },
                    onPredicateValueChanged = { onPredicateValueChanged(selection.schema.name, it) },
                )
            }
            item { Spacer(modifier = Modifier.height(12.dp)) }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SecondaryButton(text = "Back", onClick = onBack, modifier = Modifier.weight(1f))
            if (isLoading) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = WalletColors.Primary)
                }
            } else {
                PrimaryButton(text = "Generate QR", onClick = onContinue, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FieldSelectionRow(
    selection: FieldSelection,
    onChecked: (Boolean) -> Unit,
    onPredicateOperatorChanged: (PredicateOperator?) -> Unit,
    onPredicateValueChanged: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(WalletColors.White, androidx.compose.material3.MaterialTheme.shapes.large)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = selection.schema.label, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            Checkbox(checked = selection.checked, onCheckedChange = onChecked)
        }
    }
}

@Composable
private fun ShowQrWaitingStep(
    qrContent: String?,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (qrContent != null) {
            Box(
                modifier = Modifier
                    .background(WalletColors.White, androidx.compose.material3.MaterialTheme.shapes.large)
                    .padding(20.dp),
            ) {
                QrCodeView(content = qrContent, size = 220)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        CircularProgressIndicator(color = WalletColors.Primary)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Waiting for the holder to scan and respond",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(24.dp))
        SecondaryButton(text = "Cancel", onClick = onCancel)
    }
}

@Composable
private fun ResultStep(
    result: VerificationPollResult,
    onStartNew: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (result) {
            is VerificationPollResult.Success -> {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = WalletColors.Success,
                    modifier = Modifier.height(64.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Verification successful", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WalletColors.White, androidx.compose.material3.MaterialTheme.shapes.large)
                        .padding(16.dp),
                ) {
                    result.claims.forEach { (key, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(text = key, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                            Text(text = value.toString(), style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
            is VerificationPollResult.Failed -> {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = WalletColors.Danger,
                    modifier = Modifier.height(64.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Verification failed", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
                result.reason?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = it, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                }
            }
            is VerificationPollResult.Pending -> {
                CircularProgressIndicator(color = WalletColors.Primary)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        PrimaryButton(text = "Start new verification", onClick = onStartNew)
    }
}