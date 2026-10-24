package com.example.boschlaser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Formatted values and selection come from the host; clicks never mutate a document here. */
@Composable
fun RecentMeasurements(labels: List<String>, show: Boolean, onSelect: (Int) -> Unit) {
    if (show && labels.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            labels.take(3).forEachIndexed { index, label ->
                Button(onClick = { onSelect(index) }, modifier = Modifier.weight(1f)) { Text(label) }
            }
        }
    }
}

