package com.example.boschlaser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue

/** Both size drafts are submitted together, so invalid depth cannot leave a partial width edit. */
@Composable
fun ColumnSizeProperty(width: TextFieldValue, depth: TextFieldValue?, widthLabel: String, depthLabel: String,
    applyLabel: String, onWidth: (TextFieldValue) -> Unit, onDepth: (TextFieldValue) -> Unit, onApply: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = width, onValueChange = onWidth, label = { Text(widthLabel) }, singleLine = true, shape = MaterialTheme.shapes.medium,
            modifier = Modifier.weight(1f).semantics { contentDescription = widthLabel },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        if (depth != null) {
            OutlinedTextField(value = depth, onValueChange = onDepth, label = { Text(depthLabel) }, singleLine = true, shape = MaterialTheme.shapes.medium,
                modifier = Modifier.weight(1f).semantics { contentDescription = depthLabel },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
        Button(onClick = onApply, modifier = Modifier.semantics { contentDescription = "$applyLabel $widthLabel" }) {
            Text(applyLabel)
        }
    }
}
