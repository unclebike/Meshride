package com.unclebike.meshride.ui

import com.unclebike.meshride.data.ConnectionState
import com.unclebike.meshride.data.MeshNode
import com.unclebike.meshride.data.MeshPacket

/**
 * Unified UI state for the MeshRide setup screens.
 */
data class MeshRideUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val pairedDeviceAddress: String? = null,
    val pairedDeviceName: String? = null,
    val nodeCount: Int = 0,
    val nodes: List<MeshNode> = emptyList(),
    val messages: List<MeshPacket> = emptyList(),
    val quickMessages: List<QuickMessage> = listOf(
        QuickMessage(slot = 0, label = "Msg 1", text = "Regroup!"),
        QuickMessage(slot = 1, label = "Msg 2", text = "Flat tire"),
        QuickMessage(slot = 2, label = "Msg 3", text = "Stopping ahead"),
    ),
    val scanResults: List<ScannedDevice> = emptyList(),
    val isScanning: Boolean = false,
)

data class QuickMessage(
    val slot: Int,
    val label: String,
    val text: String,
)

data class ScannedDevice(
    val address: String,
    val name: String,
    val rssi: Int,
)
