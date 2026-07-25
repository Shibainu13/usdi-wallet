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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.dev.usdi_wallet.bluetooth.BluetoothProofConnectionStatus
import com.dev.usdi_wallet.bluetooth.BluetoothProofPeer
import com.dev.usdi_wallet.bluetooth.BluetoothProofTransportState
import com.dev.usdi_wallet.domain.credential.PredicateOperator
import com.dev.usdi_wallet.domain.verification.VerifiableCredentialType
import com.dev.usdi_wallet.domain.verification.VerificationPollResult
import com.dev.usdi_wallet.domain.verification.VerificationProtocol
import com.dev.usdi_wallet.ui.common.PrimaryButton
import com.dev.usdi_wallet.ui.common.ProtocolBadge
import com.dev.usdi_wallet.ui.common.QrCodeView
import com.dev.usdi_wallet.ui.common.ScreenHeader
import com.dev.usdi_wallet.ui.common.SecondaryButton
import com.dev.usdi_wallet.ui.common.SectionLabel
import com.dev.usdi_wallet.ui.common.WalletCard
import com.dev.usdi_wallet.ui.common.WalletListItem
import com.dev.usdi_wallet.ui.theme.WalletColors

@Composable
fun VerificationScreen(
    viewModel: VerificationViewModel,
) {
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
                leadingAction = if (uiState.step is VerificationStep.SelectFields) {
                    {
                        IconButton(onClick = viewModel::onBackToCredentialTypes) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = WalletColors.TextPrimary,
                            )
                        }
                    }
                } else {
                    null
                },
            )

            when (uiState.step) {
                is VerificationStep.SelectCredentialType -> SelectCredentialTypeStep(
                    credentialTypes = uiState.availableCredentialTypes,
                    bluetoothPeers = uiState.bluetoothPeers,
                    selectedBluetoothPeerAddress = uiState.selectedBluetoothPeerAddress,
                    bluetoothState = uiState.bluetoothState,
                    onSelected = viewModel::onCredentialTypeSelected,
                    onRefreshBluetoothPeers = viewModel::loadBluetoothPeers,
                    onBluetoothPeerSelected = viewModel::onBluetoothPeerSelected,
                    onStartBluetoothHolder = viewModel::onStartBluetoothHolder,
                    onStopBluetoothSession = viewModel::onStopBluetoothSession,
                )
                is VerificationStep.SelectFields -> SelectFieldsStep(
                    credentialType = uiState.selectedCredentialType,
                    fieldSelections = uiState.fieldSelections,
                    isLoading = uiState.isLoading,
                    bluetoothPeers = uiState.bluetoothPeers,
                    selectedBluetoothPeerAddress = uiState.selectedBluetoothPeerAddress,
                    bluetoothState = uiState.bluetoothState,
                    onFieldChecked = viewModel::onFieldChecked,
                    onPredicateOperatorChanged = viewModel::onFieldPredicateOperatorChanged,
                    onPredicateValueChanged = viewModel::onFieldPredicateValueChanged,
                    onRefreshBluetoothPeers = viewModel::loadBluetoothPeers,
                    onBluetoothPeerSelected = viewModel::onBluetoothPeerSelected,
                    onBluetoothContinue = viewModel::onStartBluetoothVerification,
                    onContinue = viewModel::onStartVerification,
                )
                is VerificationStep.ShowQrWaiting -> ShowQrWaitingStep(
                    qrContent = uiState.qrContent,
                    waitingMessage = uiState.waitingMessage,
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
    bluetoothPeers: List<BluetoothProofPeer>,
    selectedBluetoothPeerAddress: String?,
    bluetoothState: BluetoothProofTransportState,
    onSelected: (VerifiableCredentialType) -> Unit,
    onRefreshBluetoothPeers: () -> Unit,
    onBluetoothPeerSelected: (String) -> Unit,
    onStartBluetoothHolder: () -> Unit,
    onStopBluetoothSession: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            BluetoothHolderPanel(
                peers = bluetoothPeers,
                selectedPeerAddress = selectedBluetoothPeerAddress,
                state = bluetoothState,
                onRefreshPeers = onRefreshBluetoothPeers,
                onPeerSelected = onBluetoothPeerSelected,
                onListen = onStartBluetoothHolder,
                onStop = onStopBluetoothSession,
            )
        }
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
    bluetoothPeers: List<BluetoothProofPeer>,
    selectedBluetoothPeerAddress: String?,
    bluetoothState: BluetoothProofTransportState,
    onFieldChecked: (String, Boolean) -> Unit,
    onPredicateOperatorChanged: (String, PredicateOperator?) -> Unit,
    onPredicateValueChanged: (String, String) -> Unit,
    onRefreshBluetoothPeers: () -> Unit,
    onBluetoothPeerSelected: (String) -> Unit,
    onBluetoothContinue: () -> Unit,
    onContinue: () -> Unit,
) {
    val showQrAction = credentialType?.protocol != VerificationProtocol.ANONCREDS

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
            item {
                BluetoothVerifierPanel(
                    peers = bluetoothPeers,
                    selectedPeerAddress = selectedBluetoothPeerAddress,
                    state = bluetoothState,
                    isAnonCreds = credentialType?.protocol == VerificationProtocol.ANONCREDS,
                    isLoading = isLoading,
                    onRefreshPeers = onRefreshBluetoothPeers,
                    onPeerSelected = onBluetoothPeerSelected,
                    onSend = onBluetoothContinue,
                )
            }
            item { Spacer(modifier = Modifier.height(12.dp)) }
        }

        if (showQrAction) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = WalletColors.Primary)
                    }
                } else {
                    PrimaryButton(text = "Generate QR", onClick = onContinue)
                }
            }
        }
    }
}

