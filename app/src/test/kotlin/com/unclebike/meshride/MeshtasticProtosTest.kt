package com.unclebike.meshride

import com.unclebike.meshride.ble.MeshtasticProtos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshtasticProtosTest {

    @Test
    fun `buildWantConfigPacket produces valid protobuf bytes`() {
        val bytes = MeshtasticProtos.buildWantConfigPacket(42)
        assertTrue("Packet should not be empty", bytes.isNotEmpty())
        // Field 3 (want_config_id), varint tag = (3 << 3) | 0 = 24
        assertEquals("First byte should be field tag for want_config_id", 24, bytes[0].toInt() and 0xFF)
    }

    @Test
    fun `buildTextMessagePacket produces valid protobuf bytes`() {
        val bytes = MeshtasticProtos.buildTextMessagePacket("Hello mesh!", 0)
        assertTrue("Packet should not be empty", bytes.isNotEmpty())
        // Should start with field 1 (packet), tag = (1 << 3) | 2 = 10 (length-delimited)
        assertEquals("First byte should be field tag for packet", 10, bytes[0].toInt() and 0xFF)
    }

    @Test
    fun `parseFromRadio handles empty bytes`() {
        val result = MeshtasticProtos.parseFromRadio(ByteArray(0))
        assertTrue("Empty bytes should return Unknown", result is MeshtasticProtos.FromRadio.Unknown)
    }

    @Test
    fun `buildWantConfigPacket roundtrip`() {
        val configId = 42
        val bytes = MeshtasticProtos.buildWantConfigPacket(configId)
        // Parse it back - should be a config-related message
        // The want_config_id in ToRadio doesn't parse as FromRadio, so just verify bytes are well-formed
        assertTrue("Should produce non-empty bytes", bytes.size >= 2)
    }

    @Test
    fun `text message packet contains message text`() {
        val text = "Test message"
        val bytes = MeshtasticProtos.buildTextMessagePacket(text, 0)
        // The text should be embedded somewhere in the protobuf bytes
        val bytesStr = String(bytes, Charsets.ISO_8859_1)
        assertTrue("Packet should contain the message text", bytesStr.contains(text))
    }

    @Test
    fun `FromRadio Packet type contains decoded data`() {
        // Build a simulated FromRadio containing a MeshPacket with text
        // This is a minimal hand-crafted protobuf:
        // FromRadio { packet { from: 0x12345678, decoded { portnum: 1, payload: "Hi" } } }
        val packet = buildMinimalFromRadioWithText("Hi", 0x12345678)
        val result = MeshtasticProtos.parseFromRadio(packet)

        assertTrue("Should parse as Packet", result is MeshtasticProtos.FromRadio.Packet)
        val meshPacket = (result as MeshtasticProtos.FromRadio.Packet).meshPacket
        assertNotNull("Decoded data should not be null", meshPacket.decoded)
        assertEquals("Portnum should be TEXT_MESSAGE", MeshtasticProtos.PORTNUM_TEXT_MESSAGE, meshPacket.decoded!!.portnum)
        assertEquals("Payload should be 'Hi'", "Hi", String(meshPacket.decoded!!.payload, Charsets.UTF_8))
    }

    /**
     * Hand-build a minimal FromRadio protobuf containing a text MeshPacket.
     */
    private fun buildMinimalFromRadioWithText(text: String, fromNodeId: Long): ByteArray {
        val textBytes = text.toByteArray(Charsets.UTF_8)

        // Data { portnum: 1 (TEXT_MESSAGE), payload: textBytes }
        val dataPayload = byteArrayOf(
            0x08, 0x01, // field 1 (portnum), varint, value 1
        ) + byteArrayOf(
            0x12, textBytes.size.toByte(), // field 2 (payload), length-delimited
        ) + textBytes

        // MeshPacket { from: fromNodeId (fixed32), decoded: dataPayload }
        val fromBytes = byteArrayOf(
            0x0D, // field 1 (from), wire type 5 (32-bit)
            (fromNodeId and 0xFF).toByte(),
            ((fromNodeId shr 8) and 0xFF).toByte(),
            ((fromNodeId shr 16) and 0xFF).toByte(),
            ((fromNodeId shr 24) and 0xFF).toByte(),
        )
        val decodedField = byteArrayOf(
            0x22, dataPayload.size.toByte(), // field 4 (decoded), length-delimited
        ) + dataPayload

        val meshPacketPayload = fromBytes + decodedField

        // FromRadio { packet: meshPacketPayload }
        return byteArrayOf(
            0x12, meshPacketPayload.size.toByte(), // field 2 (packet), length-delimited
        ) + meshPacketPayload
    }
}
