package com.example.boschlaser.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
fun CreationPropertyTools(labels: List<String>, accessory: @Composable () -> Unit = {}, onSelect: (Int) -> Unit) {
    ScrollableTools(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            TextButton(onClick = { onSelect(index) }, modifier = Modifier.semantics { contentDescription = label }) {
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
        accessory()
    }
}
