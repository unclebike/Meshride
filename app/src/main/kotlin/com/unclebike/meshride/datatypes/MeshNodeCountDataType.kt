package com.unclebike.meshride.datatypes

import com.unclebike.meshride.ble.MeshtasticConnection
import com.unclebike.meshride.data.ConnectionState
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

class MeshNodeCountDataType(
    extension: KarooExtension,
    private val connection: MeshtasticConnection,
) : DataTypeImpl(extension.extension, TYPE_ID) {

    companion object {
        const val TYPE_ID = "mesh-node-count"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun startStream(emitter: Emitter<StreamState>) {
        Timber.d("Starting mesh node count stream")
        val job = scope.launch {
            // Emit node count updates periodically
            while (isActive) {
                val state = connection.connectionState.value
                if (state == ConnectionState.CONNECTED) {
                    val nodeCount = connection.getNodeCount()
                    emitter.onNext(
                        StreamState.Streaming(
                            DataPoint(
                                dataTypeId = dataTypeId,
                                values = mapOf(DataType.Field.SINGLE to nodeCount.toDouble()),
                            )
                        )
                    )
                } else if (state == ConnectionState.DISCONNECTED) {
                    emitter.onNext(StreamState.Searching)
                } else {
                    emitter.onNext(StreamState.Searching)
                }
                delay(2000) // Update every 2 seconds
            }
        }
        emitter.setCancellable {
            job.cancel()
            Timber.d("Mesh node count stream cancelled")
        }
    }
}
