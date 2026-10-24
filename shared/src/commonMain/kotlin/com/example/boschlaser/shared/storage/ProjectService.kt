package com.example.boschlaser.shared.storage

import com.example.boschlaser.shared.annotation.AnnotationDocument
import com.example.boschlaser.shared.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ProjectCatalogEntry(val info: ProjectInfo, val photoAssetIds: List<String>, val hasSketch: Boolean)

/** One application-owned instance serializes edits to different sections of the same project. */
class ProjectService(private val repository: ProjectRepository, private val epochTime: () -> Long,
    private val newId: () -> String) {
    private val mutex = Mutex()
    private val catalog = MutableStateFlow<List<ProjectCatalogEntry>>(emptyList())
    val projects: StateFlow<List<ProjectCatalogEntry>> = catalog.asStateFlow()

    suspend fun refresh() = mutex.withLock {
        val loaded = repository.list().mapNotNull { repository.load(it.id) }
        catalog.value = loaded.map(::entry).sortedWith(order)
    }
    suspend fun load(id: String): Project? = mutex.withLock { repository.load(id) }
    suspend fun createSketch(name: String): Project = mutex.withLock {
        val project = Project(ProjectManifest(newId(), name.trim(), epochTime()))
        check(repository.load(project.manifest.projectId) == null)
        repository.save(project)
        publish(project)
        project
    }
    suspend fun rename(id: String, name: String): Project = mutate(id) { project ->
        val value = name.trim()
        require(value.isNotBlank())
        project.copy(manifest = project.manifest.copy(name = value))
    }
    suspend fun saveSketch(id: String, document: SketchDocument): Project = mutate(id) { project ->
        project.copy(sketch = document, bindings = project.bindings.filter { binding -> when (binding.property) {
            MeasurementProperty.WALL_LENGTH, MeasurementProperty.WALL_THICKNESS -> binding.targetEntityId in document.walls
            MeasurementProperty.OPENING_WIDTH -> binding.targetEntityId in document.openings
            MeasurementProperty.COLUMN_WIDTH, MeasurementProperty.COLUMN_DEPTH -> document.columns[binding.targetEntityId] is RectColumn
            MeasurementProperty.COLUMN_DIAMETER -> document.columns[binding.targetEntityId] is CircularColumn
        } })
    }
    suspend fun saveAnnotation(id: String, assetId: String, document: AnnotationDocument): Project = mutate(id) { project ->
        require(document.photoAssetId == assetId && project.assets[assetId]?.mimeType?.startsWith("image/") == true)
        project.copy(annotations = project.annotations + (assetId to document))
    }
    suspend fun delete(id: String) = mutex.withLock {
        repository.delete(id)
        catalog.value = catalog.value.filter { it.info.id != id }
    }
    private suspend fun mutate(id: String, change: (Project) -> Project): Project = mutex.withLock {
        val before = repository.load(id) ?: throw NoSuchElementException()
        val changed = change(before)
        require(changed.manifest.projectId == id)
        if (before == changed) return@withLock before
        val project = changed.copy(manifest = changed.manifest.copy(updatedAt = epochTime()))
        repository.save(project)
        publish(project)
        project
    }
    private fun publish(project: Project) {
        catalog.value = (catalog.value.filter { it.info.id != project.manifest.projectId } + entry(project)).sortedWith(order)
    }
    private fun entry(project: Project): ProjectCatalogEntry {
        val manifest = project.manifest
        val sketch = project.sketch
        return ProjectCatalogEntry(ProjectInfo(manifest.projectId, manifest.name, manifest.updatedAt),
            project.annotations.keys.toList(), project.annotations.isEmpty() || sketch.walls.isNotEmpty() || sketch.columns.isNotEmpty() || sketch.openings.isNotEmpty())
    }
    private val order = compareByDescending<ProjectCatalogEntry> { it.info.updatedAt }.thenBy { it.info.id }
}
