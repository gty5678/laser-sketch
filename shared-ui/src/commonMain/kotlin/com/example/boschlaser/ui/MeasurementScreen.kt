package com.example.boschlaser.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class MeasurementRowState(val id: String, val text: String)
data class MeasurementLabels(val current: String, val history: String, val measure: String,
    val clear: String, val export: String, val empty: String, val delete: String)

/** Newest first. Paging is display state only; callbacks always carry durable measurement IDs. */
@Composable
fun MeasurementScreen(
    rows: List<MeasurementRowState>,
    distance: String,
    canMeasure: Boolean,
    labels: MeasurementLabels,
    moreLabel: (Int) -> String,
    onMeasure: () -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit,
    onDelete: (String) -> Unit,
) {
    var visibleCount by rememberSaveable { mutableStateOf(100) }
    LaunchedEffect(rows.isEmpty()) { if (rows.isEmpty()) visibleCount = 100 }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(labels.current, style = MaterialTheme.typography.titleLarge)
        Text(distance, style = MaterialTheme.typography.headlineLarge)
        Button(onClick = onMeasure, enabled = canMeasure) { Text(labels.measure) }
        Text(labels.history, style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onClear, enabled = rows.isNotEmpty()) { Text(labels.clear) }
            OutlinedButton(onClick = onExport, enabled = rows.isNotEmpty()) { Text(labels.export) }
        }
        if (rows.isEmpty()) Text(labels.empty, style = MaterialTheme.typography.bodySmall)
        rows.take(visibleCount).forEach { row ->
            key(row.id) {
                Row {
                    SelectionContainer(Modifier.weight(1f)) { Text(row.text, style = MaterialTheme.typography.bodyMedium) }
                    TextButton(onClick = { onDelete(row.id) }) { Text(labels.delete) }
                }
            }
        }
        val hidden = rows.size - visibleCount
        if (hidden > 0) TextButton(onClick = { visibleCount += 100 }) { Text(moreLabel(hidden)) }
    }
}
