package com.unclebike.meshride.ble

import com.google.protobuf.ByteString
import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import java.io.ByteArrayOutputStream

/**
 * Minimal hand-written Meshtastic protobuf codec.
 * Only decodes the fields we need: FromRadio, MeshPacket, Data, User, NodeInfo.
 * Avoids depending on the full meshtastic-protobufs library.
 *
 * Field numbers match https://github.com/meshtastic/protobufs/blob/master/meshtastic/mesh.proto
 */
object MeshtasticProtos {

    // Portnum values we care about
    const val PORTNUM_TEXT_MESSAGE = 1
    const val PORTNUM_NODEINFO = 4
    const val PORTNUM_POSITION = 3

    /**
     * Decoded FromRadio message - the top-level wrapper from the device.
     */
    sealed class FromRadio {
        data class Packet(val meshPacket: DecodedMeshPacket) : FromRadio()
        data class NodeInfoVariant(val nodeInfo: DecodedNodeInfo) : FromRadio()
        data class MyInfo(val myNodeNum: Long) : FromRadio()
        data class ConfigComplete(val configCompleteId: Long) : FromRadio()
        data object Unknown : FromRadio()
    }

    /**
     * Decoded MeshPacket with the fields we need.
     */
    data class DecodedMeshPacket(
        val from: Long = 0,
        val to: Long = 0,
        val channel: Int = 0,
        val id: Long = 0,
        val rxTime: Long = 0,
        val rxSnr: Float = 0f,
        val rxRssi: Int = 0,
        val hopLimit: Int = 0,
        val decoded: DecodedData? = null,
    )

