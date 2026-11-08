package com.example.boschlaser.ui

import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.boschlaser.R
import com.example.boschlaser.shared.bosch.MeasurementOperation
import com.example.boschlaser.shared.measurement.MeasurementService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun AndroidMeasurements(service: MeasurementService, canMeasure: Boolean,
    onMeasure: () -> Unit, onClear: () -> Unit, onExport: () -> Unit, onDelete: (String) -> Unit) {
    val context = LocalContext.current
    val records by service.records.collectAsStateWithLifecycle()
    val labels = MeasurementLabels(context.getString(R.string.measurement_current), context.getString(R.string.measurement_history),
        context.getString(R.string.measurement_measure), context.getString(R.string.measurement_clear), context.getString(R.string.measurement_export),
        context.getString(R.string.measurement_empty), context.getString(R.string.measurement_delete), context.getString(R.string.editor_undo), context.getString(R.string.measurement_pending_delete))
    val rows = records.asReversed().map { record ->
        val item = record.measurement
        val operation = record.legacyOperation ?: context.getString(when (item.operation) {
            MeasurementOperation.ADD -> R.string.measurement_operation_add
            MeasurementOperation.SUBTRACT -> R.string.measurement_operation_subtract
            null -> R.string.measurement_operation_distance
        })
        val total = item.accumulatedMm?.let { context.getString(R.string.measurement_total, "%.1f".format(Locale.US, it)) }.orEmpty()
        MeasurementRowState(item.id, context.getString(R.string.measurement_row,
            SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(item.timestamp)), operation,
            "%.1f".format(Locale.US, item.valueMm), total))
    }
    val distance = records.lastOrNull()?.measurement?.valueMm?.let { context.getString(R.string.measurement_distance, "%.1f".format(Locale.US, it)) }
        ?: context.getString(R.string.measurement_distance_empty)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var session by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { session++ }
    key(session) {
        MeasurementScreen(rows, distance, canMeasure, labels, { context.getString(R.string.measurement_more, it) },
            onMeasure, onClear, onExport, onDelete = { id ->
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) onDelete(id)
            })
    }
}
