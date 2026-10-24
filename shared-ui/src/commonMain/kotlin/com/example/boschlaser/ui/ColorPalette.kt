package com.example.boschlaser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

data class ColorOption(val argb: Int, val label: String)

/** ARGB values are document properties; the palette owns no selected annotation. */
@Composable
fun ColorPalette(selected: Int?, label: String, colors: List<ColorOption>, onColor: (Int) -> Unit) {
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
    }
}
