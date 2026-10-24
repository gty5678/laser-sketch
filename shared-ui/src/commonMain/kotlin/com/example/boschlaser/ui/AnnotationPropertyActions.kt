package com.example.boschlaser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun AnnotationAreaPointAction(show: Boolean, label: String, onDelete: () -> Unit,
    icon: @Composable () -> Unit) {
    if (show) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onDelete, modifier = Modifier.size(52.dp)
                .semantics { contentDescription = label }, content = icon)
        }
    }
}
