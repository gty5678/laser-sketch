package com.example.boschlaser.shared.storage

import com.example.boschlaser.shared.annotation.*
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.measurement.Measurement
import com.example.boschlaser.shared.measurement.MeasurementRecord
import com.example.boschlaser.shared.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.*

class ProjectServiceTest {
    private class Repository : ProjectRepository {
        val contents = linkedMapOf<String, Project>()
        var failSave = false
        var failDelete = false
        var writes = 0
        override suspend fun save(project: Project) {
            yield()
            check(!failSave)
            contents[project.manifest.projectId] = project
            writes++
        }
        override suspend fun load(id: String): Project? { yield(); return contents[id] }
        override suspend fun list() = contents.values.map { it.manifest.let { item -> ProjectInfo(item.projectId, item.name, item.updatedAt) } }
        override suspend fun delete(id: String) { check(!failDelete); contents.remove(id) }
    }
    private val wall = Wall("wall", Vec2(0.0, 0.0), Vec2(2000.0, 0.0))
    private val asset = ProjectAsset("photo", "assets/photo.jpg", "image/jpeg")
    private fun fixture() = Project(ProjectManifest("p", "Fixture", 1), SketchDocument(walls = mapOf(wall.id to wall)),
        listOf(MeasurementRecord(Measurement("record", 2000.0, 1))),
        mapOf(asset.id to AnnotationDocument(photoAssetId = asset.id)), mapOf(asset.id to asset),
        listOf(MeasurementBinding("record", wall.id, MeasurementProperty.WALL_LENGTH)))
    @Test fun concurrentSectionsAndRenameKeepOneCompleteProject() = runTest {
        val repository = Repository().apply { contents["p"] = fixture() }
        val service = ProjectService(repository, { 10 }, { "new" })
        val document = AnnotationDocument(mapOf("text" to TextAnnotation("text", PhotoPoint(0.2, 0.3), "Label", -1)), asset.id)
        coroutineScope {
            val sketch = async { service.saveSketch("p", SketchDocument(walls = mapOf(wall.id to wall.copy(thickness = 300.0)))) }
            val annotation = async { service.saveAnnotation("p", asset.id, document) }
            val rename = async { service.rename("p", " Renamed ") }
            sketch.await(); annotation.await(); rename.await()
        }
        val project = service.load("p")!!
        assertEquals(300.0, project.sketch.walls.getValue(wall.id).thickness)
        assertEquals(document, project.annotations.getValue(asset.id))
        assertEquals("Renamed", project.manifest.name)
        assertEquals(fixture().measurements, project.measurements)
        assertEquals(fixture().assets, project.assets)
        assertEquals(fixture().bindings, project.bindings)
        assertEquals(10, service.projects.value.single().info.updatedAt)
    }
    @Test fun deletingGeometryRemovesOnlyItsInvalidMeasurementBindings() = runTest {
        val repository = Repository().apply { contents["p"] = fixture() }
        val service = ProjectService(repository, { 2 }, { "new" })
        val project = service.saveSketch("p", SketchDocument())
        assertTrue(project.bindings.isEmpty())
        assertEquals(fixture().measurements, project.measurements)
        assertEquals(fixture().annotations, project.annotations)
    }
    @Test fun failedPersistenceAndNoOpEditsDoNotPublishNewState() = runTest {
        val repository = Repository().apply { contents["p"] = fixture() }
        val service = ProjectService(repository, { 2 }, { "new" })
        service.refresh()
        val original = service.projects.value
        service.rename("p", "Fixture")
        service.saveSketch("p", fixture().sketch)
        assertEquals(0, repository.writes)
        repository.failSave = true
        assertFailsWith<IllegalStateException> { service.rename("p", "Failed") }
        assertEquals(original, service.projects.value)
        assertEquals(fixture(), service.load("p"))
        repository.failDelete = true
        assertFailsWith<IllegalStateException> { service.delete("p") }
        assertEquals(original, service.projects.value)
    }
    @Test fun createRefreshAndDeletePublishStableOrderedCatalog() = runTest {
        val repository = Repository().apply { contents["p"] = fixture() }
        val service = ProjectService(repository, { 3 }, { "new" })
        service.refresh()
        val created = service.createSketch(" New sketch ")
        assertEquals("New sketch", created.manifest.name)
        assertEquals(listOf("new", "p"), service.projects.value.map { it.info.id })
        assertTrue(service.projects.value.first().hasSketch)
        service.delete("new")
        assertNull(service.load("new"))
        assertEquals(listOf("p"), service.projects.value.map { it.info.id })
    }
    @Test fun invalidAnnotationHostAndMissingProjectFailWithoutMutation() = runTest {
        val repository = Repository().apply { contents["p"] = fixture() }
        val service = ProjectService(repository, { 2 }, { "new" })
        assertFailsWith<IllegalArgumentException> { service.saveAnnotation("p", "missing", AnnotationDocument(photoAssetId = "missing")) }
        assertFailsWith<NoSuchElementException> { service.rename("missing", "Name") }
        assertEquals(fixture(), service.load("p"))
        assertEquals(0, repository.writes)
    }
}
