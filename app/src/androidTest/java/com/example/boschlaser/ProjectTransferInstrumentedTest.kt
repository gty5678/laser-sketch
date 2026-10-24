package com.example.boschlaser

import android.graphics.Bitmap
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.boschlaser.platform.AndroidPhotoCodec
import com.example.boschlaser.platform.AndroidProjectArchive
import com.example.boschlaser.shared.annotation.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ProjectTransferInstrumentedTest {
    @Test fun providerTransferCreatesIndependentCopiesAndRejectsInvalidArchiveAtomically() = runBlocking {
        withTimeout(15000) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val services = ProjectServices.get(context)
            val directory = File(context.cacheDir, "shared").apply { check(isDirectory || mkdirs()) }
            val output = File(directory, "transfer-fixture-${UUID.randomUUID()}.lsk")
            val invalid = File(directory, "invalid-fixture-${UUID.randomUUID()}.lsk").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val bitmap = Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888)
            val jpeg = AndroidPhotoCodec.jpeg(bitmap)
            bitmap.recycle()
            val source = services.createAnnotation("Transfer fixture", jpeg, 4, 3).await()
            val asset = source.annotations.keys.single()
            val document = AnnotationDocument(mapOf("text" to TextAnnotation("text", PhotoPoint(.123456789, .5), "Preserved", -1)), asset)
            val copies = mutableListOf<String>()
            try {
                services.saveAnnotation(source.manifest.projectId, asset, document, null).await()
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", output)
                services.exportProject(source.manifest.projectId, uri).await()
                val archive = output.inputStream().use { AndroidProjectArchive().read(it) }
                assertEquals(document, archive.project.annotations.getValue(asset))
                assertArrayEquals(jpeg, archive.assets.getValue(asset))
                repeat(2) {
                    val imported = services.importProject(uri).await()
                    copies += imported.manifest.projectId
                    assertNotEquals(source.manifest.projectId, imported.manifest.projectId)
                    assertEquals(document, imported.annotations.getValue(asset))
                    assertArrayEquals(jpeg, services.loadAnnotation(imported.manifest.projectId, asset).await()!!.bytes)
                }
                assertEquals(2, copies.distinct().size)
                val catalog = services.projects.value
                try {
                    services.importProject(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", invalid)).await()
                    fail("Invalid archive must be rejected")
                } catch (_: com.example.boschlaser.platform.ProjectArchiveException) { }
                assertEquals(catalog, services.projects.value)
                assertEquals(document, services.loadAnnotation(source.manifest.projectId, asset).await()!!.project.annotations.getValue(asset))
            } finally {
                services.acknowledgeError()
                for (id in copies + source.manifest.projectId) services.delete(id).await()
                output.delete()
                invalid.delete()
            }
        }
    }
}
