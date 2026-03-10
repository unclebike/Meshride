package com.unclebike.meshride.datatypes

import android.widget.RemoteViews
import com.unclebike.meshride.R
import com.unclebike.meshride.ble.MeshtasticConnection
import com.unclebike.meshride.data.MeshPacket
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MeshLastMessageDataType(
    private val ext: KarooExtension,
    private val connection: MeshtasticConnection,
) : DataTypeImpl(ext.extension, TYPE_ID) {

    companion object {
        const val TYPE_ID = "mesh-last-message"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastMessage: MeshPacket? = null

    override fun startStream(emitter: Emitter<StreamState>) {
        Timber.d("Starting mesh last message stream")
        val job = scope.launch {
            connection.incomingMessages.collect { packet ->
                lastMessage = packet
                emitter.onNext(
                    StreamState.Streaming(
                        DataPoint(
                            dataTypeId = dataTypeId,
                            values = mapOf(DataType.Field.SINGLE to packet.timestamp.toDouble()),
                        )
                    )
                )
            }
        }
        emitter.setCancellable {
            job.cancel()
            Timber.d("Mesh last message stream cancelled")
        }
    }

    override fun startView(context: android.content.Context, config: ViewConfig, emitter: ViewEmitter) {
        Timber.d("Starting mesh last message view")
        val job = scope.launch {
            connection.incomingMessages.collect { packet ->
                lastMessage = packet
                updateView(context, emitter, packet)
            }
        }
        // Also render current state immediately
        lastMessage?.let { updateView(context, emitter, it) }
            ?: run { updateEmptyView(context, emitter) }

        emitter.setCancellable {
            job.cancel()
            Timber.d("Mesh last message view cancelled")
        }
    }

    private fun updateView(context: android.content.Context, emitter: ViewEmitter, packet: MeshPacket) {
        val remoteViews = RemoteViews(context.packageName, R.layout.mesh_last_message_field)
        val initial = packet.senderShortName.firstOrNull()?.uppercase() ?: "?"
        remoteViews.setTextViewText(R.id.text_sender_avatar, initial)
        remoteViews.setTextViewText(R.id.text_message, "${packet.senderName}: ${packet.messageText}")
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        remoteViews.setTextViewText(R.id.text_timestamp, timeFormat.format(Date(packet.timestamp)))
        emitter.updateView(remoteViews)
    }

    private fun updateEmptyView(context: android.content.Context, emitter: ViewEmitter) {
        val remoteViews = RemoteViews(context.packageName, R.layout.mesh_last_message_field)
        remoteViews.setTextViewText(R.id.text_sender_avatar, "?")
        remoteViews.setTextViewText(R.id.text_message, context.getString(R.string.no_messages))
        remoteViews.setTextViewText(R.id.text_timestamp, "")
        emitter.updateView(remoteViews)
    }
}
