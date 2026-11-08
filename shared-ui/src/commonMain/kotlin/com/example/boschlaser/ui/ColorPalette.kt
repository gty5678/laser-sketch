package com.example.boschlaser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

data class ColorOption(val argb: Int, val label: String)

data class CustomColorLabels(val title: String, val hex: String, val wheel: String, val brightness: String, val apply: String, val cancel: String)

internal fun parseCustomColor(value: String): Int? {
    val hex = value.removePrefix("#")
    if (hex.length != 6 || hex.any { it.digitToIntOrNull(16) == null }) return null
    return hex.toIntOrNull(16)?.or(0xFF000000.toInt())
}

internal fun customColorHex(argb: Int): String = (argb and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')

/** ARGB values are document properties; the palette owns no selected annotation. */
@Composable
fun ColorPalette(selected: Int?, label: String, colors: List<ColorOption>, customLabels: CustomColorLabels? = null, onColor: (Int) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var hex by remember { mutableStateOf("") }
    var wheel by remember { mutableStateOf(wheelColor(0xFFFF0000.toInt())) }
    LaunchedEffect(selected == null) { if (selected == null) editing = false }
    Row(Modifier.padding(horizontal = 6.dp).horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        colors.forEach { option ->
            val active = selected == option.argb
            Box(Modifier.size(48.dp).selectable(selected = active, enabled = selected != null,
                role = Role.RadioButton, onClick = { onColor(option.argb) })
                .semantics { contentDescription = option.label }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(34.dp).background(Color(option.argb), CircleShape)
                    .border(if (active) 3.dp else 2.dp,
                        if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface, CircleShape))
            }
        }
        if (customLabels != null) {
            TextButton(enabled = selected != null, onClick = {
                hex = customColorHex(selected ?: 0xFF000000.toInt())
                wheel = wheelColor(selected ?: 0xFF000000.toInt()); editing = true
            }, modifier = Modifier.testTag("annotation-custom-color")) {
                if (selected != null && colors.none { it.argb == selected }) {
                    Box(Modifier.size(24.dp).background(Color(selected), CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                    Spacer(Modifier.width(6.dp))
                }
                Text(customLabels.title)
            }
        }
    }
    if (editing && customLabels != null) {
        val parsed = parseCustomColor(hex)
        val preview = parsed ?: selected ?: 0xFF000000.toInt()
        AlertDialog(onDismissRequest = { editing = false }, title = { Text(customLabels.title) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().height(48.dp).background(Color(preview))
                    .border(1.dp, MaterialTheme.colorScheme.outline))
                OutlinedTextField(value = hex, onValueChange = { hex = it; parseCustomColor(it)?.let { color -> wheel = wheelColor(color) } }, singleLine = true,
                    label = { Text(customLabels.hex) }, prefix = { Text("#") }, isError = parsed == null,
                    modifier = Modifier.fillMaxWidth().testTag("annotation-color-hex"))
                AnnotationColorWheel(wheel, customLabels.wheel, customLabels.brightness) {
                    wheel = it
                    hex = customColorHex(it.argb())
                }
            } },
            confirmButton = { TextButton(enabled = parsed != null, onClick = {
                parsed?.let(onColor); editing = false
            }) { Text(customLabels.apply) } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(customLabels.cancel) } })
    }
}
