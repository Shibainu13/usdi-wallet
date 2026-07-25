package com.dev.usdi_wallet.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
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
    private var serverSocket: BluetoothServerSocket? = null
    @Volatile
    private var holdOpenUntilUserStop = false

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
            runCatching {
                val adapter = requireAdapter()
                _state.value = BluetoothProofTransportState(
                    status = BluetoothProofConnectionStatus.LISTENING,
                    message = "Listening for a paired holder-verifier session",
                )
                val server = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
                serverSocket = server
                val acceptedSocket = server.accept()
                server.close()
                serverSocket = null
                openSocket(acceptedSocket, onFrame)
            }.onFailure { error ->
                if (isActive) {
                    setError("Bluetooth listen failed: ${error.message ?: error::class.simpleName}")
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
            runCatching {
                val adapter = requireAdapter()
                val device = adapter.getRemoteDevice(peerAddress)
                _state.value = BluetoothProofTransportState(
                    status = BluetoothProofConnectionStatus.CONNECTING,
                    peerName = device.name ?: peerAddress,
                    peerAddress = peerAddress,
                    message = "Connecting over Bluetooth",
                )
                Logger.d("Success 1")
                adapter.cancelDiscovery()
                val connectedSocket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
                Logger.d("Success 2")
                connectedSocket.connect()
                Logger.d("Success 3")
                openSocket(connectedSocket, onFrame, onConnected)
            }.onFailure { error ->
                if (isActive) {
                    setError("Bluetooth connect failed: ${error.message ?: error::class.simpleName}")
                }
            }
        }
    }

    suspend fun send(frame: BluetoothProofFrame) {
        val activeSocket = socket ?: error("Bluetooth session is not connected")
        withContext(Dispatchers.IO) {
            val frameJson = frame.toJsonString()
            val bytes = frameJson.toByteArray(StandardCharsets.UTF_8)
            Logger.d(BluetoothPresentProofTransport::class.toString()) {
                "Sending Bluetooth proof frame type=${frame.messageType}, id=${frame.id}, thid=${frame.thid}, bytes=${bytes.size}"
            }
            if (bytes.size <= MAX_FRAME_BYTES) {
                activeSocket.writeLine(frameJson)
            } else {
                val encoded = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(bytes)
                val chunks = encoded.chunked(MAX_CHUNK_CHARS)
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
            Logger.d(BluetoothPresentProofTransport::class.toString()) {
                "Sent Bluetooth proof frame type=${frame.messageType}, id=${frame.id}"
            }
        }
    }

    fun holdOpenUntilUserStop(message: String = "Bluetooth proof session completed") {
        holdOpenUntilUserStop = true
        val current = _state.value
        if (current.status == BluetoothProofConnectionStatus.CONNECTED) {
            _state.value = current.copy(message = message)
        }
    }

    fun close() {
        holdOpenUntilUserStop = false
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
        socket = connectedSocket
        _state.value = BluetoothProofTransportState(
            status = BluetoothProofConnectionStatus.CONNECTED,
            peerName = connectedSocket.remoteDevice?.name ?: connectedSocket.remoteDevice?.address,
            peerAddress = connectedSocket.remoteDevice?.address,
            message = "Bluetooth proof session connected",
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
                    val frame = decodeLine(line) ?: continue
                    Logger.d(BluetoothPresentProofTransport::class.toString()) {
                        "Received Bluetooth proof frame type=${frame.messageType}, id=${frame.id}, thid=${frame.thid}"
                    }
                    onFrame(frame)
                    if (holdOpenUntilUserStop) {
                        waitUntilUserStops(activeSocket)
                    }
                }
            }
        } catch (error: Exception) {
            if (coroutineContext.isActive) {
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

    private fun closeSockets() {
        runCatching { serverSocket?.close() }
        runCatching { socket?.close() }
        serverSocket = null
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

    private fun setError(message: String) {
        Logger.e(BluetoothPresentProofTransport::class.toString()) { message }
        closeSockets()
        _state.value = BluetoothProofTransportState(
            status = BluetoothProofConnectionStatus.ERROR,
            message = message,
        )
    }

    private companion object {
        const val SERVICE_NAME = "USDI Local Present Proof"
        const val MAX_FRAME_BYTES = 12_000
        const val MAX_CHUNK_CHARS = 12_000
        val SERVICE_UUID: UUID = UUID.fromString("8b1e7f10-6d3b-4c43-9fc9-31ff623c4912")
    }

    private data class ChunkAccumulator(
        val chunkCount: Int,
        val chunks: MutableMap<Int, String> = mutableMapOf(),
    )
}
