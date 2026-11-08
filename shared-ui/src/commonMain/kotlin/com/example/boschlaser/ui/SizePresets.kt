package com.example.boschlaser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun ColumnSizePresets(width: String, depth: String?, onSelect: (String) -> Unit) {
    SizePresets(listOf(300, 400, 500, 600), width, depth, depth != null, onSelect)
}

@Composable
fun OpeningWidthPresets(width: String, onSelect: (String) -> Unit) {
    SizePresets(listOf(800, 900, 1000, 1100, 1200), width, null, false, onSelect)
}

@Composable
private fun SizePresets(sizes: List<Int>, width: String, depth: String?, square: Boolean, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        sizes.forEach { size ->
            FilterChip(selected = width.toDoubleOrNull() == size.toDouble() &&
                (!square || depth?.toDoubleOrNull() == size.toDouble()),
                onClick = { onSelect(size.toString()) },
                label = { Text(if (square) "$size×$size" else size.toString()) })
        }
    }
}

@Composable
fun ColumnAnglePresets(angle: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0, 30, 45, 60, 90, 180).forEach { degrees ->
            FilterChip(selected = angle.toDoubleOrNull() == degrees.toDouble(),
                onClick = { onSelect(degrees.toString()) }, label = { Text("$degrees°") })
        }
    }
}
