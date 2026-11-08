package com.example.boschlaser.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Keeps annotation editing behind one compact entry. */
@Composable
fun AnnotationDimensionMenu(title: String, value: String, closeLabel: String, resetKey: Any,
    tag: String = "annotation-dimension-menu",
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit) {
    var expanded by remember(resetKey) { mutableStateOf(false) }
    OutlinedButton(onClick = { expanded = true }, modifier = (if (compact) Modifier.widthIn(max = 200.dp) else Modifier.fillMaxWidth())
        .testTag(tag).semantics { contentDescription = title }) {
        Text(if (value.isBlank()) title else "$title · $value", maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (expanded) AlertDialog(onDismissRequest = { expanded = false }, title = { Text(title) },
        text = { Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp), content = content) },
        confirmButton = { TextButton(onClick = { expanded = false }) { Text(closeLabel) } })
}