@Composable
private fun BluetoothHolderPanel(
    peers: List<BluetoothProofPeer>,
    selectedPeerAddress: String?,
    state: BluetoothProofTransportState,
    onRefreshPeers: () -> Unit,
    onPeerSelected: (String) -> Unit,
    onListen: () -> Unit,
    onStop: () -> Unit,
) {
    val isActive = state.status == BluetoothProofConnectionStatus.LISTENING ||
        state.status == BluetoothProofConnectionStatus.CONNECTING ||
        state.status == BluetoothProofConnectionStatus.CONNECTED

    WalletCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = WalletColors.Primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("Bluetooth local proof", style = MaterialTheme.typography.titleMedium)
                    Text(bluetoothStatusText(state), style = MaterialTheme.typography.bodySmall)
                }
            }

            BluetoothPeerSelector(
                peers = peers,
                selectedPeerAddress = selectedPeerAddress,
                onPeerSelected = onPeerSelected,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SecondaryButton(
                    text = "Refresh",
                    onClick = onRefreshPeers,
                    modifier = Modifier.weight(1f),
                )
                if (isActive) {
                    SecondaryButton(
                        text = "Stop",
                        onClick = onStop,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    PrimaryButton(
                        text = "Listen",
                        onClick = onListen,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BluetoothVerifierPanel(
    peers: List<BluetoothProofPeer>,
    selectedPeerAddress: String?,
    state: BluetoothProofTransportState,
    isAnonCreds: Boolean,
    isLoading: Boolean,
    onRefreshPeers: () -> Unit,
    onPeerSelected: (String) -> Unit,
    onSend: () -> Unit,
) {
    val canSend = isAnonCreds &&
        !isLoading &&
        selectedPeerAddress != null &&
        state.status != BluetoothProofConnectionStatus.CONNECTING &&
        state.status != BluetoothProofConnectionStatus.LISTENING

    WalletCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = WalletColors.Success,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("Send over Bluetooth", style = MaterialTheme.typography.titleMedium)
                    Text(bluetoothStatusText(state), style = MaterialTheme.typography.bodySmall)
                }
            }

            if (!isAnonCreds) {
                Text(
                    text = "Bluetooth local proof is available for AnonCreds requests.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            BluetoothPeerSelector(
                peers = peers,
                selectedPeerAddress = selectedPeerAddress,
                onPeerSelected = onPeerSelected,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SecondaryButton(
                    text = "Refresh",
                    onClick = onRefreshPeers,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = "Send",
                    onClick = onSend,
                    modifier = Modifier.weight(1f),
                    enabled = canSend,
                )
            }
        }
    }
}

@Composable
private fun BluetoothPeerSelector(
    peers: List<BluetoothProofPeer>,
    selectedPeerAddress: String?,
    onPeerSelected: (String) -> Unit,
) {
    if (peers.isEmpty()) {
        Text(
            text = "No paired Bluetooth devices found.",
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        peers.forEach { peer ->
            FilterChip(
                selected = peer.address == selectedPeerAddress,
                onClick = { onPeerSelected(peer.address) },
                label = {
                    Text(
                        text = peer.name,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
            )
        }
    }
}

private fun bluetoothStatusText(state: BluetoothProofTransportState): String =
    when (state.status) {
        BluetoothProofConnectionStatus.IDLE -> "Idle"
        BluetoothProofConnectionStatus.LISTENING -> "Listening for a paired device"
        BluetoothProofConnectionStatus.CONNECTING -> "Connecting to ${state.peerName ?: "paired device"}"
        BluetoothProofConnectionStatus.CONNECTED -> state.message ?: "Connected to ${state.peerName ?: "paired device"}"
        BluetoothProofConnectionStatus.CLOSED -> "Bluetooth session closed"
        BluetoothProofConnectionStatus.ERROR -> state.message ?: "Bluetooth session failed"
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
            .background(WalletColors.White, MaterialTheme.shapes.large)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = selection.schema.label, style = MaterialTheme.typography.titleMedium)
            Checkbox(checked = selection.checked, onCheckedChange = onChecked)
        }

        if (selection.checked && selection.schema.supportsPredicate) {
            Spacer(modifier = Modifier.height(8.dp))
            PredicateEditor(
                selectedOperator = selection.predicateOperator,
                predicateValue = selection.predicateValue,
                onOperatorSelected = onPredicateOperatorChanged,
                onValueChanged = onPredicateValueChanged,
            )
        }
    }
}

@Composable
private fun PredicateEditor(
    selectedOperator: PredicateOperator?,
    predicateValue: String,
    onOperatorSelected: (PredicateOperator?) -> Unit,
    onValueChanged: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PredicateOperator.entries.forEach { operator ->
                val selected = selectedOperator == operator
                FilterChip(
                    selected = selected,
                    onClick = { onOperatorSelected(if (selected) null else operator) },
                    label = { Text(operator.symbol) },
                )
            }
        }
        if (selectedOperator != null) {
            OutlinedTextField(
                value = predicateValue,
                onValueChange = onValueChanged,
                label = { Text("Value") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ShowQrWaitingStep(
    qrContent: String?,
    waitingMessage: String,
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
                    .background(WalletColors.White, MaterialTheme.shapes.large)
                    .padding(20.dp),
            ) {
                QrCodeView(content = qrContent, size = 220)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        CircularProgressIndicator(color = WalletColors.Primary)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = waitingMessage,
            style = MaterialTheme.typography.bodyMedium,
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
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = WalletColors.Success,
                    modifier = Modifier.height(64.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Verification successful", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WalletColors.White, MaterialTheme.shapes.large)
                        .padding(16.dp),
                ) {
                    result.claims.forEach { (key, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(text = key, style = MaterialTheme.typography.bodyMedium)
                            Text(text = value.toString(), style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
            is VerificationPollResult.Failed -> {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = WalletColors.Danger,
                    modifier = Modifier.height(64.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Verification failed", style = MaterialTheme.typography.headlineSmall)
                result.reason?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = it, style = MaterialTheme.typography.bodyMedium)
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
