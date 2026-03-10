package com.unclebike.meshride.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.content.Context
import com.unclebike.meshride.data.ConnectionState
import com.unclebike.meshride.data.MeshNode
import com.unclebike.meshride.data.MeshPacket
import com.unclebike.meshride.ui.ScannedDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.ktx.suspend
import no.nordicsemi.android.ble.ktx.getNotifications
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real Meshtastic BLE connection using Nordic Android BLE Library (BleManager pattern).
 * This matches the pattern used by Meshtastic-Android for proven compatibility.
 */
@Singleton
class MeshtasticBleConnection @Inject constructor(
    @ApplicationContext private val context: Context,
) : MeshtasticConnection {

    companion object {
        val MESHTASTIC_SERVICE_UUID: UUID = UUID.fromString("6ba1b218-15a8-461f-9fa8-5dcae273eafd")
        val FROM_RADIO_UUID: UUID = UUID.fromString("2c55e69e-4993-11ed-b878-0242ac120002")
        val TO_RADIO_UUID: UUID = UUID.fromString("f75c76d2-129e-4dad-a1dd-7866124401e7")
        val FROM_NUM_UUID: UUID = UUID.fromString("ed9da18c-a800-4f66-a670-aa7547e34453")

        private const val RECONNECT_DELAY_MS = 3000L
        private const val MAX_RECONNECT_ATTEMPTS = 10
        private const val MTU_SIZE = 512
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var bleManager: MeshtasticBleManager? = null
    private var connectionJob: Job? = null
    private var reconnectJob: Job? = null
    private var readJob: Job? = null
    private var currentAddress: String? = null
    private var myNodeNum: Long = 0

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<MeshPacket>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val incomingMessages: Flow<MeshPacket> = _incomingMessages.asSharedFlow()

    private val _nodes = MutableStateFlow<Map<Long, MeshNode>>(emptyMap())
    override val nodes: StateFlow<Map<Long, MeshNode>> = _nodes.asStateFlow()

    private val nodesMap = ConcurrentHashMap<Long, MeshNode>()

    @SuppressLint("MissingPermission")
    override fun startScan(): Flow<ScannedDevice> = callbackFlow {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter ?: run {
            close()
            return@callbackFlow
        }

        _connectionState.value = ConnectionState.SCANNING
        val scanner = adapter.bluetoothLeScanner ?: run {
            _connectionState.value = ConnectionState.DISCONNECTED
            close()
            return@callbackFlow
        }

        val scanCallback = object : android.bluetooth.le.ScanCallback() {
            override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
                val device = result.device
                val name = device.name ?: return
                if (name.contains("Meshtastic", ignoreCase = true) ||
                    name.contains("Mesh", ignoreCase = true)
                ) {
                    trySend(ScannedDevice(address = device.address, name = name, rssi = result.rssi))
                }
            }
        }

        val scanSettings = android.bluetooth.le.ScanSettings.Builder()
            .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // Also emit bonded devices that look like Meshtastic radios
        val bondedDevices = adapter.bondedDevices
        bondedDevices?.forEach { device ->
            val name = device.name ?: "Unknown"
            if (device.uuids?.any { it.uuid == MESHTASTIC_SERVICE_UUID } == true ||
                name.contains("Meshtastic", ignoreCase = true) ||
                name.contains("Mesh", ignoreCase = true)
            ) {
                trySend(ScannedDevice(address = device.address, name = name, rssi = -50))
            }
        }

        try {
            scanner.startScan(null, scanSettings, scanCallback)
        } catch (e: Exception) {
            Timber.e(e, "Scan start failed")
            _connectionState.value = ConnectionState.DISCONNECTED
            close()
            return@callbackFlow
        }

        awaitClose {
            try {
                scanner.stopScan(scanCallback)
            } catch (e: Exception) {
                Timber.e(e, "Scan stop failed")
            }
            if (_connectionState.value == ConnectionState.SCANNING) {
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        }
    }

    override fun stopScan() {
        if (_connectionState.value == ConnectionState.SCANNING) {
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(address: String) {
        currentAddress = address
        connectionJob?.cancel()
        reconnectJob?.cancel()

        connectionJob = scope.launch {
            try {
                _connectionState.value = ConnectionState.CONNECTING
                Timber.d("Connecting to Meshtastic device: $address")

                val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val device = bluetoothManager.adapter?.getRemoteDevice(address) ?: run {
                    Timber.e("Device not found: $address")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    return@launch
                }

                val manager = MeshtasticBleManager(context)
                bleManager = manager

                // Connect using Nordic BLE library with retry and auto-connect
                manager.connect(device)
                    .retry(3, 200)
                    .useAutoConnect(true)
                    .suspend()

                // Connection established - now run the Meshtastic handshake
                manager.requestMtu(MTU_SIZE).suspend()

                // Send wantConfig to initiate the config download
                val wantConfig = MeshtasticProtos.buildWantConfigPacket()
                manager.writeToRadio(wantConfig)
                Timber.d("Sent wantConfig packet")

                // Read initial config dump from FromRadio
                readInitialConfig(manager)

                _connectionState.value = ConnectionState.CONNECTED
                Timber.d("Connected to Meshtastic device, ${nodesMap.size} nodes discovered")

                // Subscribe to FromNum notifications for ongoing messages
                startMessageListener(manager)

            } catch (e: Exception) {
                Timber.e(e, "Connection failed")
                _connectionState.value = ConnectionState.DISCONNECTED
                scheduleReconnect()
            }
        }
    }

    private suspend fun readInitialConfig(manager: MeshtasticBleManager) {
        var configComplete = false
        while (!configComplete) {
            val data = manager.readFromRadio()
            if (data == null || data.isEmpty()) break

            val parsed = MeshtasticProtos.parseFromRadio(data)
            when (parsed) {
                is MeshtasticProtos.FromRadio.MyInfo -> {
                    myNodeNum = parsed.myNodeNum
                    Timber.d("My node num: $myNodeNum")
                }
                is MeshtasticProtos.FromRadio.NodeInfoVariant -> {
                    handleNodeInfo(parsed.nodeInfo)
                }
                is MeshtasticProtos.FromRadio.Packet -> {
                    handleMeshPacket(parsed.meshPacket)
                }
                is MeshtasticProtos.FromRadio.ConfigComplete -> {
                    configComplete = true
                    Timber.d("Config complete (id: ${parsed.configCompleteId})")
                }
                is MeshtasticProtos.FromRadio.Unknown -> { /* skip */ }
            }
        }
    }

    private fun startMessageListener(manager: MeshtasticBleManager) {
        readJob?.cancel()
        readJob = scope.launch {
            try {
                manager.fromNumNotifications().collect {
                    // New data available - read all pending FromRadio packets
                    var hasMore = true
                    while (hasMore && isActive) {
                        val data = manager.readFromRadio()
                        if (data == null || data.isEmpty()) {
                            hasMore = false
                        } else {
                            val parsed = MeshtasticProtos.parseFromRadio(data)
                            when (parsed) {
                                is MeshtasticProtos.FromRadio.Packet -> handleMeshPacket(parsed.meshPacket)
                                is MeshtasticProtos.FromRadio.NodeInfoVariant -> handleNodeInfo(parsed.nodeInfo)
                                else -> { /* ignore config messages during normal operation */ }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Message listener error")
                if (currentAddress != null) {
                    scheduleReconnect()
                }
            }
        }
    }

    private fun handleNodeInfo(nodeInfo: MeshtasticProtos.DecodedNodeInfo) {
        val node = MeshNode(
            nodeId = nodeInfo.num,
            longName = nodeInfo.user?.longName ?: "Node ${nodeInfo.num}",
            shortName = nodeInfo.user?.shortName ?: "?",
            lastHeard = nodeInfo.lastHeard * 1000, // convert to millis
            snr = nodeInfo.snr,
        )
        nodesMap[nodeInfo.num] = node
        _nodes.value = nodesMap.toMap()
        Timber.d("Node updated: ${node.longName} (${node.nodeId})")
    }

    private suspend fun handleMeshPacket(packet: MeshtasticProtos.DecodedMeshPacket) {
        val decoded = packet.decoded ?: return

        when (decoded.portnum) {
            MeshtasticProtos.PORTNUM_TEXT_MESSAGE -> {
                val text = String(decoded.payload, Charsets.UTF_8)
                val senderNode = nodesMap[packet.from]
                val meshPacket = MeshPacket(
                    nodeId = packet.from,
                    senderName = senderNode?.longName ?: "Node ${packet.from}",
                    senderShortName = senderNode?.shortName ?: "?",
                    messageText = text,
                    timestamp = if (packet.rxTime > 0) packet.rxTime * 1000 else System.currentTimeMillis(),
                    snr = packet.rxSnr,
                    rssi = packet.rxRssi,
                    channel = packet.channel,
                )
                _incomingMessages.emit(meshPacket)
                Timber.d("Received message from ${meshPacket.senderName}: $text")
            }
            MeshtasticProtos.PORTNUM_NODEINFO -> {
                try {
                    val input = com.google.protobuf.CodedInputStream.newInstance(decoded.payload)
                    val user = MeshtasticProtos.parseUser(input)
                    val existing = nodesMap[packet.from]
                    val node = MeshNode(
                        nodeId = packet.from,
                        longName = user.longName.ifEmpty { existing?.longName ?: "Node ${packet.from}" },
                        shortName = user.shortName.ifEmpty { existing?.shortName ?: "?" },
                        lastHeard = System.currentTimeMillis(),
                        snr = packet.rxSnr,
                        rssi = packet.rxRssi,
                    )
                    nodesMap[packet.from] = node
                    _nodes.value = nodesMap.toMap()
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse NodeInfo payload")
                }
            }
            else -> {
                Timber.v("Ignoring packet with portnum: ${decoded.portnum}")
            }
        }
    }

    private fun scheduleReconnect() {
        if (currentAddress == null) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            _connectionState.value = ConnectionState.RECONNECTING
            var attempt = 0
            while (attempt < MAX_RECONNECT_ATTEMPTS && currentAddress != null) {
                delay(RECONNECT_DELAY_MS * (attempt + 1))
                attempt++
                Timber.d("Reconnect attempt $attempt/$MAX_RECONNECT_ATTEMPTS")
                try {
                    connect(currentAddress!!)
                    return@launch
                } catch (e: Exception) {
                    Timber.e(e, "Reconnect attempt $attempt failed")
                }
            }
            _connectionState.value = ConnectionState.DISCONNECTED
            Timber.w("Max reconnect attempts reached")
        }
    }

    override suspend fun disconnect() {
        currentAddress = null
        connectionJob?.cancel()
        reconnectJob?.cancel()
        readJob?.cancel()
        try {
            bleManager?.disconnect()?.suspend()
        } catch (e: Exception) {
            Timber.e(e, "Disconnect error")
        }
        bleManager = null
        _connectionState.value = ConnectionState.DISCONNECTED
        Timber.d("Disconnected from Meshtastic device")
    }

    override suspend fun sendMessage(text: String, channel: Int) {
        val manager = bleManager ?: run {
            Timber.w("Cannot send message - not connected")
            return
        }
        try {
            val packet = MeshtasticProtos.buildTextMessagePacket(text, channel)
            manager.writeToRadio(packet)
            Timber.d("Sent message: $text")
        } catch (e: Exception) {
            Timber.e(e, "Failed to send message")
        }
    }

    override fun getNodeCount(): Int = nodesMap.size

    /**
     * Inner BleManager subclass implementing the Meshtastic GATT service discovery
     * and characteristic caching, following the Nordic BLE Library pattern used by
     * Meshtastic-Android (RadioInterfaceService / BluetoothInterface).
     */
    private class MeshtasticBleManager(context: Context) : BleManager(context) {

        private var toRadioChar: BluetoothGattCharacteristic? = null
        private var fromRadioChar: BluetoothGattCharacteristic? = null
        private var fromNumChar: BluetoothGattCharacteristic? = null

        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            val service = gatt.getService(MESHTASTIC_SERVICE_UUID) ?: return false
            toRadioChar = service.getCharacteristic(TO_RADIO_UUID)
            fromRadioChar = service.getCharacteristic(FROM_RADIO_UUID)
            fromNumChar = service.getCharacteristic(FROM_NUM_UUID)
            return toRadioChar != null && fromRadioChar != null && fromNumChar != null
        }

        override fun onServicesInvalidated() {
            toRadioChar = null
            fromRadioChar = null
            fromNumChar = null
        }

        suspend fun writeToRadio(data: ByteArray) {
            val char = toRadioChar ?: throw IllegalStateException("Not connected")
            writeCharacteristic(char, data).suspend()
        }

        suspend fun readFromRadio(): ByteArray? {
            val char = fromRadioChar ?: return null
            return readCharacteristic(char).suspend().value
        }

        fun fromNumNotifications(): Flow<ByteArray> = callbackFlow {
            val char = fromNumChar ?: run {
                close()
                return@callbackFlow
            }

            setNotificationCallback(char).with { _, data ->
                data.value?.let { trySend(it) }
            }
            enableNotifications(char).suspend()

            awaitClose {
                disableNotifications(char)
            }
        }
    }
}
