package com.example.boschlaser.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue

/** Input remains a draft until Apply; parsing and domain validation belong to the host/editor. */
@Composable
fun NumberProperty(value: TextFieldValue, label: String, applyLabel: String,
    onValue: (TextFieldValue) -> Unit, onApply: () -> Unit, accessory: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = value, onValueChange = onValue, label = { Text(label) }, singleLine = true,
            modifier = Modifier.weight(1f).semantics { contentDescription = label },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        accessory()
        TextButton(onClick = onApply, modifier = Modifier.semantics { contentDescription = "$applyLabel $label" }) {
            Text(applyLabel)
        }
    }
}
