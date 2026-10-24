package com.example.boschlaser.shared.measurement

import com.example.boschlaser.shared.bosch.MeasurementOperation

/** Millimetres and epoch time only: no UI, geometry or platform handles. */
data class Measurement(
    val id: String,
    val valueMm: Double,
    val timestamp: Long,
    val deviceId: String? = null,
    val operation: MeasurementOperation? = null,
    val accumulatedMm: Double? = null,
) {
    init {
        require(id.isNotBlank() && valueMm.isFinite() && valueMm >= 0.0)
        require(accumulatedMm == null || accumulatedMm.isFinite())
    }
}
