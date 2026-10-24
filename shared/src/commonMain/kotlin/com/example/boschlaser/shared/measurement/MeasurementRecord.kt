package com.example.boschlaser.shared.measurement

/** Diagnostic bytes are copied at the boundary; callers cannot mutate a persisted record. */
class MeasurementRecord(val measurement: Measurement, raw: ByteArray = byteArrayOf(), val legacyOperation: String? = null) {
    private val frame = raw.copyOf()
    val raw: ByteArray get() = frame.copyOf()
    override fun equals(other: Any?): Boolean = other is MeasurementRecord && measurement == other.measurement &&
        frame.contentEquals(other.frame) && legacyOperation == other.legacyOperation
    override fun hashCode(): Int = 31 * (31 * measurement.hashCode() + frame.contentHashCode()) + (legacyOperation?.hashCode() ?: 0)
}
