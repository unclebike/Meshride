package com.unclebike.meshride.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unclebike.meshride.ble.MeshtasticConnection
import com.unclebike.meshride.data.MessageRepository
import com.unclebike.meshride.data.NodeRepository
import com.unclebike.meshride.data.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MeshRideViewModel @Inject constructor(
    private val meshtasticConnection: MeshtasticConnection,
    private val messageRepository: MessageRepository,
    private val nodeRepository: NodeRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val _scanResults = MutableStateFlow<List<ScannedDevice>>(emptyList())
    private val _isScanning = MutableStateFlow(false)

    val uiState: StateFlow<MeshRideUiState> = combine(
        meshtasticConnection.connectionState,
        nodeRepository.nodes,
        messageRepository.recentMessages,
        preferencesRepository.quickMessages,
        combine(
            preferencesRepository.pairedDeviceAddress,
            preferencesRepository.pairedDeviceName,
        ) { address, name -> Pair(address, name) },
    ) { connectionState, nodes, messages, quickMessages, pairedDevice ->
        MeshRideUiState(
            connectionState = connectionState,
            pairedDeviceAddress = pairedDevice.first,
            pairedDeviceName = pairedDevice.second,
            nodeCount = nodes.size,
            nodes = nodes.values.toList().sortedByDescending { it.lastHeard },
            messages = messages,
            quickMessages = quickMessages,
            scanResults = _scanResults.value,
            isScanning = _isScanning.value,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MeshRideUiState(),
    )

    // Persist incoming messages to Room
    init {
        viewModelScope.launch {
            meshtasticConnection.incomingMessages.collect { packet ->
                messageRepository.saveMessage(packet)
            }
        }
    }

    fun startScan() {
        _isScanning.value = true
        _scanResults.value = emptyList()
        viewModelScope.launch {
            try {
                meshtasticConnection.startScan().collect { device ->
                    _scanResults.update { current ->
                        val existing = current.indexOfFirst { it.address == device.address }
                        if (existing >= 0) {
                            current.toMutableList().apply { set(existing, device) }
                        } else {
                            current + device
                        }
                    }
                }
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun stopScan() {
        meshtasticConnection.stopScan()
        _isScanning.value = false
    }

    fun connectToDevice(device: ScannedDevice) {
        viewModelScope.launch {
            preferencesRepository.setPairedDevice(device.address, device.name)
            meshtasticConnection.connect(device.address)
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            meshtasticConnection.disconnect()
            preferencesRepository.clearPairedDevice()
        }
    }

    fun updateQuickMessage(slot: Int, label: String, text: String) {
        viewModelScope.launch {
            val current = uiState.value.quickMessages.toMutableList()
            val index = current.indexOfFirst { it.slot == slot }
            if (index >= 0) {
                current[index] = QuickMessage(slot = slot, label = label, text = text)
            }
            preferencesRepository.setQuickMessages(current)
        }
    }

    fun sendQuickMessage(slot: Int) {
        viewModelScope.launch {
            val message = uiState.value.quickMessages.getOrNull(slot) ?: return@launch
            if (message.text.isNotBlank()) {
                meshtasticConnection.sendMessage(message.text)
                Timber.d("Sent quick message: ${message.text}")
            }
        }
    }
}
