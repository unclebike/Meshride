package com.unclebike.meshride.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a decoded Meshtastic mesh packet.
 * Used both as an in-memory model and Room entity for message persistence.
 */
@Entity(tableName = "messages")
data class MeshPacket(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nodeId: Long,
    val senderName: String,
    val senderShortName: String,
    val messageText: String,
    val timestamp: Long,
    val snr: Float = 0f,
    val rssi: Int = 0,
    val channel: Int = 0,
)

/**
 * Represents a node seen on the mesh network.
 */
data class MeshNode(
    val nodeId: Long,
    val longName: String,
    val shortName: String,
    val lastHeard: Long,
    val snr: Float = 0f,
    val rssi: Int = 0,
    val hwModel: String = "UNKNOWN",
)

/**
 * Connection state for the Meshtastic BLE link.
 */
enum class ConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
}
