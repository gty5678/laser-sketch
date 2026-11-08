@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.io.encoding.ExperimentalEncodingApi::class)

package com.example.boschlaser.ui

import com.example.boschlaser.shared.annotation.*
import com.example.boschlaser.shared.model.*
import platform.Foundation.*
import kotlin.io.encoding.Base64
import kotlin.test.*

class IosArchiveTest {
    private val root = NSTemporaryDirectory() + NSUUID().UUIDString
    @AfterTest fun clean() { NSFileManager.defaultManager.removeItemAtPath(root, null) }

    @Test fun readsDeflatedUtf8ZipWithDataDescriptors() {
        // Synthetic fixture written by production AndroidProjectArchive (DEFLATE + descriptors).
        val archive = IosProjectArchive.read(Base64.decode("UEsDBBQACAgIAAAAIQAAAAAAAAAAAAAAAAANAAkAbWFuaWZlc3QuanNvblVUBQABAAAAAKtWSssvyk0sUbJS8kksTi0Kzk4tSc5Q0oEKh6UWFWfm5ylZGeooFRTlZ6Uml3imANVWZRakZVaUlBalApXmJeamAsVebF/xdOfmp/0bni3Y/mzOGqBEaUFKYklqimMJWH9pXibImtxcoExicXFqSbGSVXRsLQBQSwcIl1gmk3YAAACCAAAAUEsDBBQACAgIAAAAIQAAAAAAAAAAAAAAAAALAAkAc2tldGNoLmpzb25VVAUAAQAAAACrVkrLL8pNLAlLLSrOzM9TsjLUUSpPzMkpVrKKjtVRyi9IzcvMS4fykvNzSnPzoJykzLwUmFQtAFBLBwhMNys8OAAAAEcAAABQSwMEFAAICAgAAAAhAAAAAAAAAAAAAAAAABEACQBtZWFzdXJlbWVudHMuanNvblVUBQABAAAAAIuOBQBQSwcIKbtMDQQAAAACAAAAUEsDBBQACAgIAAAAIQAAAAAAAAAAAAAAAAAQAAkAYW5ub3RhdGlvbnMuanNvblVUBQABAAAAAIuOBQBQSwcIKbtMDQQAAAACAAAAUEsBAhQAFAAICAgAAAAhAJdYJpN2AAAAggAAAA0ACQAAAAAAAAAAAAAAAAAAAG1hbmlmZXN0Lmpzb25VVAUAAQAAAABQSwECFAAUAAgICAAAACEATDcrPDgAAABHAAAACwAJAAAAAAAAAAAAAAC6AAAAc2tldGNoLmpzb25VVAUAAQAAAABQSwECFAAUAAgICAAAACEAKbtMDQQAAAACAAAAEQAJAAAAAAAAAAAAAAA0AQAAbWVhc3VyZW1lbnRzLmpzb25VVAUAAQAAAABQSwECFAAUAAgICAAAACEAKbtMDQQAAAACAAAAEAAJAAAAAAAAAAAAAACAAQAAYW5ub3RhdGlvbnMuanNvblVUBQABAAAAAFBLBQYAAAAABAAEABUBAADLAQAAAAA="))
        assertEquals("跨平台样本", archive.project.manifest.name)
        assertEquals("zipfixture", archive.project.manifest.projectId)
    }

    @Test fun photosAnnotationsAndOtherPartitionsSurviveExportAndImportAsIndependentCopies() {
        val store = IosProjectStore(root)
        val asset = ProjectAsset("photo", "assets/photo.jpg", "image/jpeg")
        val mark = TextAnnotation("text", PhotoPoint(.25, .75), "测量", 0xff1976d2.toInt())
        val project = Project(ProjectManifest("original", "Original", 1),
            annotations = mapOf("photo" to AnnotationDocument(mapOf(mark.id to mark), "photo")), assets = mapOf("photo" to asset))
        val bytes = byteArrayOf(1, 2, 3, 4)
        store.putAsset("original", asset.path, bytes); store.save(project)
        val exported = store.export(project)
        (NSProcessInfo.processInfo.environment["LSK_EXCHANGE_OUTPUT"] as? String)?.let {
            check(exported.nsData().writeToFile(it, true))
        }
        val parsed = IosProjectArchive.read(exported)
        assertEquals(project, parsed.project)
        assertContentEquals(bytes, parsed.assets.getValue("photo"))
        val imported = store.import(exported)
        assertNotEquals(project.manifest.projectId, imported.manifest.projectId)
        assertEquals(project.annotations, imported.annotations)
        assertContentEquals(bytes, store.asset(imported, "photo"))
        store.delete(imported.manifest.projectId)
        assertEquals(project, store.load("original"))
        assertEquals(listOf(project), store.list())
    }

    @Test fun pickedFileUrlReadsActualFoundationFileAttributesBeforeImport() {
        check(NSFileManager.defaultManager.createDirectoryAtPath(root, true, null, null))
        val url = NSURL.fileURLWithPath("$root/input.lsk")
        val project = Project(ProjectManifest("picked", "Picked file", 1))
        val bytes = IosProjectArchive.write(project, emptyMap())
        check(bytes.nsData().writeToURL(url, true))
        assertEquals(project, IosProjectArchive.read(readProjectUrl(url)).project)
        val empty = NSURL.fileURLWithPath("$root/empty.lsk")
        check(byteArrayOf().nsData().writeToURL(empty, true))
        assertFails { readProjectUrl(empty) }
    }

    @Test fun rejectsCorruptionTraversalDuplicatesAndTruncatedContainers() {
        val project = Project(ProjectManifest("project", "Safe", 1))
        val valid = IosProjectArchive.write(project, emptyMap())
        assertFails { IosProjectArchive.read(valid.copyOf(valid.size - 1)) }
        val corrupt = valid.copyOf(); corrupt[100] = (corrupt[100].toInt() xor 1).toByte()
        assertFails { IosProjectArchive.read(corrupt) }
        val traversal = valid.copyOf()
        val target = "manifest.json".encodeToByteArray()
        val replacement = "../unsafe.txt".encodeToByteArray()
        for (at in 0..traversal.size - target.size) {
            if (traversal.copyOfRange(at, at + target.size).contentEquals(target)) replacement.copyInto(traversal, at)
        }
        assertFails { IosProjectArchive.read(traversal) }
        // Failed imports must leave the existing directory unchanged.
        val store = IosProjectStore(root); store.save(project)
        assertFails { store.import(corrupt) }
        assertEquals(listOf(project), store.list())
    }
}
