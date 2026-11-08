package com.example.boschlaser.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.boschlaser.shared.ble.BlePhase
import com.example.boschlaser.shared.measurement.LaserDevicePhase
import com.example.boschlaser.shared.measurement.MeasurementCsv

@Composable
internal fun IosDevicePanel(services: IosAppServices) {
    val state by services.transport.state.collectAsState()
    val device by services.device.state.collectAsState()
    val found by services.transport.found.collectAsState()
    val busy by services.busy.collectAsState()
    val connectionFailure by services.connectionFailure.collectAsState()
    val connectionRequested by services.connectionRequested.collectAsState()
    Column {
        Text(label("devices"), style = MaterialTheme.typography.titleLarge)
        Text(label("ble_phase_${state.phase.name.lowercase()}"))
        (state.failure ?: connectionFailure)?.let { Text(label("ble_${it.name.lowercase()}"), color = MaterialTheme.colorScheme.error) }
        Text(state.device?.name.orEmpty())
        Button(enabled = !busy && device.phase != LaserDevicePhase.READY, onClick = services::scan) { Text(label("scan")) }
        OutlinedButton(enabled = state.phase != BlePhase.DISCONNECTED || busy || connectionRequested, onClick = services::disconnect) { Text(label("disconnect")) }
        found.forEach { target -> Button(enabled = !busy, onClick = { services.connect(target) }) { Text(target.name ?: label("bosch_device")) } }
    }
}

@Composable
internal fun IosMeasurementPanel(services: IosAppServices, platform: IosPlatform) {
    val records by services.measurements.records.collectAsState()
    val device by services.device.state.collectAsState()
    MeasurementScreen(records.asReversed().map { MeasurementRowState(it.measurement.id, millimeters(it.measurement.valueMm)) },
        records.lastOrNull()?.let { millimeters(it.measurement.valueMm) }.orEmpty(), device.phase == LaserDevicePhase.READY,
        MeasurementLabels(label("current_measurement"), label("history"), label("measure"), label("clear"), label("export_csv"),
            label("empty_measurements"), label("delete"), label("undo"), label("pending_delete")),
        moreLabel = { label("more") }, onMeasure = { services.run { services.device.measure() } },
        onClear = { services.run { services.measurements.clear() } },
        onDelete = { id -> services.run { services.measurements.delete(id) } }, onExport = {
            services.run {
                val text = StringBuilder()
                MeasurementCsv.write(records, listOf("csv_index", "csv_time", "csv_operation", "csv_mm", "csv_m", "csv_total", "csv_raw").map(::label),
                    time = { it.toString() }, operation = { it.measurement.operation?.name.orEmpty() },
                    decimal = ::fixedDecimal, append = { text.append(it) })
                platform.share(text.toString().encodeToByteArray(), "csv")
            }
        })
}
