package com.example.boschlaser.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.boschlaser.shared.editor.ToolType

data class SketchToolItem(val type: ToolType, val label: String)

/** The host owns the editor and interprets actions; the toolbar only shows the active tool. */
@Composable
fun SketchToolbar(selected: ToolType, tools: List<SketchToolItem>, backLabel: String, settingsLabel: String,
    onBack: () -> Unit, onSettings: () -> Unit, onTool: (ToolType) -> Unit,
    backIcon: @Composable () -> Unit, settingsIcon: @Composable () -> Unit, toolIcon: @Composable (ToolType) -> Unit,
    shareLabel: String = "", pngLabel: String = "", cadLabel: String = "",
    onSharePng: (() -> Unit)? = null, onShareCad: (() -> Unit)? = null,
    shareIcon: @Composable () -> Unit = {}) {
    Surface {
        Row(Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = backLabel }) { backIcon() }
            ScrollableTools(Modifier.weight(1f)) {
                SketchToolButtons(selected, tools, onTool, toolIcon)
            }
            IconButton(onClick = onSettings, modifier = Modifier.semantics { contentDescription = settingsLabel }) { settingsIcon() }
            if (onSharePng != null && onShareCad != null) {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { expanded = true }, modifier = Modifier.semantics { contentDescription = shareLabel }) { shareIcon() }
                    DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text(pngLabel) }, onClick = { expanded = false; onSharePng() })
                        DropdownMenuItem(text = { Text(cadLabel) }, onClick = { expanded = false; onShareCad() })
                    }
                }
            }

        }
    }
}

@Composable
fun SketchToolButtons(selected: ToolType, tools: List<SketchToolItem>, onTool: (ToolType) -> Unit,
    toolIcon: @Composable (ToolType) -> Unit) {
    tools.forEach { tool ->
        IconToggleButton(modifier = Modifier.semantics { contentDescription = tool.label }, checked = selected == tool.type, onCheckedChange = { onTool(tool.type) },
            colors = IconButtonDefaults.iconToggleButtonColors(
                checkedContainerColor = MaterialTheme.colorScheme.primary,
                checkedContentColor = MaterialTheme.colorScheme.onPrimary)) {
            toolIcon(tool.type)
        }
    }
}
