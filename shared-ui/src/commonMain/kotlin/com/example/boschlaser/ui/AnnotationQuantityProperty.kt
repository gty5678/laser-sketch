package com.example.boschlaser.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue

@Composable
fun AnnotationQuantityProperty(value: TextFieldValue?, unit: Int, units: List<String>, hint: String, unitLabel: String,
    dismissFocusSignal: Int, onValue: (TextFieldValue) -> Unit, onUnit: (Int) -> Unit) {
    require(unit in units.indices)
    var expanded by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    LaunchedEffect(dismissFocusSignal, value == null) { expanded = false; focus.clearFocus(force = true) }
    if (value != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = value, onValueChange = onValue, singleLine = true,
                modifier = Modifier.weight(2f).semantics { contentDescription = hint },
                placeholder = { Text(hint) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Box(Modifier.weight(.8f)) {
                TextButton(onClick = { expanded = true }, modifier = Modifier.semantics { contentDescription = unitLabel }) {
                    Text(units[unit])
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    units.forEachIndexed { index, name ->
                        DropdownMenuItem(text = { Text(name) }, onClick = { expanded = false; onUnit(index) },
                            modifier = Modifier.semantics { contentDescription = "$unitLabel $name" })
                    }
                }
            }
        }
    }
}
