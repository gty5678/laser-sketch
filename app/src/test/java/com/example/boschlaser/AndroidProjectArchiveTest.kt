package com.example.boschlaser

import com.example.boschlaser.platform.*
import com.example.boschlaser.shared.model.*
import com.example.boschlaser.shared.storage.ProjectSerializer
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AndroidProjectArchiveTest {
    private val asset = ProjectAsset("photo", "assets/photo.png", "image/png", 2, 2)
    private val project = Project(ProjectManifest("p", "Fixture", 1), assets = mapOf(asset.id to asset))
    private val bytes = byteArrayOf(0, 1, 2, -1)
    private fun entries() = ProjectSerializer.encode(project).mapValues { it.value.toByteArray(Charsets.UTF_8) } + (asset.path to bytes)
    private fun zip(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip -> entries.forEach { (path, bytes) -> zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry() } }
    }.toByteArray()
    private fun rejected(data: ByteArray, reason: ProjectArchiveFailure, codec: AndroidProjectArchive = AndroidProjectArchive()) {
        try { codec.read(ByteArrayInputStream(data)); fail("Expected archive rejection") }
        catch (failure: ProjectArchiveException) { assertEquals(reason, failure.reason) }
    }
    @Test fun archiveRoundTripPreservesAssetsAndCallerStreamOwnership() {
        var outputClosed = false
        val output = object : ByteArrayOutputStream() { override fun close() { outputClosed = true } }
        AndroidProjectArchive().write(project, mapOf(asset.id to bytes), output)
        assertFalse(outputClosed)
        var inputClosed = false
        val input = object : ByteArrayInputStream(output.toByteArray()) { override fun close() { inputClosed = true } }
        val restored = AndroidProjectArchive().read(input)
        assertFalse(inputClosed)
        assertEquals(project, restored.project)
        assertArrayEquals(bytes, restored.assets.getValue(asset.id))
        restored.assets.getValue(asset.id)[0] = 99
        assertEquals(0, restored.assets.getValue(asset.id)[0].toInt())
    }
    @Test fun missingAndUnlistedAssetsAreRejected() {
        rejected(zip(entries() - asset.path), ProjectArchiveFailure.MISSING_ASSET)
        rejected(zip(entries() + ("assets/other.png" to bytes)), ProjectArchiveFailure.UNKNOWN_ASSET)
        val output = ByteArrayOutputStream()
        try { AndroidProjectArchive().write(project, emptyMap(), output); fail("Missing asset") }
        catch (failure: ProjectArchiveException) { assertEquals(ProjectArchiveFailure.MISSING_ASSET, failure.reason) }
        assertEquals(0, output.size())
    }
    @Test fun traversalAbsoluteAndUnknownPathsCannotBeExtracted() {
        for (path in listOf("assets/../outside", "/absolute", "assets/a:b", "assets/a\\b", "assets//a", "unexpected.json", "assets/"))
            rejected(zip(entries() + (path to bytes)), ProjectArchiveFailure.INVALID_PATH)
    }
    @Test fun duplicateZipEntryNamesAreRejected() {
        val data = zip(entries() + ("assets/other.png" to bytes))
        // Both names have the same encoded size; patch only this artificial fixture's names.
        val patched = String(data, Charsets.ISO_8859_1).replace("assets/other.png", "assets/photo.png").toByteArray(Charsets.ISO_8859_1)
        rejected(patched, ProjectArchiveFailure.DUPLICATE_ENTRY)
    }
    @Test fun metadataAssetsTotalSizeAndEntryCountAreBoundedOnReadAndWrite() {
        val archive = zip(entries())
        for (limits in listOf(ProjectArchiveLimits(metadataBytes = 10), ProjectArchiveLimits(assetBytes = 3), ProjectArchiveLimits(totalBytes = 20), ProjectArchiveLimits(entryCount = 4))) {
            val codec = AndroidProjectArchive(limits)
            rejected(archive, ProjectArchiveFailure.SIZE_LIMIT, codec)
            val output = ByteArrayOutputStream()
            try { codec.write(project, mapOf(asset.id to bytes), output); fail("Size limit") }
            catch (failure: ProjectArchiveException) { assertEquals(ProjectArchiveFailure.SIZE_LIMIT, failure.reason) }
            assertEquals(0, output.size())
        }
    }
    @Test fun invalidUtf8TruncatedAndNonZipInputFailWithoutAProject() {
        rejected(zip(entries() + ("manifest.json" to byteArrayOf(-61, 40))), ProjectArchiveFailure.INVALID_TEXT)
        rejected(byteArrayOf(1, 2, 3), ProjectArchiveFailure.INVALID_ARCHIVE)
        val data = zip(entries())
        rejected(data.copyOf(50), ProjectArchiveFailure.INVALID_ARCHIVE)
    }
}
