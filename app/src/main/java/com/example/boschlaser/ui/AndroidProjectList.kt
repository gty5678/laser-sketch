package com.example.boschlaser.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.boschlaser.ProjectServices
import kotlinx.coroutines.CancellationException

/** Lifecycle, bitmap ownership and Android thumbnail decoding stay in the Android host. */
@Composable
internal fun AndroidProjectList(
    services: ProjectServices,
    sketch: Boolean,
    labels: ProjectLabels,
    updatedLabel: (Long) -> String,
    onOpen: (String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
    onExport: (String) -> Unit,
) {
    val catalog by services.projects.collectAsStateWithLifecycle()
    val projects = catalog.filter { if (sketch) it.hasSketch else it.photoAssetIds.isNotEmpty() }
    ProjectScreen(projects.map { ProjectCardState(it.info.id, it.info.name, updatedLabel(it.info.updatedAt)) },
        labels, onOpen, onRename, onDelete, onExport) { id ->
        val project = projects.first { it.info.id == id }
        ProjectThumbnail(services, id, project.info.updatedAt, if (sketch) null else project.photoAssetIds.firstOrNull(), project.info.name)
    }
}

@Composable
private fun ProjectThumbnail(services: ProjectServices, id: String, updatedAt: Long, assetId: String?, description: String) {
    val targetSize = with(LocalDensity.current) { 180.dp.roundToPx() }
    var bitmap by remember(id, updatedAt, assetId, targetSize) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(id, updatedAt, assetId, targetSize) {
        try { bitmap = services.thumbnail(id, targetSize, assetId) }
        catch (failure: CancellationException) { throw failure }
        catch (_: Exception) { /* The catalog error subscription handles unreadable projects. */ }
    }
    DisposableEffect(id, updatedAt, assetId, targetSize) {
        onDispose { bitmap?.recycle() }
    }
    bitmap?.let { Image(it.asImageBitmap(), contentDescription = description, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
}
