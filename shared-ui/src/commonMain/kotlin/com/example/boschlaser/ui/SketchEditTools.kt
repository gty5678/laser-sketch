package com.example.boschlaser.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Multi-wall picking and geometry stay in the editor; these controls expose only intent. */
@Composable
fun SketchEditTools(joinActive: Boolean, extendActive: Boolean, joinLabel: String, extendLabel: String,
    onJoin: () -> Unit, onExtend: () -> Unit,
    joinIcon: @Composable () -> Unit, extendIcon: @Composable () -> Unit,
    additionalTools: @Composable () -> Unit = {}) {
    Surface {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconToggleButton(checked = joinActive, onCheckedChange = { onJoin() },
                modifier = Modifier.semantics { contentDescription = joinLabel },
                colors = IconButtonDefaults.iconToggleButtonColors(checkedContainerColor = MaterialTheme.colorScheme.primary,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimary)) { joinIcon() }
            IconToggleButton(checked = extendActive, onCheckedChange = { onExtend() },
                modifier = Modifier.semantics { contentDescription = extendLabel },
                colors = IconButtonDefaults.iconToggleButtonColors(checkedContainerColor = MaterialTheme.colorScheme.primary,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimary)) { extendIcon() }
            additionalTools()
        }
    }
}

@Composable
fun GridSnapControl(enabled: Boolean, label: String, onChange: (Boolean) -> Unit) {
    Surface(shape = MaterialTheme.shapes.large) {
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, modifier = Modifier.padding(top = 14.dp), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = enabled, onCheckedChange = onChange, modifier = Modifier.semantics { contentDescription = label })
        }
    }
}
