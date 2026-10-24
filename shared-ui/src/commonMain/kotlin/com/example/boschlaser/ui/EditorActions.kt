package com.example.boschlaser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Visibility comes from the editor host; actions never mutate a second document. */
@Composable
fun EditorActions(
    showDelete: Boolean,
    showConfirm: Boolean,
    undoLabel: String,
    deleteLabel: String,
    confirmLabel: String,
    onUndo: () -> Unit,
    onDelete: () -> Unit,
    onConfirm: () -> Unit,
    undoIcon: @Composable () -> Unit,
    deleteIcon: @Composable () -> Unit,
    confirmIcon: @Composable () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FloatingActionButton(onClick = onUndo, shape = CircleShape,
            modifier = Modifier.size(56.dp).semantics { contentDescription = undoLabel },
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary, content = undoIcon)
        if (showDelete) {
            FloatingActionButton(onClick = onDelete, shape = CircleShape,
                modifier = Modifier.size(56.dp).semantics { contentDescription = deleteLabel },
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError, content = deleteIcon)
        }
        if (showConfirm) {
            FloatingActionButton(onClick = onConfirm, shape = CircleShape,
                modifier = Modifier.size(56.dp).semantics { contentDescription = confirmLabel },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary, content = confirmIcon)
        }
    }
}
