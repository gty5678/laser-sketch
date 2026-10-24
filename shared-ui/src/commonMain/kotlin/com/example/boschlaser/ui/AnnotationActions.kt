package com.example.boschlaser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** The finish button stays centered; deletion keeps its original 64 dp lower position. */
@Composable
fun AnnotationActions(showFinish: Boolean, showDelete: Boolean, finishLabel: String, deleteLabel: String,
    onFinish: () -> Unit, onDelete: () -> Unit, finishIcon: @Composable () -> Unit, deleteIcon: @Composable () -> Unit) {
    Box(Modifier.width(52.dp).height(180.dp)) {
        if (showFinish) {
            FloatingActionButton(onClick = onFinish, shape = CircleShape,
                modifier = Modifier.align(Alignment.Center).size(52.dp).semantics { contentDescription = finishLabel },
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                content = finishIcon)
        }
        if (showDelete) {
            FloatingActionButton(onClick = onDelete, shape = CircleShape,
                modifier = Modifier.align(Alignment.Center).offset(y = 64.dp).size(52.dp)
                    .semantics { contentDescription = deleteLabel },
                containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError,
                content = deleteIcon)
        }
    }
}
