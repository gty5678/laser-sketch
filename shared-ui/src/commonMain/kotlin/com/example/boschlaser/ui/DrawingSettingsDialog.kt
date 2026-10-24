package com.example.boschlaser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue

data class DrawingSettingsLabels(val title: String, val grid: String, val gridHint: String,
    val magnifier: String, val magnifierHint: String, val apply: String, val cancel: String,
    val automaticDimensions: String)

/** No platform preference access; the host validates and applies all submitted drafts together. */
@Composable
fun DrawingSettingsDialog(initialGrid: String, initialMagnifier: String, initialAutomaticDimensions: Boolean,
    labels: DrawingSettingsLabels, onApply: (String, String, Boolean) -> Unit, onDismiss: () -> Unit) {
    var grid by remember { mutableStateOf(TextFieldValue(initialGrid, TextRange(0, initialGrid.length))) }
    var magnifier by remember { mutableStateOf(TextFieldValue(initialMagnifier)) }
    var automaticDimensions by remember { mutableStateOf(initialAutomaticDimensions) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(labels.title) }, text = {
        Column {
            Row(Modifier.fillMaxWidth().toggleable(value = automaticDimensions, role = Role.Switch,
                onValueChange = { automaticDimensions = it }), verticalAlignment = Alignment.CenterVertically) {
                Text(labels.automaticDimensions, modifier = Modifier.weight(1f))
                Switch(checked = automaticDimensions, onCheckedChange = null)
            }
            OutlinedTextField(value = grid, onValueChange = { grid = it }, label = { Text(labels.grid) },
                placeholder = { Text(labels.gridHint) }, singleLine = true,
                modifier = Modifier.semantics { contentDescription = labels.grid },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(value = magnifier, onValueChange = { magnifier = it }, label = { Text(labels.magnifier) },
                placeholder = { Text(labels.magnifierHint) }, singleLine = true,
                modifier = Modifier.semantics { contentDescription = labels.magnifier },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
    }, confirmButton = { TextButton(onClick = { onApply(grid.text, magnifier.text, automaticDimensions) }) { Text(labels.apply) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(labels.cancel) } })
}
