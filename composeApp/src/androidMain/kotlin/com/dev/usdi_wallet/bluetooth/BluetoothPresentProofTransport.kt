package com.dev.usdi_wallet.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.SystemClock
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import kotlin.coroutines.coroutineContext

data class BluetoothProofPeer(
    val name: String,
    val address: String,
)

enum class BluetoothProofConnectionStatus {
    IDLE,
    LISTENING,
    CONNECTING,
    CONNECTED,
    CLOSED,
    ERROR,
}

data class BluetoothProofTransportState(
    val status: BluetoothProofConnectionStatus = BluetoothProofConnectionStatus.IDLE,
    val peerName: String? = null,
    val peerAddress: String? = null,
    val message: String? = null,
)

data class BluetoothProofFrame(
    val messageType: String,
    val id: String = UUID.randomUUID().toString(),
    val thid: String? = null,
    val messageJson: String? = null,
    val description: String? = null,
) {
    fun toJsonString(): String {
        val payload = JSONObject()
        messageJson?.let { payload.put("messageJson", it) }
        description?.let { payload.put("description", it) }

        return JSONObject()
            .put("protocol", PROTOCOL)
            .put("messageType", messageType)
            .put("id", id)
            .put("thid", thid)
            .put("payload", payload)
            .toString()
    }

    companion object {
        const val PROTOCOL = "local-present-proof/1.0"
        const val REQUEST_PRESENTATION = "request-presentation"
        const val PRESENTATION = "presentation"
        const val ACK = "ack"
        const val PROBLEM_REPORT = "problem-report"

        fun fromJsonString(value: String): BluetoothProofFrame {
            val root = JSONObject(value)
            require(root.optString("protocol") == PROTOCOL) {
                "Unsupported Bluetooth proof protocol: ${root.optString("protocol")}"
            }

            val payload = root.optJSONObject("payload") ?: JSONObject()
            return BluetoothProofFrame(
                messageType = root.getString("messageType"),
                id = root.optString("id").ifBlank { UUID.randomUUID().toString() },
                thid = root.optString("thid").ifBlank { null },
                messageJson = payload.optString("messageJson").ifBlank { null },
                description = payload.optString("description").ifBlank { null },
            )
        }
    }
}