    /**
     * Decoded Data payload.
     */
    data class DecodedData(
        val portnum: Int = 0,
        val payload: ByteArray = ByteArray(0),
        val requestId: Long = 0,
        val wantResponse: Boolean = false,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is DecodedData) return false
            return portnum == other.portnum && payload.contentEquals(other.payload)
        }
        override fun hashCode(): Int = 31 * portnum + payload.contentHashCode()
    }

    /**
     * Decoded User info from NodeInfo packets.
     */
    data class DecodedUser(
        val id: String = "",
        val longName: String = "",
        val shortName: String = "",
        val hwModel: Int = 0,
    )

    /**
     * Decoded NodeInfo.
     */
    data class DecodedNodeInfo(
        val num: Long = 0,
        val user: DecodedUser? = null,
        val lastHeard: Long = 0,
        val snr: Float = 0f,
        val channel: Int = 0,
    )

    /**
     * Parse a FromRadio protobuf from raw bytes received from the BLE FromRadio characteristic.
     */
    fun parseFromRadio(bytes: ByteArray): FromRadio {
        if (bytes.isEmpty()) return FromRadio.Unknown
        val input = CodedInputStream.newInstance(bytes)
        var packet: DecodedMeshPacket? = null
        var nodeInfo: DecodedNodeInfo? = null
        var myNodeNum: Long = 0
        var configCompleteId: Long = 0

        while (!input.isAtEnd) {
            val tag = input.readTag()
            val fieldNumber = tag ushr 3
            when (fieldNumber) {
                // field 2: packet (MeshPacket)
                2 -> {
                    val length = input.readRawVarint32()
                    val limit = input.pushLimit(length)
                    packet = parseMeshPacket(input)
                    input.popLimit(limit)
                }
                // field 4: nodeInfo (NodeInfo)
                4 -> {
                    val length = input.readRawVarint32()
                    val limit = input.pushLimit(length)
                    nodeInfo = parseNodeInfo(input)
                    input.popLimit(limit)
                }
                // field 5: my_info (MyNodeInfo)
                5 -> {
                    val length = input.readRawVarint32()
                    val limit = input.pushLimit(length)
                    while (!input.isAtEnd) {
                        val innerTag = input.readTag()
                        val innerField = innerTag ushr 3
                        if (innerField == 1) {
                            myNodeNum = input.readUInt32().toLong()
                        } else {
                            input.skipField(innerTag)
                        }
                    }
                    input.popLimit(limit)
                }
                // field 8: config_complete_id
                8 -> {
                    configCompleteId = input.readUInt32().toLong()
                }
                else -> input.skipField(tag)
            }
        }

        return when {
            packet != null -> FromRadio.Packet(packet)
            nodeInfo != null -> FromRadio.NodeInfoVariant(nodeInfo)
            myNodeNum > 0 -> FromRadio.MyInfo(myNodeNum)
            configCompleteId > 0 -> FromRadio.ConfigComplete(configCompleteId)
            else -> FromRadio.Unknown
        }
    }

    private fun parseMeshPacket(input: CodedInputStream): DecodedMeshPacket {
        var from = 0L
        var to = 0L
        var channel = 0
        var id = 0L
        var rxTime = 0L
        var rxSnr = 0f
        var rxRssi = 0
        var hopLimit = 0
        var decoded: DecodedData? = null

        while (!input.isAtEnd) {
            val tag = input.readTag()
            val fieldNumber = tag ushr 3
            when (fieldNumber) {
                1 -> from = input.readFixed32().toLong() and 0xFFFFFFFFL // from (fixed32)
                2 -> to = input.readFixed32().toLong() and 0xFFFFFFFFL   // to (fixed32)
                3 -> channel = input.readUInt32()                        // channel
                // field 4: decoded (Data) - length-delimited
                4 -> {
                    val length = input.readRawVarint32()
                    val limit = input.pushLimit(length)
                    decoded = parseData(input)
                    input.popLimit(limit)
                }
                6 -> id = input.readFixed32().toLong() and 0xFFFFFFFFL   // id (fixed32)
                7 -> rxTime = input.readFixed32().toLong() and 0xFFFFFFFFL // rx_time (fixed32)
                8 -> rxSnr = input.readFloat()                           // rx_snr
                11 -> hopLimit = input.readUInt32()                      // hop_limit
                // field 15: rx_rssi (int32)
                15 -> rxRssi = input.readInt32()
                else -> input.skipField(tag)
            }
        }

        return DecodedMeshPacket(from, to, channel, id, rxTime, rxSnr, rxRssi, hopLimit, decoded)
    }

    private fun parseData(input: CodedInputStream): DecodedData {
        var portnum = 0
        var payload = ByteArray(0)
        var requestId = 0L
        var wantResponse = false

        while (!input.isAtEnd) {
            val tag = input.readTag()
            val fieldNumber = tag ushr 3
            when (fieldNumber) {
                1 -> portnum = input.readEnum()          // portnum (PortNum enum)
                2 -> payload = input.readBytes().toByteArray() // payload (bytes)
                6 -> requestId = input.readFixed32().toLong() and 0xFFFFFFFFL // request_id
                7 -> wantResponse = input.readBool()     // want_response
                else -> input.skipField(tag)
            }
        }

        return DecodedData(portnum, payload, requestId, wantResponse)
    }

    private fun parseNodeInfo(input: CodedInputStream): DecodedNodeInfo {
        var num = 0L
        var user: DecodedUser? = null
        var lastHeard = 0L
        var snr = 0f
        var channel = 0

        while (!input.isAtEnd) {
            val tag = input.readTag()
            val fieldNumber = tag ushr 3
            when (fieldNumber) {
                1 -> num = input.readUInt32().toLong()
                2 -> {
                    val length = input.readRawVarint32()
                    val limit = input.pushLimit(length)
                    user = parseUser(input)
                    input.popLimit(limit)
                }
                5 -> lastHeard = input.readFixed32().toLong() and 0xFFFFFFFFL
                7 -> snr = input.readFloat()
                8 -> channel = input.readUInt32()
                else -> input.skipField(tag)
            }
        }

        return DecodedNodeInfo(num, user, lastHeard, snr, channel)
    }

    fun parseUser(input: CodedInputStream): DecodedUser {
        var id = ""
        var longName = ""
        var shortName = ""
        var hwModel = 0

        while (!input.isAtEnd) {
            val tag = input.readTag()
            val fieldNumber = tag ushr 3
            when (fieldNumber) {
                1 -> id = input.readString()
                2 -> longName = input.readString()
                3 -> shortName = input.readString()
                5 -> hwModel = input.readEnum()
                else -> input.skipField(tag)
            }
        }

        return DecodedUser(id, longName, shortName, hwModel)
    }

    /**
     * Build a ToRadio packet requesting the device to send its full config/nodedb.
     * This is sent after initial BLE connection to bootstrap.
     */
    fun buildWantConfigPacket(configId: Int = 42): ByteArray {
        val baos = ByteArrayOutputStream()
        val output = CodedOutputStream.newInstance(baos)
        // ToRadio field 3: want_config_id (uint32)
        output.writeUInt32(3, configId)
        output.flush()
        return baos.toByteArray()
    }

    /**
     * Build a ToRadio packet containing a text message to send on the mesh.
     */
    fun buildTextMessagePacket(text: String, channel: Int = 0): ByteArray {
        // First build the Data sub-message
        val dataBytes = run {
            val baos = ByteArrayOutputStream()
            val output = CodedOutputStream.newInstance(baos)
            output.writeEnum(1, PORTNUM_TEXT_MESSAGE) // portnum = TEXT_MESSAGE_APP
            output.writeBytes(2, ByteString.copyFromUtf8(text)) // payload
            output.flush()
            baos.toByteArray()
        }

        // Then build the MeshPacket
        val meshPacketBytes = run {
            val baos = ByteArrayOutputStream()
            val output = CodedOutputStream.newInstance(baos)
            output.writeFixed32(2, 0xFFFFFFFF.toInt()) // to = broadcast
            output.writeUInt32(3, channel) // channel
            output.writeBytes(4, ByteString.copyFrom(dataBytes)) // decoded (Data)
            output.writeBool(12, true) // want_ack
            output.flush()
            baos.toByteArray()
        }

        // Finally wrap in ToRadio
        val baos = ByteArrayOutputStream()
        val output = CodedOutputStream.newInstance(baos)
        // ToRadio field 1: packet (MeshPacket)
        output.writeBytes(1, ByteString.copyFrom(meshPacketBytes))
        output.flush()
        return baos.toByteArray()
    }
}
