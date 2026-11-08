package com.example.boschlaser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
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

/** Manual input remains a draft until Apply; presets submit through the same host validation. */
@Composable
fun NumberProperty(value: TextFieldValue, label: String, applyLabel: String,
    onValue: (TextFieldValue) -> Unit, onApply: () -> Unit, accessory: @Composable () -> Unit = {}, error: String? = null, openingWidthPresets: Boolean = false, columnAnglePresets: Boolean = false) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = value, onValueChange = onValue, label = { Text(label) }, isError = error != null, supportingText = error?.let { message -> { Text(message) } }, singleLine = true, shape = MaterialTheme.shapes.medium,
                modifier = Modifier.weight(1f).semantics { contentDescription = label },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onApply() }))
            accessory()
            Button(onClick = onApply, modifier = Modifier.semantics { contentDescription = "$applyLabel $label" }) {
                Text(applyLabel)
            }
        }
        if (columnAnglePresets) ColumnAnglePresets(value.text) { selected ->
            onValue(TextFieldValue(selected, androidx.compose.ui.text.TextRange(0, selected.length)))
            onApply()
        }
        if (openingWidthPresets) OpeningWidthPresets(value.text) { selected ->
            onValue(TextFieldValue(selected, androidx.compose.ui.text.TextRange(0, selected.length)))
            onApply()
        }
    }
}
