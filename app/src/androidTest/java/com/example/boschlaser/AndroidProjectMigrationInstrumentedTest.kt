package com.example.boschlaser

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.boschlaser.platform.*
import com.example.boschlaser.shared.storage.ProjectService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AndroidProjectMigrationInstrumentedTest {
    @Test fun realEncodedPhotoMigratesOnAndroidAndAtomicUpdatesPreserveItsBytes() = runBlocking {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.canonicalFile
        val root = File(cache, "project-migration-test-${UUID.randomUUID()}").apply { check(mkdir()) }
        try {
            val id = UUID.randomUUID().toString()
            val source = File(root, "annotation-projects/$id").apply { check(mkdirs()) }
            val bitmap = Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888)
            val photo = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 96, it) }.toByteArray()
            bitmap.recycle()
            File(source, "photo.jpg").writeBytes(photo)
            val metadata = """{"version":1,"id":"$id","name":"Fixture","updatedAt":1,"texts":[{"x":0.3,"y":0.6,"text":"Test"}]}"""
            File(source, "project.json").writeText(metadata, Charsets.UTF_8)
            val repository = AndroidProjectRepository(File(root, "projects"))
            val migration = AndroidLegacyProjectMigration(root, repository)
            assertEquals(LegacyMigrationStatus.MIGRATED, migration.migrate().single().status)
            val project = repository.load("annotation-$id")!!
            val service = ProjectService(repository, { 2L }, { UUID.randomUUID().toString() })
            service.refresh()
            service.rename(project.manifest.projectId, "Updated")
            assertEquals("Updated", service.projects.value.single().info.name)
            val output = ByteArrayOutputStream()
            AndroidProjectRepository(File(root, "projects")).export(project.manifest.projectId, output)
            val exported = AndroidProjectArchive().read(ByteArrayInputStream(output.toByteArray()))
            assertEquals("Updated", exported.project.manifest.name)
            assertArrayEquals(photo, exported.assets.getValue("photo"))
            assertEquals(metadata, File(source, "project.json").readText(Charsets.UTF_8))
            assertArrayEquals(photo, File(source, "photo.jpg").readBytes())
            assertEquals(LegacyMigrationStatus.ALREADY_PRESENT, migration.migrate().single().status)
            repository.delete(project.manifest.projectId)
            val reopened = AndroidProjectRepository(File(root, "projects"))
            assertEquals(LegacyMigrationStatus.ALREADY_PRESENT, AndroidLegacyProjectMigration(root, reopened).migrate().single().status)
            assertNull(reopened.load(project.manifest.projectId))
            assertTrue(File(source, "project.json").isFile)
        } finally {
            check(root.canonicalFile.parentFile == cache)
            root.deleteRecursively()
        }
    }
}
