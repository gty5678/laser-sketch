package com.example.boschlaser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Text is localized by the host; this screen never owns storage, platform images or an editor. */
data class ProjectCardState(val id: String, val name: String, val updatedLabel: String)
data class ProjectLabels(val empty: String, val actions: String, val rename: String, val delete: String, val export: String)

@Composable
fun ProjectScreen(
    projects: List<ProjectCardState>,
    labels: ProjectLabels,
    onOpen: (String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
    onExport: (String) -> Unit,
    thumbnail: @Composable (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (projects.isEmpty()) Text(labels.empty, style = MaterialTheme.typography.bodySmall)
        projects.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { project ->
                    key(project.id) {
                        ProjectCard(project, labels, Modifier.weight(1f), onOpen, onRename, onDelete, onExport, thumbnail)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ProjectCard(
    project: ProjectCardState,
    labels: ProjectLabels,
    modifier: Modifier,
    onOpen: (String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
    onExport: (String) -> Unit,
    thumbnail: @Composable (String) -> Unit,
) {
    var menuOpen by remember(project.id) { mutableStateOf(false) }
    Card(modifier.clickable { onOpen(project.id) }) {
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f)) { thumbnail(project.id) }
        Column(Modifier.padding(horizontal = 8.dp)) {
            Text(project.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text(project.updatedLabel, maxLines = 1, style = MaterialTheme.typography.bodySmall)
            Box {
                TextButton(onClick = { menuOpen = true }) { Text(labels.actions) }
                DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(labels.rename) }, onClick = { menuOpen = false; onRename(project.id) })
                    DropdownMenuItem(text = { Text(labels.delete) }, onClick = { menuOpen = false; onDelete(project.id) })
                    DropdownMenuItem(text = { Text(labels.export) }, onClick = { menuOpen = false; onExport(project.id) })
                }
            }
        }
    }
}
