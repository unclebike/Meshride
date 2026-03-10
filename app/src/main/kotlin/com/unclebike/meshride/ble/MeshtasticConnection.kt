package com.unclebike.meshride.ble

import com.unclebike.meshride.data.ConnectionState
import com.unclebike.meshride.data.MeshNode
import com.unclebike.meshride.data.MeshPacket
import com.unclebike.meshride.ui.ScannedDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for Meshtastic BLE radio communication.
 * Implementations: MeshtasticBleConnection (real hardware), MockMeshtasticConnection (testing)
 */
interface MeshtasticConnection {

    /** Current BLE connection state */
    val connectionState: StateFlow<ConnectionState>

    /** Flow of incoming decoded mesh messages */
    val incomingMessages: Flow<MeshPacket>

    /** Flow of discovered/updated mesh nodes */
    val nodes: StateFlow<Map<Long, MeshNode>>

    /** Scan for nearby Meshtastic BLE devices */
    fun startScan(): Flow<ScannedDevice>

    /** Stop scanning */
    fun stopScan()

    /** Connect to a specific Meshtastic device by address */
    suspend fun connect(address: String)

    /** Disconnect from the current device */
    suspend fun disconnect()

    /** Send a text message to the mesh network */
    suspend fun sendMessage(text: String, channel: Int = 0)

    /** Get the current node count (connected nodes on the mesh) */
    fun getNodeCount(): Int
}
