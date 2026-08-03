package com.dev.usdi_wallet.ui.verification

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dev.usdi_wallet.bluetooth.BluetoothProofConnectionStatus
import com.dev.usdi_wallet.bluetooth.BluetoothProofPeer
import com.dev.usdi_wallet.bluetooth.BluetoothProofTransportState
import com.dev.usdi_wallet.ui.common.ClaimInputFormat
import com.dev.usdi_wallet.domain.credential.PredicateOperator
import com.dev.usdi_wallet.domain.verification.VerifiableCredentialType
import com.dev.usdi_wallet.domain.verification.VerificationPollResult
import com.dev.usdi_wallet.domain.verification.VerificationProtocol
import com.dev.usdi_wallet.ui.common.formatClaimName
import com.dev.usdi_wallet.ui.common.isClaimInputCandidate
import com.dev.usdi_wallet.ui.common.isFinalClaimInputValid
import com.dev.usdi_wallet.ui.common.PrimaryButton
import com.dev.usdi_wallet.ui.common.ProtocolBadge
import com.dev.usdi_wallet.ui.common.QrCodeView
import com.dev.usdi_wallet.ui.common.ScreenHeader
import com.dev.usdi_wallet.ui.common.SecondaryButton
import com.dev.usdi_wallet.ui.common.SectionLabel
import com.dev.usdi_wallet.ui.common.WalletCard
import com.dev.usdi_wallet.ui.common.WalletListItem
import com.dev.usdi_wallet.ui.common.isUserVisibleClaim
import com.dev.usdi_wallet.ui.common.predicateInputFormatForClaim
import com.dev.usdi_wallet.ui.theme.WalletColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun VerificationScreen(
    viewModel: VerificationViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = uiState.step is VerificationStep.SelectFields) {
        viewModel.onBackToCredentialTypes()
    }

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
                    bluetoothState = uiState.bluetoothState,
                    onSelected = viewModel::onCredentialTypeSelected,
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
                    onBluetoothConnect = viewModel::onConnectBluetoothPeer,
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
    bluetoothState: BluetoothProofTransportState,
    onSelected: (VerifiableCredentialType) -> Unit,
    onStartBluetoothHolder: () -> Unit,
    onStopBluetoothSession: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            BluetoothHolderPanel(
                state = bluetoothState,
                onReceive = onStartBluetoothHolder,
                onDisconnect = onStopBluetoothSession,
            )
        }
        item { SectionLabel(text = "Available credentials") }
        items(credentialTypes, key = { it.id }) { credentialType ->
            val (tint, bg) = if (credentialType.protocol == VerificationProtocol.EUDI) {
                WalletColors.Primary to WalletColors.PrimaryLight
            } else {
                WalletColors.Success to WalletColors.SuccessLight
            }
            val visibleFieldCount = credentialType.fields.count { isUserVisibleClaim(it.name) }
            WalletListItem(
                icon = Icons.Default.Badge,
                iconTint = tint,
                iconBackground = bg,
                title = credentialType.label,
                subtitle = "$visibleFieldCount verifiable fields",
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
    onBluetoothConnect: () -> Unit,
    onBluetoothContinue: () -> Unit,
    onContinue: () -> Unit,
) {
    val showQrAction = credentialType?.protocol != VerificationProtocol.ANONCREDS
    val visibleFieldSelections = fieldSelections.filter { isUserVisibleClaim(it.schema.name) }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SectionLabel(text = credentialType?.label.orEmpty()) }
            items(visibleFieldSelections, key = { it.schema.name }) { selection ->
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
                    onConnect = onBluetoothConnect,
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
    state: BluetoothProofTransportState,
    onReceive: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val isConnected = state.status == BluetoothProofConnectionStatus.CONNECTED
    val isWaiting = state.status == BluetoothProofConnectionStatus.LISTENING ||
        state.status == BluetoothProofConnectionStatus.CONNECTING

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
                    Text("Bluetooth connection", style = MaterialTheme.typography.titleMedium)
                    Text(bluetoothStatusText(state), style = MaterialTheme.typography.bodySmall)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isConnected) {
                    SecondaryButton(
                        text = "End connection",
                        onClick = onDisconnect,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (isWaiting) {
                    SecondaryButton(
                        text = "Cancel",
                        onClick = onDisconnect,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    PrimaryButton(
                        text = "Receive requests",
                        onClick = onReceive,
                        modifier = Modifier.fillMaxWidth(),
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
    onConnect: () -> Unit,
    onSend: () -> Unit,
) {
    val canSend = isAnonCreds &&
        !isLoading &&
        state.status == BluetoothProofConnectionStatus.CONNECTED
    val canConnect = isAnonCreds &&
        !isLoading &&
        selectedPeerAddress != null &&
        state.status != BluetoothProofConnectionStatus.CONNECTED &&
        state.status != BluetoothProofConnectionStatus.CONNECTING

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
                    Text("Bluetooth connection", style = MaterialTheme.typography.titleMedium)
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
                    text = if (state.status == BluetoothProofConnectionStatus.CONNECTED) {
                        "Send request"
                    } else {
                        "Connect"
                    },
                    onClick = if (state.status == BluetoothProofConnectionStatus.CONNECTED) {
                        onSend
                    } else {
                        onConnect
                    },
                    modifier = Modifier.weight(1f),
                    enabled = if (state.status == BluetoothProofConnectionStatus.CONNECTED) {
                        canSend
                    } else {
                        canConnect
                    },
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
        BluetoothProofConnectionStatus.IDLE -> "Not connected"
        BluetoothProofConnectionStatus.LISTENING -> "Waiting for Bluetooth connection"
        BluetoothProofConnectionStatus.CONNECTING -> "Connecting to ${state.peerName ?: "paired device"}"
        BluetoothProofConnectionStatus.CONNECTED -> state.message ?: "Bluetooth connected"
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
    val inputFormat = predicateInputFormatForClaim(selection.schema.name)

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
            Text(text = formatClaimName(selection.schema.label), style = MaterialTheme.typography.titleMedium)
            Checkbox(checked = selection.checked, onCheckedChange = onChecked)
        }

        if (selection.checked && selection.schema.supportsPredicate) {
            Spacer(modifier = Modifier.height(8.dp))
            PredicateEditor(
                selectedOperator = selection.predicateOperator,
                predicateValue = selection.predicateValue,
                inputFormat = inputFormat,
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
    inputFormat: ClaimInputFormat,
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
            if (inputFormat == ClaimInputFormat.DATE) {
                DatePredicateValuePicker(
                    predicateValue = predicateValue,
                    onValueChanged = onValueChanged,
                )
            } else {
                val isInvalid = predicateValue.isNotBlank() && !isFinalClaimInputValid(predicateValue, inputFormat)
                OutlinedTextField(
                    value = predicateValue,
                    onValueChange = { value ->
                        if (isClaimInputCandidate(value, inputFormat)) {
                            onValueChanged(value)
                        }
                    },
                    label = { Text("Value") },
                    singleLine = true,
                    isError = isInvalid,
                    supportingText = {
                        Text(predicateInputHelpText(inputFormat))
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (inputFormat) {
                            ClaimInputFormat.INTEGER -> KeyboardType.Number
                            else -> KeyboardType.Text
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePredicateValuePicker(
    predicateValue: String,
    onValueChanged: (String) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val isInvalid = predicateValue.isNotBlank() && !isFinalClaimInputValid(predicateValue, ClaimInputFormat.DATE)

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = predicateValue,
            onValueChange = {},
            label = { Text("Date") },
            placeholder = { Text("Select a date") },
            singleLine = true,
            readOnly = true,
            isError = isInvalid,
            supportingText = {
                Text(if (isInvalid) "Select a valid date" else predicateInputHelpText(ClaimInputFormat.DATE))
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { showPicker = true },
        )
    }

    if (showPicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = predicateValue.toUtcStartOfDayMillisOrNull(),
        )

        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    enabled = datePickerState.selectedDateMillis != null,
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedDateMillis ->
                            onValueChanged(selectedDateMillis.toIsoDateString())
                        }
                        showPicker = false
                    },
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun predicateInputHelpText(inputFormat: ClaimInputFormat): String =
    when (inputFormat) {
        ClaimInputFormat.DATE -> "Select a date"
        ClaimInputFormat.INTEGER -> "Use an integer"
        else -> "Enter a value"
    }

private fun String.toUtcStartOfDayMillisOrNull(): Long? =
    runCatching {
        LocalDate.parse(trim())
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
    }.getOrNull()

private fun Long.toIsoDateString(): String =
    Instant.ofEpochMilli(this)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .toString()

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
                val visibleClaims = result.claims.filterKeys(::isUserVisibleClaim)
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
                    if (visibleClaims.isEmpty()) {
                        Text(text = "No attributes disclosed.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        visibleClaims.forEach { (key, value) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(text = formatClaimName(key), style = MaterialTheme.typography.bodyMedium)
                                Text(text = value.toString(), style = MaterialTheme.typography.titleSmall)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
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
