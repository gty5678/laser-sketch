package com.example.boschlaser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** Millimetre presets submit through the host’s normal validation. */
@Composable
fun WallThicknessPresets(value: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(50, 60, 100, 120, 200, 240, 300).forEach { thickness ->
            FilterChip(selected = value.toDoubleOrNull() == thickness.toDouble(),
                onClick = { onSelect(thickness.toString()) }, label = { Text(thickness.toString()) })
        }
    }
}
