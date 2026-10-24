package com.example.boschlaser.shared.model

import com.example.boschlaser.shared.annotation.AnnotationDocument
import com.example.boschlaser.shared.measurement.MeasurementRecord

/** Exchange metadata has its own version; coordinates remain semantic, never rendered polygons. */
data class ProjectManifest(val projectId: String, val name: String, val updatedAt: Long,
    val format: String = "LaserSketch", val formatVersion: Int = 1, val unit: String = "mm") {
    init {
        require(projectId.isNotBlank() && name.isNotBlank())
        require(format == "LaserSketch" && formatVersion == 1 && unit == "mm")
    }
}
data class ProjectAsset(val id: String, val path: String, val mimeType: String,
    val pixelWidth: Int? = null, val pixelHeight: Int? = null) {
    init {
        require(id.isNotBlank() && mimeType.isNotBlank())
        require(path.startsWith("assets/") && !path.contains('\\') && !path.contains(':') && !path.contains('\u0000'))
        require(path.split('/').all { it.isNotBlank() && it != "." && it != ".." })
        require(pixelWidth == null || pixelWidth > 0)
        require(pixelHeight == null || pixelHeight > 0)
    }
}
enum class MeasurementProperty { WALL_LENGTH, WALL_THICKNESS, OPENING_WIDTH, COLUMN_WIDTH, COLUMN_DEPTH, COLUMN_DIAMETER }
data class MeasurementBinding(val measurementId: String, val targetEntityId: String, val property: MeasurementProperty) {
    init { require(measurementId.isNotBlank() && targetEntityId.isNotBlank()) }
}
data class Project(val manifest: ProjectManifest, val sketch: SketchDocument = SketchDocument(),
    val measurements: List<MeasurementRecord> = emptyList(),
    val annotations: Map<String, AnnotationDocument> = emptyMap(),
    val assets: Map<String, ProjectAsset> = emptyMap(),
    val bindings: List<MeasurementBinding> = emptyList()) {
    init {
        require(assets.all { (id, asset) -> id == asset.id })
        require(assets.values.map { it.path }.distinct().size == assets.size)
        require(annotations.all { (assetId, document) -> document.photoAssetId == assetId &&
            assets[assetId]?.mimeType?.startsWith("image/") == true })
        val measurementIds = measurements.map { it.measurement.id }
        require(measurementIds.distinct().size == measurementIds.size)
        require(bindings.all { binding -> binding.measurementId in measurementIds && when (binding.property) {
            MeasurementProperty.WALL_LENGTH, MeasurementProperty.WALL_THICKNESS -> binding.targetEntityId in sketch.walls
            MeasurementProperty.OPENING_WIDTH -> binding.targetEntityId in sketch.openings
            MeasurementProperty.COLUMN_WIDTH, MeasurementProperty.COLUMN_DEPTH -> sketch.columns[binding.targetEntityId] is RectColumn
            MeasurementProperty.COLUMN_DIAMETER -> sketch.columns[binding.targetEntityId] is CircularColumn
        } })
    }
}
data class ProjectInfo(val id: String, val name: String, val updatedAt: Long)
