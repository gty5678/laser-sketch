package com.example.boschlaser.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.boschlaser.shared.editor.ToolType

data class SketchToolItem(val type: ToolType, val label: String)

/** The host owns the editor and interprets actions; the toolbar only shows the active tool. */
@Composable
fun SketchToolbar(selected: ToolType, tools: List<SketchToolItem>, backLabel: String, settingsLabel: String,
    onBack: () -> Unit, onSettings: () -> Unit, onTool: (ToolType) -> Unit,
    backIcon: @Composable () -> Unit, settingsIcon: @Composable () -> Unit, toolIcon: @Composable (ToolType) -> Unit) {
    Surface {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = backLabel }) { backIcon() }
            SketchToolButtons(selected, tools, onTool, toolIcon)
            IconButton(onClick = onSettings, modifier = Modifier.semantics { contentDescription = settingsLabel }) { settingsIcon() }
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
