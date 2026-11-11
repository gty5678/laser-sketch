package com.example.boschlaser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.boschlaser.shared.annotation.AnnotationMode

data class AnnotationToolItem(val mode: AnnotationMode, val label: String)

/** Photo access and sharing remain host actions; tool state follows the shared identity. */
@Composable
fun AnnotationToolbar(
    selected: AnnotationMode,
    tools: List<AnnotationToolItem>,
    backLabel: String,
    shareLabel: String,
    saveLabel: String,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onTool: (AnnotationMode) -> Unit,
    backIcon: @Composable () -> Unit,
    shareIcon: @Composable () -> Unit,
    saveIcon: @Composable () -> Unit,
    toolIcon: @Composable (AnnotationMode) -> Unit,
    exportLabel: String,
) {
    var exportMenuExpanded by remember { mutableStateOf(false) }
    Surface {
        Row(Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = backLabel }, content = backIcon)
            ScrollableTools(Modifier.weight(1f)) {
                tools.forEach { tool ->
                    IconToggleButton(checked = selected == tool.mode, onCheckedChange = { onTool(tool.mode) },
                        modifier = Modifier.semantics { contentDescription = tool.label },
                        colors = IconButtonDefaults.iconToggleButtonColors(
                            checkedContainerColor = MaterialTheme.colorScheme.primary,
                            checkedContentColor = MaterialTheme.colorScheme.onPrimary)) { toolIcon(tool.mode) }
                }
            }
            Box {
                IconButton(onClick = { exportMenuExpanded = true },
                    modifier = Modifier.semantics { contentDescription = exportLabel }, content = shareIcon)
                DropdownMenu(expanded = exportMenuExpanded, onDismissRequest = { exportMenuExpanded = false }) {
                    DropdownMenuItem(text = { Text(shareLabel) }, leadingIcon = shareIcon, onClick = {
                        exportMenuExpanded = false
                        onShare()
                    })
                    DropdownMenuItem(text = { Text(saveLabel) }, leadingIcon = saveIcon, onClick = {
                        exportMenuExpanded = false
                        onSave()
                    })
                }
            }
        }
    }
}
