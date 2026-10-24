package com.example.boschlaser

import com.example.boschlaser.platform.*
import com.example.boschlaser.shared.model.ProjectManifest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID

class AndroidLegacyProjectMigrationTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun source(root: File, kind: LegacyProjectKind, id: String, version: Int = 1): File = File(root, "${kind.directory}/$id").apply {
        check(mkdirs())
        File(this, "project.json").writeText("""{"version":$version,"id":"$id","name":"Original","updatedAt":7,"walls":[],"dimensions":[]}""", Charsets.UTF_8)
        if (kind == LegacyProjectKind.ANNOTATION) File(this, "photo.jpg").writeBytes(byteArrayOf(1, -1, 3))
        File(this, "preview.png").writeBytes(byteArrayOf(4, 5))
    }
    @Test fun bothKindsWithSameIdRemainSeparateAndAllOriginalBytesSurvive() = runBlocking {
        val root = temporary.newFolder()
        val id = UUID.randomUUID().toString()
        val sketch = source(root, LegacyProjectKind.SKETCH, id)
        val annotation = source(root, LegacyProjectKind.ANNOTATION, id)
        val originals = (sketch.listFiles()!!.toList() + annotation.listFiles()!!.toList()).associateWith { it.readBytes() }
        val repository = AndroidProjectRepository(File(root, "projects"))
        val result = AndroidLegacyProjectMigration(root, repository).migrate()
        assertEquals(2, result.size)
        assertTrue(result.all { it.status == LegacyMigrationStatus.MIGRATED })
        assertEquals(setOf("sketch-$id", "annotation-$id"), repository.list().map { it.id }.toSet())
        val output = ByteArrayOutputStream()
        repository.export("annotation-$id", output)
        assertArrayEquals(byteArrayOf(1, -1, 3), AndroidProjectArchive().read(ByteArrayInputStream(output.toByteArray())).assets.getValue("photo"))
        originals.forEach { (file, bytes) -> assertArrayEquals(bytes, file.readBytes()) }
    }
    @Test fun repeatMigrationCannotOverwriteLaterEdits() = runBlocking {
        val root = temporary.newFolder()
        val id = UUID.randomUUID().toString()
        source(root, LegacyProjectKind.SKETCH, id)
        val repository = AndroidProjectRepository(File(root, "projects"))
        val migration = AndroidLegacyProjectMigration(root, repository)
        migration.migrate()
        val project = repository.load("sketch-$id")!!
        repository.save(project.copy(manifest = ProjectManifest(project.manifest.projectId, "Edited", 10)))
        assertEquals(LegacyMigrationStatus.ALREADY_PRESENT, migration.migrate().single().status)
        assertEquals("Edited", repository.load("sketch-$id")!!.manifest.name)
    }
    @Test fun failedSourceIsRetainedAndOtherProjectsStillMigrate() = runBlocking {
        val root = temporary.newFolder()
        val bad = source(root, LegacyProjectKind.SKETCH, UUID.randomUUID().toString(), 2)
        source(root, LegacyProjectKind.SKETCH, UUID.randomUUID().toString())
        val repository = AndroidProjectRepository(File(root, "projects"))
        val result = AndroidLegacyProjectMigration(root, repository).migrate()
        assertEquals(LegacyMigrationFailure.UNSUPPORTED_VERSION, result.single { it.status == LegacyMigrationStatus.FAILED }.failure)
        assertEquals(1, repository.list().size)
        assertTrue(File(bad, "project.json").isFile)
    }
    @Test fun deletedMigratedProjectDoesNotReturnAfterRepositoryReopen() = runBlocking {
        val root = temporary.newFolder()
        val id = UUID.randomUUID().toString()
        val original = source(root, LegacyProjectKind.SKETCH, id)
        val directory = File(root, "projects")
        val repository = AndroidProjectRepository(directory)
        AndroidLegacyProjectMigration(root, repository).migrate()
        repository.delete("sketch-$id")
        val reopened = AndroidProjectRepository(directory)
        assertEquals(LegacyMigrationStatus.ALREADY_PRESENT, AndroidLegacyProjectMigration(root, reopened).migrate().single().status)
        assertNull(reopened.load("sketch-$id"))
        assertTrue(reopened.list().isEmpty())
        assertTrue(File(original, "project.json").isFile)
    }
    @Test fun missingPhotoIdentityMismatchAndInvalidTextLeaveNoPartialProject() = runBlocking {
        val root = temporary.newFolder()
        val noPhoto = source(root, LegacyProjectKind.ANNOTATION, UUID.randomUUID().toString())
        File(noPhoto, "photo.jpg").delete()
        val wrongId = source(root, LegacyProjectKind.SKETCH, UUID.randomUUID().toString())
        File(wrongId, "project.json").writeText("""{"version":1,"id":"other","name":"Mismatch"}""")
        val invalid = source(root, LegacyProjectKind.SKETCH, UUID.randomUUID().toString())
        File(invalid, "project.json").writeBytes(byteArrayOf(-61, 40))
        val repository = AndroidProjectRepository(File(root, "projects"))
        val result = AndroidLegacyProjectMigration(root, repository).migrate()
        assertEquals(3, result.size)
        assertTrue(result.all { it.status == LegacyMigrationStatus.FAILED })
        assertTrue(repository.list().isEmpty())
        assertTrue(File(noPhoto, "project.json").isFile)
    }
}
