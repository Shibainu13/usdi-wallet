package com.dev.usdi_wallet.ui.verification

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.bluetooth.BluetoothPresentProofTransport
import com.dev.usdi_wallet.bluetooth.BluetoothProofConnectionStatus
import com.dev.usdi_wallet.bluetooth.BluetoothProofFrame
import com.dev.usdi_wallet.bluetooth.BluetoothProofPeer
import com.dev.usdi_wallet.bluetooth.BluetoothProofTransportState
import com.dev.usdi_wallet.common.ErrorHandler
import com.dev.usdi_wallet.domain.credential.PredicateOperator
import com.dev.usdi_wallet.domain.protocol.Protocol
import com.dev.usdi_wallet.domain.verification.RequestedField
import com.dev.usdi_wallet.domain.verification.VerifiableCredentialType
import com.dev.usdi_wallet.domain.verification.VerifiableFieldSchema
import com.dev.usdi_wallet.domain.verification.VerificationManager
import com.dev.usdi_wallet.domain.verification.VerificationPollResult
import com.dev.usdi_wallet.domain.verification.VerificationProtocol
import com.dev.usdi_wallet.domain.verification.VerificationSession
import com.dev.usdi_wallet.eudi.EudiProtocol
import com.dev.usdi_wallet.hyperledger_identus.IdentusAnonBluetoothProofManager
import com.dev.usdi_wallet.hyperledger_identus.LocalAnonCredBluetoothExchange
import com.dev.usdi_wallet.hyperledger_identus.IdentusAnonProtocol
import com.dev.usdi_wallet.ui.common.isSystemIndexClaim
import com.dev.usdi_wallet.ui.common.isUserVisibleClaim
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.collections.emptyList

sealed class VerificationStep {
    data object SelectCredentialType : VerificationStep()
    data object SelectFields : VerificationStep()
    data object ShowQrWaiting : VerificationStep()
    data object Result : VerificationStep()
}

data class FieldSelection(
    val schema: VerifiableFieldSchema,
    val checked: Boolean = false,
    val predicateOperator: PredicateOperator? = null,
    val predicateValue: String = ""
)

data class VerificationUiState(
    val step: VerificationStep = VerificationStep.SelectCredentialType,
    val availableCredentialTypes: List<VerifiableCredentialType> = emptyList(),
    val selectedCredentialType: VerifiableCredentialType? = null,
    val fieldSelections: List<FieldSelection> = emptyList(),
    val qrContent: String? = null,
    val waitingMessage: String = "Waiting for the holder to scan and respond",
    val pollResult: VerificationPollResult = VerificationPollResult.Pending,
    val isLoading: Boolean = false,
    val error: String? = null,
    val bluetoothPeers: List<BluetoothProofPeer> = emptyList(),
    val selectedBluetoothPeerAddress: String? = null,
    val bluetoothState: BluetoothProofTransportState = BluetoothProofTransportState(),
)

class VerificationViewModel(application: Application) : AndroidViewModel(application) {
    private val protocols = listOf(
        IdentusAnonProtocol.getInstance(),
        EudiProtocol.getInstance(),
    )
    private val _uiState = MutableStateFlow(VerificationUiState())
    val uiState = _uiState.asStateFlow()
    private var activeSession: VerificationSession? = null
    private var pollJob: Job? = null
    private var credentialTypeToManager: Map<VerifiableCredentialType, VerificationManager?> = emptyMap()
    private val bluetoothTransport = BluetoothPresentProofTransport(application, viewModelScope)
    private val bluetoothProofManager = IdentusAnonBluetoothProofManager()
    private val bluetoothRequestSentAtByThread = mutableMapOf<String, Long>()
    private val bluetoothFlowStartAtByThread = mutableMapOf<String, Long>()

    init {
        loadCredentialTypes()
        loadBluetoothPeers()
        observeBluetoothState()
        LocalAnonCredBluetoothExchange.registerPresentationSender { message ->
            bluetoothTransport.send(
                BluetoothProofFrame(
                    messageType = BluetoothProofFrame.PRESENTATION,
                    id = message.messageId,
                    thid = message.threadId,
                    messageJson = message.messageJson,
                )
            )
        }
        LocalAnonCredBluetoothExchange.registerProblemReportSender { report ->
            bluetoothTransport.send(
                BluetoothProofFrame(
                    messageType = BluetoothProofFrame.PROBLEM_REPORT,
                    id = report.messageId,
                    thid = report.threadId,
                    description = report.description,
                )
            )
            bluetoothTransport.close()
        }
    }

