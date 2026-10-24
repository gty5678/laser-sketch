package com.example.boschlaser.shared.model

import com.example.boschlaser.shared.annotation.*
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.measurement.Measurement
import com.example.boschlaser.shared.measurement.MeasurementRecord
import kotlin.test.*

class ProjectTest {
    private val manifest = ProjectManifest("project", "Test project", 1)
    @Test fun manifestRejectsUnknownFormatVersionAndUnits() {
        assertFailsWith<IllegalArgumentException> { manifest.copy(formatVersion = 2) }
        assertFailsWith<IllegalArgumentException> { manifest.copy(unit = "m") }
        assertFailsWith<IllegalArgumentException> { manifest.copy(format = "Other") }
    }
    @Test fun assetsRejectTraversalAbsolutePathsAndDuplicatePaths() {
        for (path in listOf("assets/../outside.jpg", "assets/./photo.jpg", "assets//photo.jpg", "/photo.jpg", "assets/drive:photo.jpg"))
            assertFailsWith<IllegalArgumentException> { ProjectAsset("photo", path, "image/jpeg") }
        val first = ProjectAsset("first", "assets/photo.jpg", "image/jpeg")
        val second = first.copy(id = "second")
        assertFailsWith<IllegalArgumentException> { Project(manifest, assets = mapOf(first.id to first, second.id to second)) }
    }
    @Test fun annotationsReferenceTheirOwnImageAsset() {
        val asset = ProjectAsset("photo", "assets/photo.jpg", "image/jpeg", 400, 300)
        val document = AnnotationDocument(photoAssetId = asset.id)
        assertEquals(document, Project(manifest, annotations = mapOf(asset.id to document), assets = mapOf(asset.id to asset)).annotations[asset.id])
        assertFailsWith<IllegalArgumentException> { Project(manifest, annotations = mapOf(asset.id to document)) }
        assertFailsWith<IllegalArgumentException> { Project(manifest, annotations = mapOf(asset.id to document.copy(photoAssetId = "other")), assets = mapOf(asset.id to asset)) }
    }
    @Test fun bindingRequiresExistingMeasurementAndCompatibleTargetType() {
        val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(1000.0, 0.0))
        val record = MeasurementRecord(Measurement("measurement", 1000.0, 1))
        val binding = MeasurementBinding(record.measurement.id, wall.id, MeasurementProperty.WALL_LENGTH)
        val project = Project(manifest, SketchDocument(walls = mapOf(wall.id to wall)), listOf(record), bindings = listOf(binding))
        assertEquals(binding, project.bindings.single())
        assertFailsWith<IllegalArgumentException> { project.copy(bindings = listOf(binding.copy(measurementId = "missing"))) }
        assertFailsWith<IllegalArgumentException> { project.copy(bindings = listOf(binding.copy(property = MeasurementProperty.COLUMN_DEPTH))) }
        assertFailsWith<IllegalArgumentException> { project.copy(measurements = listOf(record, record)) }
    }
}
