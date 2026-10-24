package com.example.boschlaser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue

/** Draft text and cursor are UI state; the host sends only text changes to the editor. */
@Composable
fun AnnotationLabelProperties(text: TextFieldValue?, angle: TextFieldValue?, textHint: String, angleHint: String,
    degree: String, dismissFocusSignal: Int, onText: (TextFieldValue) -> Unit, onAngle: (TextFieldValue) -> Unit) {
    val focus = LocalFocusManager.current
    LaunchedEffect(dismissFocusSignal) { focus.clearFocus(force = true) }
    Column {
        if (text != null) {
            OutlinedTextField(value = text, onValueChange = onText, singleLine = true,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = textHint },
                placeholder = { Text(textHint) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
        }
        if (angle != null) {
            OutlinedTextField(value = angle, onValueChange = onAngle, singleLine = true,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = angleHint },
                placeholder = { Text(angleHint) }, suffix = { Text(degree) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
    }
}
