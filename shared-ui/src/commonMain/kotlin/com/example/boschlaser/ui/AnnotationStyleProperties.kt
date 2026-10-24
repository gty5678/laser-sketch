package com.example.boschlaser.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

data class AnnotationStyleLabels(val size: String, val sizes: List<String>, val background: String,
    val thickness: String, val thicknesses: List<String>)

/** Selection values come from the host projection; only accepted commands update them. */
@Composable
fun AnnotationStyleProperties(textSize: Int?, whiteBackground: Boolean, thickness: Int?, labels: AnnotationStyleLabels,
    onTextSize: (Int) -> Unit, onBackground: (Boolean) -> Unit, onThickness: (Int) -> Unit) {
    require(labels.sizes.size == 3 && labels.thicknesses.size == 3)
    @Composable
    fun choices(title: String, names: List<String>, selected: Int, onChoice: (Int) -> Unit) {
        Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            names.forEachIndexed { index, name ->
                FilterChip(selected = selected == index, onClick = { onChoice(index) }, label = { Text(name) },
                    modifier = Modifier.semantics { contentDescription = "$title $name" },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary))
            }
        }
    }
    Column {
        if (textSize != null) {
            choices(labels.size, labels.sizes, textSize, onTextSize)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = whiteBackground, onCheckedChange = onBackground,
                    modifier = Modifier.semantics { contentDescription = labels.background })
                Text(labels.background, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (thickness != null) choices(labels.thickness, labels.thicknesses, thickness, onThickness)
    }
}
