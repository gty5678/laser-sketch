package com.example.boschlaser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue

/** A focused numeric entry above the keyboard; the host owns validation and the atomic edit. */
@Composable
fun WallFaceDistanceDialog(initialValue: String, title: String, hint: String, valueLabel: String,
    applyLabel: String, cancelLabel: String, errorLabel: String, onApply: (String) -> Boolean, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(initialValue, TextRange(0, initialValue.length))) }
    var invalid by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    fun submit() { invalid = !onApply(value.text) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        val keyboard = LocalSoftwareKeyboardController.current
        val windowInfo = LocalWindowInfo.current
        LaunchedEffect(windowInfo.isWindowFocused) {
            if (windowInfo.isWindowFocused) { focus.requestFocus(); keyboard?.show() }
        }
        Column {
            Text(hint)
            OutlinedTextField(value = value, onValueChange = { value = it; invalid = false },
                singleLine = true, label = { Text(valueLabel) }, isError = invalid,
                supportingText = if (invalid) ({ Text(errorLabel) }) else null,
                modifier = Modifier.focusRequester(focus).semantics { contentDescription = valueLabel },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }))
        }
    }, confirmButton = { TextButton(onClick = { submit() }) { Text(applyLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(cancelLabel) } })
}
