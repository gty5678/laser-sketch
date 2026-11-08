@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.example.boschlaser.ui

import com.example.boschlaser.shared.annotation.*
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.measurement.*
import com.example.boschlaser.shared.model.*
import com.example.boschlaser.shared.storage.ProjectService
import kotlinx.coroutines.runBlocking
import platform.Foundation.*
import kotlin.test.*

class IosProjectRepositoryTest {
    private val root = NSTemporaryDirectory() + NSUUID().UUIDString
    @AfterTest fun cleanup() { NSFileManager.defaultManager.removeItemAtPath(root, null) }
    @Test fun deletingBoundGeometryPreservesPhotoAndMeasurementsThroughSharedService() = runBlocking {
        val store = IosProjectStore(root)
        val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(2000.0, 0.0))
        val measurement = MeasurementRecord(Measurement("measurement", 2000.0, 1))
        val asset = ProjectAsset("photo", "assets/photo.jpg", "image/jpeg")
        val annotations = mapOf("photo" to AnnotationDocument(photoAssetId = "photo"))
        val project = Project(ProjectManifest("project", "Partitions", 1), SketchDocument(walls = mapOf(wall.id to wall)),
            measurements = listOf(measurement), annotations = annotations, assets = mapOf("photo" to asset),
            bindings = listOf(MeasurementBinding("measurement", "wall", MeasurementProperty.WALL_LENGTH)))
        store.putAsset("project", asset.path, byteArrayOf(1, 2)); store.save(project)
        val service = ProjectService(IosProjectRepository(store), { 2L }, { "new" })
        val result = service.saveSketch("project", SketchDocument())
        assertTrue(result.bindings.isEmpty())
        assertEquals(listOf(measurement), result.measurements)
        assertEquals(annotations, result.annotations)
        assertEquals(result, store.load("project"))
        assertContentEquals(byteArrayOf(1, 2), store.asset(result, "photo"))
        val mark = TextAnnotation("text", PhotoPoint(.25, .5), "Label", -1)
        val saved = service.saveAnnotation("project", "photo", AnnotationDocument(mapOf(mark.id to mark), "photo"))
        assertEquals(SketchDocument(), saved.sketch)
        assertEquals(listOf(measurement), saved.measurements)
        assertEquals(saved, store.load("project"))
    }
}
