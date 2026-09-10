package com.example.boschlaser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BoschProtocolTest {
    @Test
    fun commandBytesMatchVerifiedDesktopCommands() {
        assertEquals("c0 55 02 01 00 1a", BoschProtocol.enableMeasurements.hex())
        assertEquals("c0 56 01 00 1e", BoschProtocol.greenMeasureKey.hex())
        assertEquals("c0 40 01 01 5c", BoschProtocol.continuousPoll.hex())
        assertEquals("c0 40 01 02 10", BoschProtocol.continuousStop.hex())
    }

    @Test
    fun parsesDistanceForEverySupportedReference() {
        for (reference in 0..2) {
            val value = BoschProtocol.parseMeasurement(measurementFrame(2.5f, reference))
            assertNotNull(value)
            assertEquals(2500f, value!!.millimeters, 0.01f)
        }
    }

    @Test
    fun parsesAdditionResultAndCurrentComponent() {
        val value = BoschProtocol.parseMeasurement(
            measurementFrame(3.5f, reference = 2, mode = 16, component1 = 1.2f, component2 = 2.3f),
        )
        assertEquals("距离加法", value!!.operation)
        assertEquals(2300f, value.millimeters, 0.1f)
        assertEquals(3.5f, value.accumulatedMeters!!, 0.001f)
    }

    @Test
    fun reassemblesSplitAndCombinedFrames() {
        val longFrame = hex("00 13 47 4c 4d 20 35 30 2d 32 37 20 43 47 00 00 00 00 00 00 00 06")
        val heartbeat = hex("c0 11 00 3a")
        val assembler = MtFrameAssembler()
        assertTrue(assembler.feed(longFrame.copyOfRange(0, 20)).isEmpty())
        assertEquals(listOf(longFrame, heartbeat).map { it.hex() }, assembler.feed(longFrame.copyOfRange(20, 22) + heartbeat).map { it.hex() })
    }

    @Test
    fun recognizesOnlyValidCommand17() {
        assertTrue(BoschProtocol.isCommand17(hex("c0 11 00 3a")))
        assertFalse(BoschProtocol.isCommand17(hex("c0 11 00 00")))
    }

    @Test
    fun parsesCommand64Distance() {
        val body = byteArrayOf(0, 4, 0x72, 0x60, 0, 0)
        val frame = body + BoschProtocol.crc8(body).toByte()
        assertEquals(1234.5f, BoschProtocol.parseContinuousMillimeters(frame)!!, 0.01f)
    }

    private fun measurementFrame(
        result: Float,
        reference: Int,
        mode: Int = 1,
        component1: Float = 0f,
        component2: Float = 0f,
    ): ByteArray {
        val body = ByteArray(19)
        body[0] = 0xC0.toByte()
        body[1] = 0x55
        body[2] = 16
        body[3] = ((mode shl 2) or reference).toByte()
        putFloat(body, 7, result)
        putFloat(body, 11, component1)
        putFloat(body, 15, component2)
        return body + BoschProtocol.crc8(body).toByte()
    }

    private fun putFloat(destination: ByteArray, offset: Int, value: Float) {
        ByteBuffer.wrap(destination, offset, 4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value)
    }

    private fun hex(value: String): ByteArray =
        value.split(" ").filter { it.isNotEmpty() }.map { it.toInt(16).toByte() }.toByteArray()
}