    private fun loadCredentialTypes() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val pairs = protocols.flatMap { protocol ->
                protocol.verificationManager?.getSupportedCredentialTypes().orEmpty().map { credentialType ->
                    credentialType to protocol.verificationManager
                }
            }
            credentialTypeToManager = pairs.toMap()
            _uiState.update { it.copy(isLoading = false, availableCredentialTypes = pairs.map { pair -> pair.first }) }
        }
    }

    fun onCredentialTypeSelected(credentialType: VerifiableCredentialType) {
        _uiState.value = _uiState.value.copy(
            selectedCredentialType = credentialType,
            fieldSelections = credentialType.fields.map { schema ->
                FieldSelection(
                    schema = schema,
                    checked = credentialType.protocol == VerificationProtocol.ANONCREDS &&
                        isSystemIndexClaim(schema.name),
                )
            },
            step = VerificationStep.SelectFields,
        )
    }

    fun onFieldChecked(fieldName: String, checked: Boolean) {
        if (isSystemIndexClaim(fieldName)) return
        updateField(fieldName) { it.copy(checked = checked) }
    }

    fun onFieldPredicateOperatorChanged(fieldName: String, operator: PredicateOperator?) {
        updateField(fieldName) { it.copy(predicateOperator = operator) }
    }

    fun onFieldPredicateValueChanged(fieldName: String, value: String) {
        updateField(fieldName) { it.copy(predicateValue = value) }
    }

    private fun updateField(fieldName: String, transform: (FieldSelection) -> FieldSelection) {
        _uiState.update { state ->
            state.copy(
                fieldSelections = state.fieldSelections.map {
                    if (it.schema.name == fieldName) transform(it) else it
                },
            )
        }
    }

    fun onBackToCredentialTypes() {
        _uiState.value = _uiState.value.copy(
            step = VerificationStep.SelectCredentialType,
            selectedCredentialType = null,
            fieldSelections = emptyList(),
        )
    }

    fun onStartVerification() {
        val state = _uiState.value
        val credentialType = state.selectedCredentialType ?: run {
            _uiState.value = _uiState.value.copy(
                error = "Select a credential type first"
            )
            return
        }
        if (credentialType.protocol == VerificationProtocol.ANONCREDS) {
            _uiState.value = _uiState.value.copy(
                error = "AnonCreds QR proof invitations were removed. Use Bluetooth local proof."
            )
            return
        }
        val selectedFields = selectedUserVisibleFields(state)
        if (selectedFields.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                error = "Select at least one field"
            )
            return
        }
        val verificationManager = credentialTypeToManager[credentialType] ?: run {
            _uiState.value = _uiState.value.copy(error = "No verifier available for this credential type")
            return
        }
        val requestedFields = requestedFieldSelections(state).map {
            RequestedField(
                field = it.schema,
                predicateOperator = it.predicateOperator,
                predicateValue = it.predicateValue.ifBlank { null },
            )
        }
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val session = verificationManager.startVerification(credentialType, requestedFields)
                activeSession = session
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    qrContent = session.qrContent,
                    waitingMessage = "Waiting for the holder to scan and respond",
                    step = VerificationStep.ShowQrWaiting,
                    pollResult = VerificationPollResult.Pending,
                )
                pollJob = launch {
                    session.results.collect { result ->
                        _uiState.value = _uiState.value.copy(pollResult = result)
                        if (result !is VerificationPollResult.Pending) {
                            _uiState.value = _uiState.value.copy(step = VerificationStep.Result)
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.e(VerificationViewModel::class.toString()) {
                    "Failed to start verification: $e"
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = ErrorHandler.handleError("Failed to start verification", e)
                )
            }
        }
    }

    fun loadBluetoothPeers() {
        runCatching { bluetoothTransport.bondedPeers() }
            .onSuccess { peers ->
                _uiState.update { state ->
                    state.copy(
                        bluetoothPeers = peers,
                        selectedBluetoothPeerAddress = state.selectedBluetoothPeerAddress
                            ?: peers.firstOrNull()?.address,
                    )
                }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(error = ErrorHandler.handleError("Failed to load paired Bluetooth devices", error))
                }
            }
    }

    fun onBluetoothPeerSelected(address: String) {
        val shouldDisconnect = _uiState.value.selectedBluetoothPeerAddress != address &&
            (_uiState.value.bluetoothState.status == BluetoothProofConnectionStatus.CONNECTED ||
                _uiState.value.bluetoothState.status == BluetoothProofConnectionStatus.CONNECTING)
        _uiState.update { it.copy(selectedBluetoothPeerAddress = address) }
        if (shouldDisconnect) {
            bluetoothTransport.close()
        }
    }

    fun onStartBluetoothHolder() {
        _uiState.update { it.copy(error = null) }
        bluetoothTransport.startListening(::handleBluetoothFrame)
    }

    fun onStopBluetoothSession() {
        bluetoothTransport.close()
    }

    fun onConnectBluetoothPeer() {
        val state = _uiState.value
        val credentialType = state.selectedCredentialType ?: run {
            _uiState.update { it.copy(error = "Select a credential type first") }
            return
        }
        if (credentialType.protocol != VerificationProtocol.ANONCREDS) {
            _uiState.update { it.copy(error = "Bluetooth local proof currently supports AnonCreds only") }
            return
        }
        val peerAddress = state.selectedBluetoothPeerAddress ?: run {
            _uiState.update { it.copy(error = "Select a paired Bluetooth device") }
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                error = null,
                pollResult = VerificationPollResult.Pending,
            )
        }
        bluetoothTransport.connect(
            peerAddress = peerAddress,
            onFrame = ::handleBluetoothFrame,
            onConnected = {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        waitingMessage = "Bluetooth connected",
                    )
                }
            },
        )
    }

    fun onStartBluetoothVerification() {
        val state = _uiState.value
        val credentialType = state.selectedCredentialType ?: run {
            _uiState.update { it.copy(error = "Select a credential type first") }
            return
        }
        if (credentialType.protocol != VerificationProtocol.ANONCREDS) {
            _uiState.update { it.copy(error = "Bluetooth local proof currently supports AnonCreds only") }
            return
        }
        if (state.bluetoothState.status != BluetoothProofConnectionStatus.CONNECTED) {
            _uiState.update { it.copy(error = "Connect to the holder first") }
            return
        }
        val selectedFields = state.fieldSelections.filter { it.checked }
        if (selectedFields.isEmpty()) {
            _uiState.update { it.copy(error = "Select at least one field") }
            return
        }
        val requestedFields = selectedFields.map {
            RequestedField(
                field = it.schema,
                predicateOperator = it.predicateOperator,
                predicateValue = it.predicateValue.ifBlank { null },
            )
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                error = null,
                pollResult = VerificationPollResult.Pending,
            )
        }
        viewModelScope.launch {
            runCatching {
                sendBluetoothProofRequest(credentialType, requestedFields)
            }.onFailure { error ->
                Logger.e(VerificationViewModel::class.toString()) {
                    "Failed to send Bluetooth proof request: ${error.message ?: error::class.simpleName}"
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = ErrorHandler.handleError("Failed to send Bluetooth proof request", error),
                        waitingMessage = "Bluetooth proof request failed",
                    )
                }
            }
        }
    }

    private suspend fun sendBluetoothProofRequest(
        credentialType: VerifiableCredentialType,
        requestedFields: List<RequestedField>,
    ) {
        val flowStartMs = nowMs()
        logPerf(
            event = "proof_flow_start",
            details = "role=verifier credentialType=${credentialType.id} fields=${requestedFields.size}",
        )
        Logger.d(VerificationViewModel::class.toString()) {
            "Bluetooth verifier connected; creating proof request with ${requestedFields.size} fields"
        }
        _uiState.update { it.copy(waitingMessage = "Building Bluetooth proof request") }

        val requestBuildStartMs = nowMs()
        val request = bluetoothProofManager.createRequest(credentialType, requestedFields)
        logPerf(
            event = "request_build_end",
            details = "role=verifier messageId=${request.messageId} thid=${request.threadId} chars=${request.messageJson.length} durationMs=${nowMs() - requestBuildStartMs}",
        )
        Logger.d(VerificationViewModel::class.toString()) {
            "Bluetooth proof request created messageId=${request.messageId}, thid=${request.threadId}, chars=${request.messageJson.length}"
        }

        _uiState.update { it.copy(waitingMessage = "Sending proof request over Bluetooth") }
        val sendStartMs = nowMs()
        bluetoothTransport.send(
            BluetoothProofFrame(
                messageType = BluetoothProofFrame.REQUEST_PRESENTATION,
                id = request.messageId,
                thid = request.threadId,
                messageJson = request.messageJson,
            )
        )
        val sentAtMs = nowMs()
        bluetoothRequestSentAtByThread[request.threadId] = sentAtMs
        bluetoothFlowStartAtByThread[request.threadId] = flowStartMs
        logPerf(
            event = "request_send_call_end",
            details = "role=verifier messageId=${request.messageId} thid=${request.threadId} durationMs=${sentAtMs - sendStartMs}",
        )

        Logger.d(VerificationViewModel::class.toString()) {
            "Bluetooth proof request sent thid=${request.threadId}"
        }
        _uiState.update {
            it.copy(
                isLoading = false,
                qrContent = null,
                waitingMessage = "Waiting for the holder to approve over Bluetooth",
                step = VerificationStep.ShowQrWaiting,
                pollResult = VerificationPollResult.Pending,
            )
        }
    }

    private fun selectedUserVisibleFields(state: VerificationUiState): List<FieldSelection> =
        state.fieldSelections.filter { it.checked && isUserVisibleClaim(it.schema.name) }

    private fun requestedFieldSelections(state: VerificationUiState): List<FieldSelection> =
        state.fieldSelections.filter {
            it.checked || isAutoRequiredIndexField(it, state.selectedCredentialType?.protocol)
        }

    private fun isAutoRequiredIndexField(
        selection: FieldSelection,
        protocol: VerificationProtocol?,
    ): Boolean =
        protocol == VerificationProtocol.ANONCREDS && isSystemIndexClaim(selection.schema.name)

    fun onCancelVerification() {
        pollJob?.cancel()
        bluetoothTransport.close()
        viewModelScope.launch {
            activeSession?.let { session ->
                val verificationManager = credentialTypeToManager[_uiState.value.selectedCredentialType] ?: run {
                    _uiState.value = _uiState.value.copy(error = "No verifier available for this credential type")
                    return@launch
                }
                verificationManager.cancelVerification(session)
            }
        }
        resetToStart()
    }

    fun onStartNewVerification() {
        pollJob?.cancel()
        bluetoothTransport.close()
        resetToStart()
    }

    private fun resetToStart() {
        activeSession = null
        _uiState.update { state ->
            VerificationUiState(
                availableCredentialTypes = state.availableCredentialTypes,
                bluetoothPeers = state.bluetoothPeers,
                selectedBluetoothPeerAddress = state.selectedBluetoothPeerAddress,
                bluetoothState = state.bluetoothState,
            )
        }
    }

    fun onErrorShown() = _uiState.update { it.copy(error = null) }

    fun onScanError(message: String) {
        _uiState.update { it.copy(error = message) }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
        bluetoothTransport.close()
        LocalAnonCredBluetoothExchange.clearPresentationSender()
        LocalAnonCredBluetoothExchange.clearProblemReportSender()
    }

    private fun observeBluetoothState() {
        viewModelScope.launch {
            bluetoothTransport.state.collect { bluetoothState ->
                _uiState.update { state ->
                    val terminal = bluetoothState.status == BluetoothProofConnectionStatus.ERROR ||
                        bluetoothState.status == BluetoothProofConnectionStatus.CLOSED
                    state.copy(
                        bluetoothState = bluetoothState,
                        isLoading = if (terminal) false else state.isLoading,
                        error = if (bluetoothState.status == BluetoothProofConnectionStatus.ERROR) {
                            bluetoothState.message ?: state.error
                        } else {
                            state.error
                        },
                    )
                }
            }
        }
    }

    private suspend fun handleBluetoothFrame(frame: BluetoothProofFrame) {
        when (frame.messageType) {
            BluetoothProofFrame.REQUEST_PRESENTATION -> {
                val holderHandleStartMs = nowMs()
                logPerf(
                    event = "request_handle_start",
                    details = "role=holder frameId=${frame.id} thid=${frame.thid.orEmpty()} payloadChars=${frame.messageJson?.length ?: 0}",
                )
                bluetoothTransport.holdOpenUntilLocalResponse("Bluetooth proof request received")
                val messageJson = frame.messageJson ?: run {
                    sendProblemReport(frame.thid, "Bluetooth proof request was missing a DIDComm message")
                    return
                }
                runCatching {
                    val request = bluetoothProofManager.receiveRequest(messageJson)
                    logPerf(
                        event = "request_queue_end",
                        details = "role=holder messageId=${request.messageId} thid=${request.threadId} durationMs=${nowMs() - holderHandleStartMs}",
                    )
                    val ackStartMs = nowMs()
                    bluetoothTransport.send(
                        BluetoothProofFrame(
                            messageType = BluetoothProofFrame.ACK,
                            thid = frame.thid,
                            description = "Proof request queued",
                        )
                    )
                    logPerf(
                        event = "request_ack_send_call_end",
                        details = "role=holder thid=${frame.thid.orEmpty()} durationMs=${nowMs() - ackStartMs}",
                    )
                    _uiState.update {
                        it.copy(
                            bluetoothState = it.bluetoothState.copy(
                                message = "Bluetooth proof request queued",
                            )
                        )
                    }
                }.onFailure { error ->
                    sendProblemReport(frame.thid, error.message ?: "Failed to process Bluetooth proof request")
                    _uiState.update {
                        it.copy(error = ErrorHandler.handleError("Failed to process Bluetooth proof request", error))
                    }
                }
            }
            BluetoothProofFrame.PRESENTATION -> {
                val messageJson = frame.messageJson ?: run {
                    _uiState.update {
                        it.copy(
                            step = VerificationStep.Result,
                            pollResult = VerificationPollResult.Failed("Bluetooth presentation was missing a DIDComm message"),
                        )
                    }
                    return
                }
                val verifyStartMs = nowMs()
                val result = bluetoothProofManager.verifyPresentation(
                    messageJson = messageJson,
                    credentialType = _uiState.value.selectedCredentialType,
                )
                val verifyEndMs = nowMs()
                val threadId = result.threadId ?: frame.thid
                logPerf(
                    event = "presentation_verify_end",
                    details = "role=verifier messageId=${result.messageId} thid=${threadId.orEmpty()} valid=${result.isValid} attributes=${result.attributes.size} durationMs=${verifyEndMs - verifyStartMs}",
                )
                threadId?.let { nonNullThreadId ->
                    bluetoothFlowStartAtByThread.remove(nonNullThreadId)?.let { startMs ->
                        logPerf(
                            event = "proof_flow_end",
                            details = "role=verifier thid=$nonNullThreadId valid=${result.isValid} totalMs=${verifyEndMs - startMs}",
                        )
                    }
                }
                val ackDescription = if (result.isValid) {
                    "Presentation verified"
                } else {
                    "Presentation verification failed"
                }
                runCatching {
                    bluetoothTransport.send(
                        BluetoothProofFrame(
                            messageType = BluetoothProofFrame.ACK,
                            thid = result.threadId ?: frame.thid,
                            description = ackDescription,
                        )
                    )
                }.onFailure { error ->
                    Logger.w(VerificationViewModel::class.toString()) {
                        "Bluetooth presentation ACK could not be sent after verification result: ${error.message ?: error::class.simpleName}"
                    }
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        step = VerificationStep.Result,
                        pollResult = if (result.isValid) {
                            VerificationPollResult.Success(result.attributes)
                        } else {
                            VerificationPollResult.Failed(result.error ?: "Presentation verification failed")
                        },
                    )
                }
            }
            BluetoothProofFrame.ACK -> {
                val description = frame.description ?: "Bluetooth message acknowledged"
                if (description == "Proof request queued") {
                    frame.thid?.let { threadId ->
                        bluetoothRequestSentAtByThread.remove(threadId)?.let { sentAtMs ->
                            logPerf(
                                event = "request_ack_rtt_end",
                                details = "role=verifier thid=$threadId durationMs=${nowMs() - sentAtMs}",
                            )
                        }
                    }
                }
                val isTerminalPresentationAck = description == "Presentation verified" ||
                    description == "Presentation verification failed"
                _uiState.update {
                    it.copy(
                        bluetoothState = it.bluetoothState.copy(
                            message = description,
                        )
                    )
                }
                if (isTerminalPresentationAck) {
                    bluetoothTransport.close()
                }
            }
            BluetoothProofFrame.PROBLEM_REPORT -> {
                val description = frame.description ?: "Bluetooth proof exchange failed"
                logPerf(
                    event = "problem_report_received",
                    details = "thid=${frame.thid.orEmpty()} description=${description.replace('\n', ' ')}",
                )
                _uiState.update {
                    if (it.step is VerificationStep.ShowQrWaiting) {
                        it.copy(
                            isLoading = false,
                            step = VerificationStep.Result,
                            waitingMessage = description,
                            pollResult = VerificationPollResult.Failed(description),
                            bluetoothState = it.bluetoothState.copy(message = description),
                        )
                    } else {
                        it.copy(
                            error = description,
                            bluetoothState = it.bluetoothState.copy(message = description),
                        )
                    }
                }
            }
        }
    }

    private suspend fun sendProblemReport(threadId: String?, description: String) {
        logPerf(
            event = "problem_report_send",
            details = "thid=${threadId.orEmpty()} description=${description.replace('\n', ' ')}",
        )
        runCatching {
            bluetoothTransport.send(
                BluetoothProofFrame(
                    messageType = BluetoothProofFrame.PROBLEM_REPORT,
                    thid = threadId,
                    description = description,
                )
            )
        }
    }

    private fun nowMs(): Long = SystemClock.elapsedRealtimeNanos() / 1_000_000

    private fun logPerf(event: String, details: String) {
        Logger.i(PERF_TAG) { "event=$event $details tMs=${nowMs()}" }
    }

    private companion object {
        const val PERF_TAG = "BtPerf"
    }
}
