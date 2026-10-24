package com.example.boschlaser.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Reserve the row in move mode so the photo viewport does not jump. */
@Composable
fun PlacementOptions(visible: Boolean, continuous: Boolean, label: String, singleLabel: String,
    continuousLabel: String, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(48.dp)) {
        if (visible) {
            Row(Modifier.fillMaxSize().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                FilterChip(selected = !continuous, onClick = { onChange(false) }, label = { Text(singleLabel) },
                    modifier = Modifier.semantics { contentDescription = singleLabel },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary))
                FilterChip(selected = continuous, onClick = { onChange(true) }, label = { Text(continuousLabel) },
                    modifier = Modifier.semantics { contentDescription = continuousLabel },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary))
            }
        }
    }
}
