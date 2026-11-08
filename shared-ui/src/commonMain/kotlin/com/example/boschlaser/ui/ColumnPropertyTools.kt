package com.example.boschlaser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.boschlaser.shared.model.ColumnFill

/** Creation and selection expose the same compact fill, size and angle summaries. */
@Composable
fun ColumnPropertyTools(fill: ColumnFill, fillLabels: List<String>, labels: List<String>,
    onFill: (ColumnFill) -> Unit, onProperty: (Int) -> Unit) {
    ScrollableTools(Modifier.fillMaxWidth()) {
        Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) { ColumnFillPicker(fill, fillLabels, onFill) }
        labels.forEachIndexed { index, label ->
            TextButton(onClick = { onProperty(index) }, modifier = Modifier.semantics { contentDescription = label }) {
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
