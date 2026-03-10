package com.unclebike.meshride.ble

import com.unclebike.meshride.data.ConnectionState
import com.unclebike.meshride.data.MeshNode
import com.unclebike.meshride.data.MeshPacket
import com.unclebike.meshride.ui.ScannedDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mock implementation of MeshtasticConnection for UI testing without real BLE hardware.
 * Injects fake packets on a timer to simulate mesh activity.
 */
@Singleton
class MockMeshtasticConnection @Inject constructor() : MeshtasticConnection {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var messageJob: Job? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<MeshPacket>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val incomingMessages: Flow<MeshPacket> = _incomingMessages.asSharedFlow()

    private val mockNodes = mutableMapOf(
        1L to MeshNode(1L, "Alice", "AL", System.currentTimeMillis(), -8.5f, -72),
        2L to MeshNode(2L, "Bob", "BO", System.currentTimeMillis(), -5.0f, -65),
        3L to MeshNode(3L, "Carol", "CA", System.currentTimeMillis(), -12.0f, -88),
    )

    private val _nodes = MutableStateFlow<Map<Long, MeshNode>>(emptyMap())
    override val nodes: StateFlow<Map<Long, MeshNode>> = _nodes.asStateFlow()

    private val mockMessages = listOf(
        "Regroup at next junction",
        "Flat tire, stopping",
        "All clear ahead",
        "Car back!",
        "Coffee stop in 5km",
        "Waiting at top",
        "Mechanical issue",
        "Taking shortcut",
    )

    override fun startScan(): Flow<ScannedDevice> = flow {
        _connectionState.value = ConnectionState.SCANNING
        delay(500)
        emit(ScannedDevice("AA:BB:CC:DD:EE:01", "Meshtastic_ABCD", -55))
        delay(300)
        emit(ScannedDevice("AA:BB:CC:DD:EE:02", "Meshtastic_EFGH", -72))
        delay(200)
        emit(ScannedDevice("AA:BB:CC:DD:EE:03", "Meshtastic_IJKL", -88))
        delay(1000)
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    override fun stopScan() {
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    override suspend fun connect(address: String) {
        _connectionState.value = ConnectionState.CONNECTING
        delay(1500)
        _connectionState.value = ConnectionState.CONNECTED
        _nodes.value = mockNodes.toMap()
        Timber.d("Mock: Connected to $address")
        startFakeMessageStream()
    }

    override suspend fun disconnect() {
        messageJob?.cancel()
        _connectionState.value = ConnectionState.DISCONNECTED
        _nodes.value = emptyMap()
        Timber.d("Mock: Disconnected")
    }

    override suspend fun sendMessage(text: String, channel: Int) {
        Timber.d("Mock: Sent message '$text' on channel $channel")
    }

    override fun getNodeCount(): Int = _nodes.value.size

    private fun startFakeMessageStream() {
        messageJob?.cancel()
        messageJob = scope.launch {
            var messageIndex = 0
            while (isActive) {
                delay((8000L..15000L).random())
                val senderNode = mockNodes.values.toList().random()
                val text = mockMessages[messageIndex % mockMessages.size]
                val packet = MeshPacket(
                    nodeId = senderNode.nodeId,
                    senderName = senderNode.longName,
                    senderShortName = senderNode.shortName,
                    messageText = text,
                    timestamp = System.currentTimeMillis(),
                    snr = senderNode.snr,
                    rssi = senderNode.rssi,
                    channel = 0,
                )
                _incomingMessages.emit(packet)
                Timber.d("Mock: Incoming message from ${senderNode.longName}: $text")
                messageIndex++
            }
        }
    }
}
