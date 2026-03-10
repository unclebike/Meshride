package com.unclebike.meshride.ble

import android.annotation.SuppressLint
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
import no.nordicsemi.kotlin.ble.client.main.callback.ClientBleGatt
import no.nordicsemi.kotlin.ble.client.main.service.ClientBleGattCharacteristic
import no.nordicsemi.kotlin.ble.client.main.service.ClientBleGattServices
import no.nordicsemi.kotlin.ble.core.data.GattConnectionState
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

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
    private var gattConnection: ClientBleGatt? = null
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
    override fun startScan(): Flow<ScannedDevice> = flow {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter ?: return@flow

        _connectionState.value = ConnectionState.SCANNING

        // Use Android's built-in BLE scanner for device discovery
        val scanner = adapter.bluetoothLeScanner ?: return@flow
        val scanCallback = object : android.bluetooth.le.ScanCallback() {
            override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
                val device = result.device
                val name = device.name ?: return
                if (name.contains("Meshtastic", ignoreCase = true) || name.contains("Mesh", ignoreCase = true)) {
                    // Will be emitted via the flow mechanism below
                }
            }
        }

        val scanFilter = android.bluetooth.le.ScanFilter.Builder()
            .build()
        val scanSettings = android.bluetooth.le.ScanSettings.Builder()
            .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(listOf(scanFilter), scanSettings, scanCallback)

            // Also do a paired devices check
            val bondedDevices = adapter.bondedDevices
            bondedDevices?.forEach { device ->
                val name = device.name ?: "Unknown"
                if (device.uuids?.any { it.uuid == MESHTASTIC_SERVICE_UUID } == true ||
                    name.contains("Meshtastic", ignoreCase = true) ||
                    name.contains("Mesh", ignoreCase = true)) {
                    emit(ScannedDevice(address = device.address, name = name, rssi = -50))
                }
            }

            // Keep scanning for a period
            delay(10000)
            scanner.stopScan(scanCallback)
        } catch (e: Exception) {
            Timber.e(e, "Scan failed")
        } finally {
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

                val gatt = ClientBleGatt.connect(context, device, scope)
                gattConnection = gatt

                // Request MTU
                gatt.requestMtu(MTU_SIZE)

                // Discover services
                val services: ClientBleGattServices = gatt.discoverServices()
                val meshtasticService = services.findService(MESHTASTIC_SERVICE_UUID)
                if (meshtasticService == null) {
                    Timber.e("Meshtastic service not found on device")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    gatt.disconnect()
                    return@launch
                }

                val fromRadio = meshtasticService.findCharacteristic(FROM_RADIO_UUID)
                val toRadio = meshtasticService.findCharacteristic(TO_RADIO_UUID)
                val fromNum = meshtasticService.findCharacteristic(FROM_NUM_UUID)

                if (fromRadio == null || toRadio == null || fromNum == null) {
                    Timber.e("Required characteristics not found")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    gatt.disconnect()
                    return@launch
                }

                // Send wantConfig to initiate the config download
                val wantConfig = MeshtasticProtos.buildWantConfigPacket()
                toRadio.write(wantConfig)
                Timber.d("Sent wantConfig packet")

                // Read initial config dump from FromRadio
                readInitialConfig(fromRadio)

                _connectionState.value = ConnectionState.CONNECTED
                Timber.d("Connected to Meshtastic device, ${nodesMap.size} nodes discovered")

                // Subscribe to FromNum notifications for ongoing messages
                startMessageListener(fromRadio, fromNum)

                // Monitor connection state
                gatt.connectionStateWithStatus.collect { (state, status) ->
                    when (state) {
                        GattConnectionState.STATE_DISCONNECTED -> {
                            Timber.d("Disconnected from device (status: $status)")
                            if (currentAddress != null) {
                                scheduleReconnect()
                            }
                        }
                        else -> { /* connected or connecting - handled above */ }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Connection failed")
                _connectionState.value = ConnectionState.DISCONNECTED
                scheduleReconnect()
            }
        }
    }

    private suspend fun readInitialConfig(fromRadio: ClientBleGattCharacteristic) {
        var configComplete = false
        while (!configComplete) {
            val data = fromRadio.read()
            if (data.isEmpty()) break

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

    private fun startMessageListener(
        fromRadio: ClientBleGattCharacteristic,
        fromNum: ClientBleGattCharacteristic,
    ) {
        readJob?.cancel()
        readJob = scope.launch {
            try {
                fromNum.getNotifications().collect {
                    // New data available - read all pending FromRadio packets
                    var hasMore = true
                    while (hasMore && isActive) {
                        val data = fromRadio.read()
                        if (data.isEmpty()) {
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
                // Parse the embedded User from the data payload
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
            gattConnection?.disconnect()
        } catch (e: Exception) {
            Timber.e(e, "Disconnect error")
        }
        gattConnection = null
        _connectionState.value = ConnectionState.DISCONNECTED
        Timber.d("Disconnected from Meshtastic device")
    }

    override suspend fun sendMessage(text: String, channel: Int) {
        val gatt = gattConnection ?: run {
            Timber.w("Cannot send message - not connected")
            return
        }
        try {
            val services = gatt.discoverServices()
            val service = services.findService(MESHTASTIC_SERVICE_UUID)
            val toRadio = service?.findCharacteristic(TO_RADIO_UUID) ?: run {
                Timber.e("ToRadio characteristic not found")
                return
            }
            val packet = MeshtasticProtos.buildTextMessagePacket(text, channel)
            toRadio.write(packet)
            Timber.d("Sent message: $text")
        } catch (e: Exception) {
            Timber.e(e, "Failed to send message")
        }
    }

    override fun getNodeCount(): Int = nodesMap.size
}
