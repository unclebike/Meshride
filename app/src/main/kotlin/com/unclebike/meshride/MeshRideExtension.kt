package com.unclebike.meshride

import com.unclebike.meshride.ble.MeshtasticConnection
import com.unclebike.meshride.data.ConnectionState
import com.unclebike.meshride.data.PreferencesRepository
import com.unclebike.meshride.datatypes.MeshLastMessageDataType
import com.unclebike.meshride.datatypes.MeshNodeCountDataType
import dagger.hilt.android.AndroidEntryPoint
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.models.InRideAlert
import io.hammerhead.karooext.models.PlayBeepPattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MeshRideExtension : KarooExtension("meshride", "1") {

    @Inject lateinit var meshtasticConnection: MeshtasticConnection
    @Inject lateinit var preferencesRepository: PreferencesRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var karooSystem: KarooSystemService? = null

    override val types by lazy {
        listOf(
            MeshNodeCountDataType(this, meshtasticConnection),
            MeshLastMessageDataType(this, meshtasticConnection),
        )
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("MeshRideExtension created")

        karooSystem = KarooSystemService(this).also { it.connect {} }

        // Listen for incoming messages and dispatch in-ride alerts
        scope.launch {
            meshtasticConnection.incomingMessages.collect { packet ->
                Timber.d("Dispatching in-ride alert for message from ${packet.senderName}")
                karooSystem?.dispatch(
                    InRideAlert(
                        id = "mesh-message-${packet.timestamp}",
                        detail = "${packet.senderName}: ${packet.messageText}",
                        autoDismissMs = 8000,
                    )
                )
            }
        }

        // Auto-connect to paired device
        scope.launch {
            val address = preferencesRepository.pairedDeviceAddress.first()
            if (address != null && meshtasticConnection.connectionState.value == ConnectionState.DISCONNECTED) {
                Timber.d("Auto-connecting to paired device: $address")
                meshtasticConnection.connect(address)
            }
        }
    }

    override fun onBonusAction(actionId: String) {
        Timber.d("Bonus action: $actionId")
        scope.launch {
            val quickMessages = preferencesRepository.quickMessages.first()
            val slotIndex = when (actionId) {
                "send-message-1" -> 0
                "send-message-2" -> 1
                "send-message-3" -> 2
                else -> return@launch
            }
            val message = quickMessages.getOrNull(slotIndex) ?: return@launch
            if (message.text.isNotBlank()) {
                meshtasticConnection.sendMessage(message.text)
                // Confirm to rider with a beep
                karooSystem?.dispatch(PlayBeepPattern(listOf(PlayBeepPattern.Tone(200, 100))))
                Timber.d("Sent quick message slot $slotIndex: ${message.text}")
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        karooSystem?.disconnect()
        karooSystem = null
        super.onDestroy()
        Timber.d("MeshRideExtension destroyed")
    }
}
