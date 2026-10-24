package com.example.boschlaser

import com.example.boschlaser.platform.*
import com.example.boschlaser.shared.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class AndroidProjectRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val asset = ProjectAsset("photo", "assets/photo.jpg", "image/jpeg")
    private val project = Project(ProjectManifest("../logical/id", "Fixture", 1), assets = mapOf(asset.id to asset))
    private val bytes = byteArrayOf(1, 2, -1)
    @Test fun saveReopenRenameExportDeletePreservesImageAndSafeLogicalId() = runBlocking {
        val root = temporary.newFolder()
        val repository = AndroidProjectRepository(root)
        repository.save(project, mapOf(asset.id to bytes))
        assertEquals(project, AndroidProjectRepository(root).load(project.manifest.projectId))
        assertEquals(1, root.listFiles()!!.size)
        assertTrue(root.listFiles()!!.single().name.matches(Regex("[a-f0-9]{64}\\.lsk")))
        val updated = project.copy(manifest = project.manifest.copy(name = "Renamed", updatedAt = 2))
        repository.save(updated)
        assertEquals("Renamed", repository.list().single().name)
        val output = ByteArrayOutputStream()
        repository.export(project.manifest.projectId, output)
        assertArrayEquals(bytes, AndroidProjectArchive().read(ByteArrayInputStream(output.toByteArray())).assets.getValue(asset.id))
        repository.delete(project.manifest.projectId)
        assertNull(repository.load(project.manifest.projectId))
        assertTrue(repository.list().isEmpty())
    }
    @Test fun invalidReplacementPreservesLastCompleteProjectAndRemovesTemporaryFiles() = runBlocking {
        val root = temporary.newFolder()
        val repository = AndroidProjectRepository(root)
        repository.save(project, mapOf(asset.id to bytes))
        try { repository.save(project.copy(manifest = project.manifest.copy(name = "Failed")), emptyMap()); fail("Missing image") }
        catch (failure: ProjectArchiveException) { assertEquals(ProjectArchiveFailure.MISSING_ASSET, failure.reason) }
        assertEquals(project, repository.load(project.manifest.projectId))
        assertEquals(1, root.listFiles()!!.size)
    }
    @Test fun importAndConcurrentEditsKeepSeparateProjectsAndStableOrdering() = runBlocking {
        val repository = AndroidProjectRepository(temporary.newFolder())
        val output = ByteArrayOutputStream()
        AndroidProjectArchive().write(project, mapOf(asset.id to bytes), output)
        assertEquals(project, repository.import(ByteArrayInputStream(output.toByteArray())))
        coroutineScope {
            (1..10).map { index -> async {
                repository.save(Project(ProjectManifest("p-$index", "Project $index", index.toLong())))
            } }.forEach { it.await() }
        }
        assertEquals("p-10", repository.list().first().id)
        assertEquals(11, repository.list().size)
        assertEquals(project, repository.load(project.manifest.projectId))
    }
    @Test fun importingAnExistingIdCannotReplaceLocalEdits() = runBlocking {
        val root = temporary.newFolder()
        val repository = AndroidProjectRepository(root)
        val local = project.copy(manifest = project.manifest.copy(name = "Local edit"))
        repository.save(local, mapOf(asset.id to bytes))
        val incoming = ByteArrayOutputStream().also { AndroidProjectArchive().write(project, mapOf(asset.id to bytes), it) }.toByteArray()
        try { repository.import(ByteArrayInputStream(incoming)); fail("Existing ID must be rejected") }
        catch (_: IllegalStateException) { }
        assertEquals(local, repository.load(local.manifest.projectId))
        assertArrayEquals(bytes, repository.loadAsset(local.manifest.projectId, asset.id))
        assertEquals(1, root.listFiles()!!.size)
    }
    @Test fun importingACopyChangesOnlyManifestAndKeepsEveryScopedReference() = runBlocking {
        val repository = AndroidProjectRepository(temporary.newFolder())
        val wall = Wall("wall", com.example.boschlaser.shared.geometry.Vec2(0.123456789, 0.0), com.example.boschlaser.shared.geometry.Vec2(1000.123456789, 0.0), 200.0)
        val measurement = com.example.boschlaser.shared.measurement.MeasurementRecord(
            com.example.boschlaser.shared.measurement.Measurement("measurement", 1000.123456789, 1), byteArrayOf(1, 2))
        val original = project.copy(sketch = SketchDocument(walls = mapOf(wall.id to wall)),
            measurements = listOf(measurement), bindings = listOf(MeasurementBinding("measurement", wall.id, MeasurementProperty.WALL_LENGTH)),
            annotations = mapOf(asset.id to com.example.boschlaser.shared.annotation.AnnotationDocument(
                mapOf("text" to com.example.boschlaser.shared.annotation.TextAnnotation("text", com.example.boschlaser.shared.annotation.PhotoPoint(.123456789, .5), "Fixture", -1)), asset.id)))
        repository.save(original, mapOf(asset.id to bytes))
        val incoming = ByteArrayOutputStream().also { repository.export(original.manifest.projectId, it) }.toByteArray()
        val copy = repository.import(ByteArrayInputStream(incoming), "new-id", 25)
        assertEquals(original.copy(manifest = original.manifest.copy(projectId = "new-id", updatedAt = 25)), copy)
        assertEquals(copy, repository.load("new-id"))
        assertEquals(original, repository.load(original.manifest.projectId))
        assertArrayEquals(bytes, repository.loadAsset("new-id", asset.id))
        assertEquals(2, repository.list().size)
    }

}