class BluetoothPresentProofTransport(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val writeLock = Any()
    private val chunkBuffers = mutableMapOf<String, ChunkAccumulator>()
    private val _state = MutableStateFlow(BluetoothProofTransportState())
    private var connectionJob: Job? = null
    private var socket: BluetoothSocket? = null
    private var serverSockets: List<BluetoothServerSocket> = emptyList()
    @Volatile
    private var holdOpenUntilUserStop = false
    @Volatile
    private var waitingForLocalResponse = false

    val state: StateFlow<BluetoothProofTransportState> = _state.asStateFlow()

    @SuppressLint("MissingPermission")
    fun bondedPeers(): List<BluetoothProofPeer> {
        val adapter = requireAdapter()
        return adapter.bondedDevices
            .map { device ->
                BluetoothProofPeer(
                    name = device.name ?: device.address,
                    address = device.address,
                )
            }
            .sortedWith(compareBy<BluetoothProofPeer> { it.name }.thenBy { it.address })
    }

    @SuppressLint("MissingPermission")
    fun startListening(onFrame: suspend (BluetoothProofFrame) -> Unit) {
        close()
        connectionJob = scope.launch(Dispatchers.IO) {
            val listenStartMs = nowMs()
            logPerf(
                event = "listen_start",
                details = "role=holder",
            )
            val acceptedSocket = runCatching {
                val adapter = requireAdapter()
                _state.value = BluetoothProofTransportState(
                    status = BluetoothProofConnectionStatus.LISTENING,
                    message = "Waiting for Bluetooth connection",
                )
                adapter.cancelDiscovery()
                acceptIncomingSocket(adapter)
            }.getOrElse { error ->
                if (isActive) {
                    logPerf(
                        event = "listen_failed",
                        details = "role=holder durationMs=${nowMs() - listenStartMs} error=${error.shortName()}",
                    )
                    setError("Bluetooth receive setup failed: ${error.message ?: error::class.simpleName}")
                }
                return@launch
            }
            logPerf(
                event = "accept_end",
                details = "role=holder durationMs=${nowMs() - listenStartMs} peer=${acceptedSocket.remoteDevice?.address.orEmpty()}",
            )

            runCatching {
                openSocket(acceptedSocket, onFrame)
            }.onFailure { error ->
                if (isActive) {
                    setError("Bluetooth session failed: ${error.message ?: error::class.simpleName}")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(
        peerAddress: String,
        onFrame: suspend (BluetoothProofFrame) -> Unit,
        onConnected: suspend () -> Unit,
    ) {
        close()
        connectionJob = scope.launch(Dispatchers.IO) {
            val connectStartMs = nowMs()
            logPerf(
                event = "connect_start",
                details = "role=verifier peer=$peerAddress",
            )
            val connectedSocket = runCatching {
                val adapter = requireAdapter()
                val device = adapter.getRemoteDevice(peerAddress)
                _state.value = BluetoothProofTransportState(
                    status = BluetoothProofConnectionStatus.CONNECTING,
                    peerName = device.name ?: peerAddress,
                    peerAddress = peerAddress,
                    message = "Connecting over Bluetooth",
                )
                adapter.cancelDiscovery()
                if (device.bondState != BluetoothDevice.BOND_BONDED) {
                    error("Bluetooth device is not paired. Pair it in Android settings first.")
                }
                connectSocket(adapter, device)
            }.getOrElse { error ->
                if (isActive) {
                    logPerf(
                        event = "connect_failed",
                        details = "role=verifier peer=$peerAddress durationMs=${nowMs() - connectStartMs} error=${error.shortName()}",
                    )
                    setError("Bluetooth connect failed: ${error.message ?: error::class.simpleName}")
                }
                return@launch
            }
            logPerf(
                event = "connect_end",
                details = "role=verifier peer=$peerAddress durationMs=${nowMs() - connectStartMs}",
            )

            runCatching {
                openSocket(connectedSocket, onFrame, onConnected)
            }.onFailure { error ->
                if (isActive) {
                    setError("Bluetooth session failed: ${error.message ?: error::class.simpleName}")
                }
            }
        }
    }

    suspend fun send(frame: BluetoothProofFrame) {
        val activeSocket = socket ?: error("Bluetooth session is not connected")
        withContext(Dispatchers.IO) {
            val releaseLocalResponseHold = frame.messageType == BluetoothProofFrame.PRESENTATION ||
                frame.messageType == BluetoothProofFrame.PROBLEM_REPORT
            try {
                val frameJson = frame.toJsonString()
                val bytes = frameJson.toByteArray(StandardCharsets.UTF_8)
                val sendStartMs = nowMs()
                Logger.d(BluetoothPresentProofTransport::class.toString()) {
                    "Sending Bluetooth proof frame type=${frame.messageType}, id=${frame.id}, thid=${frame.thid}, bytes=${bytes.size}"
                }
                var chunkCount = 1
                if (bytes.size <= MAX_FRAME_BYTES) {
                    activeSocket.writeLine(frameJson)
                } else {
                    val encoded = Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(bytes)
                    val chunks = encoded.chunked(MAX_CHUNK_CHARS)
                    chunkCount = chunks.size
                    chunks.forEachIndexed { index, chunk ->
                        activeSocket.writeLine(
                            JSONObject()
                                .put("messageId", frame.id)
                                .put("chunkIndex", index)
                                .put("chunkCount", chunks.size)
                                .put("bytes", chunk)
                                .toString()
                        )
                    }
                }
                logPerf(
                    event = "send_end",
                    details = "type=${frame.messageType} id=${frame.id} thid=${frame.thid.orEmpty()} bytes=${bytes.size} chunks=$chunkCount durationMs=${nowMs() - sendStartMs}",
                )
                Logger.d(BluetoothPresentProofTransport::class.toString()) {
                    "Sent Bluetooth proof frame type=${frame.messageType}, id=${frame.id}"
                }
            } finally {
                if (releaseLocalResponseHold) {
                    waitingForLocalResponse = false
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun acceptIncomingSocket(adapter: BluetoothAdapter): BluetoothSocket = supervisorScope {
        var lastError: Throwable? = null
        val servers = SocketSecurity.values().mapNotNull { security ->
            runCatching {
                ListeningServer(
                    security = security,
                    serverSocket = when (security) {
                        SocketSecurity.SECURE -> adapter.listenUsingRfcommWithServiceRecord(
                            SERVICE_NAME,
                            SERVICE_UUID,
                        )
                        SocketSecurity.INSECURE_FALLBACK -> adapter.listenUsingInsecureRfcommWithServiceRecord(
                            "$SERVICE_NAME fallback",
                            INSECURE_FALLBACK_SERVICE_UUID,
                        )
                    },
                )
            }.onFailure { error ->
                lastError = error
                Logger.w(BluetoothPresentProofTransport::class.toString()) {
                    "${security.label} Bluetooth receive socket could not start: ${error.message ?: error::class.simpleName}"
                }
            }.getOrNull()
        }
        if (servers.isEmpty()) {
            throwAcceptFailure(lastError)
        }

        serverSockets = servers.map { it.serverSocket }
        val acceptResults = Channel<AcceptAttempt>(capacity = servers.size)
        val acceptJobs = servers.map { server ->
            launch(Dispatchers.IO) {
                val result = runCatching { server.serverSocket.accept() }
                acceptResults.trySend(
                    AcceptAttempt(
                        security = server.security,
                        socket = result.getOrNull(),
                        error = result.exceptionOrNull(),
                    )
                )
            }
        }

        try {
            repeat(servers.size) {
                val attempt = acceptResults.receive()
                attempt.socket?.let { acceptedSocket ->
                    Logger.d(BluetoothPresentProofTransport::class.toString()) {
                        "Accepted ${attempt.security.label} Bluetooth socket"
                    }
                    return@supervisorScope acceptedSocket
                }

                lastError = attempt.error
                Logger.w(BluetoothPresentProofTransport::class.toString()) {
                    "${attempt.security.label} Bluetooth accept failed: ${attempt.error?.message ?: attempt.error?.javaClass?.simpleName}"
                }
            }

            throwAcceptFailure(lastError)
        } finally {
            acceptJobs.forEach { it.cancel() }
            acceptResults.close()
            closeServerSockets()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectSocket(
        adapter: BluetoothAdapter,
        device: BluetoothDevice,
    ): BluetoothSocket {
        var lastError: Throwable? = null

        for (security in SocketSecurity.values()) {
            val candidate = when (security) {
                SocketSecurity.SECURE -> device.createRfcommSocketToServiceRecord(SERVICE_UUID)
                SocketSecurity.INSECURE_FALLBACK -> device.createInsecureRfcommSocketToServiceRecord(
                    INSECURE_FALLBACK_SERVICE_UUID,
                )
            }

            try {
                adapter.cancelDiscovery()
                Logger.d(BluetoothPresentProofTransport::class.toString()) {
                    "Connecting with ${security.label} Bluetooth socket"
                }
                candidate.connect()
                Logger.d(BluetoothPresentProofTransport::class.toString()) {
                    "Connected with ${security.label} Bluetooth socket"
                }
                return candidate
            } catch (error: Exception) {
                lastError = error
                runCatching { candidate.close() }
                Logger.w(BluetoothPresentProofTransport::class.toString()) {
                    "${security.label} Bluetooth connect failed: ${error.message ?: error::class.simpleName}"
                }
                delay(CONNECT_RETRY_DELAY_MS)
            }
        }

        val peerName = device.name ?: device.address
        val reason = lastError?.message ?: lastError?.javaClass?.simpleName ?: "unknown error"
        throw IllegalStateException(
            "Could not connect to $peerName. On the other device, tap Receive requests first, keep both devices paired and nearby, then retry. Last Bluetooth error: $reason",
            lastError,
        )
    }

    fun holdOpenUntilUserStop(message: String = "Bluetooth proof session completed") {
        holdOpenUntilUserStop = true
        waitingForLocalResponse = false
        val current = _state.value
        if (current.status == BluetoothProofConnectionStatus.CONNECTED) {
            _state.value = current.copy(message = message)
        }
    }

    fun holdOpenUntilLocalResponse(message: String = "Bluetooth proof request received") {
        waitingForLocalResponse = true
        val current = _state.value
        if (current.status == BluetoothProofConnectionStatus.CONNECTED) {
            _state.value = current.copy(message = message)
        }
    }

    fun close() {
        holdOpenUntilUserStop = false
        waitingForLocalResponse = false
        val oldJob = connectionJob
        connectionJob = null
        oldJob?.cancel()
        scope.launch {
            oldJob?.cancelAndJoin()
        }
        closeSockets()
        _state.value = BluetoothProofTransportState(status = BluetoothProofConnectionStatus.IDLE)
    }

    @SuppressLint("MissingPermission")
    private suspend fun openSocket(
        connectedSocket: BluetoothSocket,
        onFrame: suspend (BluetoothProofFrame) -> Unit,
        onConnected: suspend () -> Unit = {},
    ) {
        holdOpenUntilUserStop = false
        waitingForLocalResponse = false
        socket = connectedSocket
        _state.value = BluetoothProofTransportState(
            status = BluetoothProofConnectionStatus.CONNECTED,
            peerName = connectedSocket.remoteDevice?.name ?: connectedSocket.remoteDevice?.address,
            peerAddress = connectedSocket.remoteDevice?.address,
            message = "Bluetooth connected",
        )
        runCatching {
            onConnected()
        }.onFailure { error ->
            if (coroutineContext.isActive) {
                setError("Bluetooth proof setup failed: ${error.message ?: error::class.simpleName}")
            }
            return
        }
        readLoop(connectedSocket, onFrame)
    }

    private suspend fun readLoop(
        activeSocket: BluetoothSocket,
        onFrame: suspend (BluetoothProofFrame) -> Unit,
    ) {
        try {
            BufferedReader(InputStreamReader(activeSocket.inputStream, StandardCharsets.UTF_8)).use { reader ->
                while (coroutineContext.isActive) {
                    val line = reader.readLine() ?: break
                    val receiveStartMs = nowMs()
                    val frame = decodeLine(line) ?: continue
                    logPerf(
                        event = "receive_end",
                        details = "type=${frame.messageType} id=${frame.id} thid=${frame.thid.orEmpty()} lineChars=${line.length} payloadChars=${frame.messageJson?.length ?: 0} durationMs=${nowMs() - receiveStartMs}",
                    )
                    Logger.d(BluetoothPresentProofTransport::class.toString()) {
                        "Received Bluetooth proof frame type=${frame.messageType}, id=${frame.id}, thid=${frame.thid}"
                    }
                    onFrame(frame)
                    if (waitingForLocalResponse) {
                        waitUntilLocalResponse(activeSocket)
                    }
                    if (holdOpenUntilUserStop) {
                        waitUntilUserStops(activeSocket)
                    }
                }
            }
        } catch (error: Exception) {
            if (isNormalSocketClose(error)) {
                Logger.d(BluetoothPresentProofTransport::class.toString()) {
                    "Bluetooth session closed by peer: ${error.message ?: error::class.simpleName}"
                }
            } else if (coroutineContext.isActive) {
                setError("Bluetooth session failed: ${error.message ?: error::class.simpleName}")
                return
            }
        } finally {
            closeSockets()
            if (_state.value.status != BluetoothProofConnectionStatus.ERROR) {
                _state.value = BluetoothProofTransportState(status = BluetoothProofConnectionStatus.CLOSED)
            }
        }
    }

    private suspend fun waitUntilLocalResponse(activeSocket: BluetoothSocket) {
        while (
            coroutineContext.isActive &&
            socket === activeSocket &&
            waitingForLocalResponse
        ) {
            delay(250)
        }
    }

    private suspend fun waitUntilUserStops(activeSocket: BluetoothSocket) {
        while (
            coroutineContext.isActive &&
            socket === activeSocket &&
            holdOpenUntilUserStop
        ) {
            delay(250)
        }
    }

    private fun requireAdapter(): BluetoothAdapter {
        val adapter = bluetoothAdapter ?: error("Bluetooth is not available on this device")
        if (!adapter.isEnabled) {
            error("Bluetooth is turned off")
        }
        return adapter
    }

    private fun closeServerSockets() {
        serverSockets.forEach { serverSocket ->
            runCatching { serverSocket.close() }
        }
        serverSockets = emptyList()
    }

    private fun closeSockets() {
        closeServerSockets()
        runCatching { socket?.close() }
        socket = null
        synchronized(chunkBuffers) {
            chunkBuffers.clear()
        }
    }

    private fun BluetoothSocket.writeLine(value: String) {
        val line = value + "\n"
        synchronized(writeLock) {
            outputStream.write(line.toByteArray(StandardCharsets.UTF_8))
            outputStream.flush()
        }
    }

    private fun decodeLine(line: String): BluetoothProofFrame? {
        val root = JSONObject(line)
        if (root.optString("protocol") == BluetoothProofFrame.PROTOCOL) {
            return BluetoothProofFrame.fromJsonString(line)
        }
        if (!root.has("messageId") || !root.has("chunkIndex") || !root.has("chunkCount")) {
            error("Unsupported Bluetooth proof frame")
        }

        val messageId = root.getString("messageId")
        val chunkIndex = root.getInt("chunkIndex")
        val chunkCount = root.getInt("chunkCount")
        val bytes = root.getString("bytes")
        val complete = synchronized(chunkBuffers) {
            val accumulator = chunkBuffers.getOrPut(messageId) {
                ChunkAccumulator(chunkCount = chunkCount)
            }
            accumulator.chunks[chunkIndex] = bytes
            if (accumulator.chunks.size == accumulator.chunkCount) {
                chunkBuffers.remove(messageId)
                (0 until accumulator.chunkCount).joinToString("") { index ->
                    accumulator.chunks[index] ?: error("Missing Bluetooth proof chunk $index")
                }
            } else {
                null
            }
        } ?: return null

        val padded = complete.padEnd(complete.length + ((4 - complete.length % 4) % 4), '=')
        val decoded = Base64.getUrlDecoder().decode(padded)
        return BluetoothProofFrame.fromJsonString(String(decoded, StandardCharsets.UTF_8))
    }

    private fun throwAcceptFailure(lastError: Throwable?): Nothing {
        val reason = lastError?.message ?: lastError?.javaClass?.simpleName ?: "unknown error"
        throw IllegalStateException(
            "No Bluetooth connection could be received. Last Bluetooth error: $reason",
            lastError,
        )
    }

    private fun isNormalSocketClose(error: Exception): Boolean {
        val message = error.message?.lowercase().orEmpty()
        return error is IOException && (
            "bt socket closed" in message ||
                ("socket closed" in message && "read return: -1" in message)
        )
    }

    private fun setError(message: String) {
        Logger.e(BluetoothPresentProofTransport::class.toString()) { message }
        closeSockets()
        _state.value = BluetoothProofTransportState(
            status = BluetoothProofConnectionStatus.ERROR,
            message = message,
        )
    }

    private fun nowMs(): Long = SystemClock.elapsedRealtimeNanos() / 1_000_000

    private fun logPerf(event: String, details: String) {
        Logger.i(PERF_TAG) { "event=$event $details tMs=${nowMs()}" }
    }

    private fun Throwable.shortName(): String =
        message?.replace('\n', ' ')?.takeIf { it.isNotBlank() }
            ?: javaClass.simpleName

    private companion object {
        const val PERF_TAG = "BtPerf"
        const val SERVICE_NAME = "USDI Local Present Proof"
        const val MAX_FRAME_BYTES = 12_000
        const val MAX_CHUNK_CHARS = 12_000
        const val CONNECT_RETRY_DELAY_MS = 300L
        val SERVICE_UUID: UUID = UUID.fromString("8b1e7f10-6d3b-4c43-9fc9-31ff623c4912")
        val INSECURE_FALLBACK_SERVICE_UUID: UUID = UUID.fromString("8b1e7f11-6d3b-4c43-9fc9-31ff623c4912")
    }

    private enum class SocketSecurity(val label: String) {
        SECURE("secure"),
        INSECURE_FALLBACK("insecure fallback"),
    }

    private data class ListeningServer(
        val security: SocketSecurity,
        val serverSocket: BluetoothServerSocket,
    )

    private data class AcceptAttempt(
        val security: SocketSecurity,
        val socket: BluetoothSocket? = null,
        val error: Throwable? = null,
    )

    private data class ChunkAccumulator(
        val chunkCount: Int,
        val chunks: MutableMap<Int, String> = mutableMapOf(),
    )
}
