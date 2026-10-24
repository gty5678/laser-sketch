package com.example.boschlaser.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
) {
    Surface {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = backLabel }, content = backIcon)
            tools.forEach { tool ->
                IconToggleButton(checked = selected == tool.mode, onCheckedChange = { onTool(tool.mode) },
                    modifier = Modifier.semantics { contentDescription = tool.label },
                    colors = IconButtonDefaults.iconToggleButtonColors(
                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimary)) { toolIcon(tool.mode) }
            }
            IconButton(onClick = onShare, modifier = Modifier.semantics { contentDescription = shareLabel }, content = shareIcon)
            IconButton(onClick = onSave, modifier = Modifier.semantics { contentDescription = saveLabel }, content = saveIcon)
        }
    }
}
