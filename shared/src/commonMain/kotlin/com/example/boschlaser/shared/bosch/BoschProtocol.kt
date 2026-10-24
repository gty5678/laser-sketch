package com.example.boschlaser.shared.bosch

enum class MeasurementOperation { ADD, SUBTRACT }
data class BoschMeasurement(
    val millimeters: Double,
    val operation: MeasurementOperation? = null,
    val accumulatedMillimeters: Double? = null,
)

/** Binary protocol only. Clock, display labels, logging and transport belong to adapters. */
object BoschProtocol {
    const val SERVICE_UUID = "02a6c0d0-0451-4000-b000-fb3210111989"
    const val CHARACTERISTIC_UUID = "02a6c0d1-0451-4000-b000-fb3210111989"
    val enableMeasurements get() = longRequest(85, byteArrayOf(1, 0))
    val greenMeasureKey get() = longRequest(86, byteArrayOf(0))
    val continuousPoll get() = longRequest(64, byteArrayOf(1))
    val continuousStop get() = longRequest(64, byteArrayOf(2))

    fun crc8(data: ByteArray, endExclusive: Int = data.size): Int {
        require(endExclusive in 0..data.size)
        var value = 0xAA
        for (index in 0 until endExclusive) {
            val byte = unsigned(data[index])
            for (bit in 0 until 8) {
                val feedback = (value ushr 7) xor ((byte ushr (7 - bit)) and 1)
                value = (value shl 1) and 0xFF
                if (feedback != 0) value = value xor 0xA6
            }
        }
        return value
    }
    fun longRequest(command: Int, data: ByteArray = byteArrayOf()): ByteArray {
        require(command in 0..255 && data.size <= 254)
        val result = ByteArray(data.size + 4)
        result[0] = 0xC0.toByte()
        result[1] = command.toByte()
        result[2] = data.size.toByte()
        data.copyInto(result, 3)
        result[result.lastIndex] = crc8(result, result.lastIndex).toByte()
        return result
    }
    fun isCommand17(frame: ByteArray) =
        frame.size == 4 && unsigned(frame[0]) and 0xC0 == 0xC0 && unsigned(frame[1]) == 17 &&
            unsigned(frame[2]) == 0 && crc8(frame, frame.lastIndex) == unsigned(frame.last())
    fun parseLaserState(frame: ByteArray): Boolean? {
        if (frame.size < 6 || unsigned(frame[0]) and 0xC0 != 0xC0 || unsigned(frame[1]) != 85) return null
        val length = unsigned(frame[2])
        if (length < 2 || frame.size != length + 4 || crc8(frame, frame.lastIndex) != unsigned(frame.last())) return null
        return unsigned(frame[4]) and 1 != 0
    }
    fun parseMeasurement(frame: ByteArray): BoschMeasurement? {
        if (frame.size != 20 || unsigned(frame[0]) != 0xC0 || unsigned(frame[1]) != 85 || unsigned(frame[2]) != 16) return null
        if (crc8(frame, frame.lastIndex) != unsigned(frame.last())) return null
        val mode = unsigned(frame[3]) ushr 2
        val result = floatAt(frame, 7)
        if (!result.isFinite()) return null
        return when (mode) {
            1 -> if (result in 0f..1000f) BoschMeasurement(result.toDouble() * 1000.0) else null
            16, 17 -> {
                val component = floatAt(frame, 15)
                if (!component.isFinite() || component !in 0f..1000f || result !in -1000f..1000f) null
                else BoschMeasurement(component.toDouble() * 1000.0,
                    if (mode == 16) MeasurementOperation.ADD else MeasurementOperation.SUBTRACT,
                    result.toDouble() * 1000.0)
            }
            else -> null
        }
    }
    fun parseContinuousMillimeters(frame: ByteArray): Double? {
        if (frame.size != 7 || unsigned(frame[0]) and 0x1F != 0 || unsigned(frame[1]) != 4) return null
        if (crc8(frame, frame.lastIndex) != unsigned(frame.last())) return null
        val raw = littleU32(frame, 2)
        return if (raw == 0L) null else raw * 0.05
    }
    fun floatAt(data: ByteArray, offset: Int) = Float.fromBits(littleU32(data, offset).toInt())
    fun littleU32(data: ByteArray, offset: Int = 0): Long {
        require(offset >= 0 && offset <= data.size - 4)
        return (0..3).fold(0L) { value, index -> value or (unsigned(data[offset + index]).toLong() shl (index * 8)) }
    }
    private fun unsigned(value: Byte) = value.toInt() and 0xFF
}

/** Caller supplies monotonic elapsed time so fragmentation tests and platforms share one implementation. */
class MtFrameAssembler {
    private var buffer = byteArrayOf()
    private var lastChunkAt: Long? = null
    fun reset() { buffer = byteArrayOf(); lastChunkAt = null }
    fun feed(chunk: ByteArray, elapsedMillis: Long): List<ByteArray> {
        val previous = lastChunkAt
        if (buffer.isNotEmpty() && previous != null &&
            (elapsedMillis < previous || elapsedMillis - previous > 750)) reset()
        buffer += chunk
        lastChunkAt = elapsedMillis
        val frames = mutableListOf<ByteArray>()
        while (buffer.isNotEmpty()) {
            val expected = when (buffer[0].toInt() and 0xC0) {
                0x00 -> if (buffer.size >= 2) (buffer[1].toInt() and 0xFF) + 3 else null
                0xC0 -> if (buffer.size >= 3) (buffer[2].toInt() and 0xFF) + 4 else null
                else -> buffer.size
            } ?: break
            if (buffer.size < expected) break
            frames += buffer.copyOfRange(0, expected)
            buffer = buffer.copyOfRange(expected, buffer.size)
        }
        if (buffer.size > 1024) reset()
        return frames
    }
}
