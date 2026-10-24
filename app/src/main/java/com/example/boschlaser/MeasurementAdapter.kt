package com.example.boschlaser

import android.content.Context
import com.example.boschlaser.shared.bosch.MeasurementOperation
import com.example.boschlaser.shared.measurement.MeasurementRecord

/** Display-only legacy facade; the ID and raw frame remain attached to the shared record. */
internal fun MeasurementRecord.toLegacyMeasurement(context: Context): Measurement {
    val item = measurement
    val operation = legacyOperation ?: when (item.operation) {
        MeasurementOperation.ADD -> context.getString(R.string.measurement_operation_add)
        MeasurementOperation.SUBTRACT -> context.getString(R.string.measurement_operation_subtract)
        null -> null
    }
    return Measurement(item.timestamp, (item.valueMm / 1000.0).toFloat(), operation,
        item.accumulatedMm?.let { (it / 1000.0).toFloat() }, raw, item.id)
}
