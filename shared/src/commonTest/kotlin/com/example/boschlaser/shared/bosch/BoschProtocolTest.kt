package com.example.boschlaser.shared.bosch

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoschProtocolTest {
    @Test
    fun requestsMatchKnownBytes() {
        assertContentEquals(bytes("c0 55 02 01 00 1a"), BoschProtocol.enableMeasurements)
        assertContentEquals(bytes("c0 56 01 00 1e"), BoschProtocol.greenMeasureKey)
        assertContentEquals(bytes("c0 40 01 01 5c"), BoschProtocol.continuousPoll)
        assertContentEquals(bytes("c0 40 01 02 10"), BoschProtocol.continuousStop)
        assertFailsWith<IllegalArgumentException> { BoschProtocol.longRequest(256) }
        assertFailsWith<IllegalArgumentException> { BoschProtocol.longRequest(1, ByteArray(255)) }
    }
    @Test
    fun distanceAllReferencesAndOperationsUseMillimeters() {
        for (reference in 0..2) assertEquals(2500.0, BoschProtocol.parseMeasurement(frame(2.5f, 1, reference))!!.millimeters)
        val add = BoschProtocol.parseMeasurement(frame(3.5f, 16, component = 2.25f))!!
        assertEquals(MeasurementOperation.ADD, add.operation)
        assertEquals(2250.0, add.millimeters)
        assertEquals(3500.0, add.accumulatedMillimeters)
        val subtract = BoschProtocol.parseMeasurement(frame(-1.5f, 17, component = 2.25f))!!
        assertEquals(MeasurementOperation.SUBTRACT, subtract.operation)
        assertEquals(-1500.0, subtract.accumulatedMillimeters)
    }
    @Test
    fun corruptShortUnknownModeAndNonFiniteMeasurementsAreRejected() {
        val valid = frame(2.5f, 1)
        assertNull(BoschProtocol.parseMeasurement(valid.copyOf(19)))
        assertNull(BoschProtocol.parseMeasurement(valid.copyOf().apply { this[7] = 1 }))
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 1001f))
            assertNull(BoschProtocol.parseMeasurement(frame(value, 1)))
        assertNull(BoschProtocol.parseMeasurement(frame(1f, 4)))
        assertNull(BoschProtocol.parseMeasurement(frame(1f, 16, component = Float.NaN)))
    }
    @Test
    fun continuousMeasurementUsesUnsignedLittleEndianAndRejectsZeroOrBadCrc() {
        fun packet(data: ByteArray) = data + BoschProtocol.crc8(data).toByte()
        assertEquals(1234.5, BoschProtocol.parseContinuousMillimeters(packet(bytes("00 04 72 60 00 00")))!!, 1e-6)
        assertEquals(4294967295L * 0.05, BoschProtocol.parseContinuousMillimeters(packet(bytes("00 04 ff ff ff ff"))))
        assertNull(BoschProtocol.parseContinuousMillimeters(packet(bytes("00 04 00 00 00 00"))))
        assertNull(BoschProtocol.parseContinuousMillimeters(bytes("00 04 72 60 00 00 00")))
    }
    @Test
    fun fragmentedAndCombinedFramesAndTimeoutRecovery() {
        val first = BoschProtocol.enableMeasurements
        val heartbeat = bytes("c0 11 00 3a")
        val assembler = MtFrameAssembler()
        assertTrue(assembler.feed(first.copyOf(2), 0).isEmpty())
        val result = assembler.feed(first.copyOfRange(2, first.size) + heartbeat, 100)
        assertEquals(2, result.size)
        assertContentEquals(first, result[0])
        assertContentEquals(heartbeat, result[1])
        assertTrue(assembler.feed(first.copyOf(2), 200).isEmpty())
        assertContentEquals(heartbeat, assembler.feed(heartbeat, 1000).single())
        assembler.feed(first.copyOf(2), 1100)
        assembler.reset()
        assertContentEquals(heartbeat, assembler.feed(heartbeat, 1101).single())
    }
    private fun frame(result: Float, mode: Int, reference: Int = 0, component: Float = 0f): ByteArray {
        val body = ByteArray(19)
        body[0] = 0xC0.toByte(); body[1] = 85; body[2] = 16; body[3] = ((mode shl 2) or reference).toByte()
        fun put(offset: Int, value: Float) {
            for (index in 0..3) body[offset + index] = (value.toBits() ushr (index * 8)).toByte()
        }
        put(7, result); put(15, component)
        return body + BoschProtocol.crc8(body).toByte()
    }
    private fun bytes(value: String) = value.split(" ").map { it.toInt(16).toByte() }.toByteArray()
}
