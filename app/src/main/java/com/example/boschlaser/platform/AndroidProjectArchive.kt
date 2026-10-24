package com.example.boschlaser.platform

import com.example.boschlaser.shared.model.Project
import com.example.boschlaser.shared.model.ProjectAsset
import com.example.boschlaser.shared.storage.ProjectSerializer
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class ProjectArchiveFailure { INVALID_ARCHIVE, INVALID_PATH, DUPLICATE_ENTRY, MISSING_ASSET, UNKNOWN_ASSET, SIZE_LIMIT, INVALID_TEXT }
class ProjectArchiveException(val reason: ProjectArchiveFailure) : Exception(reason.name)

data class ProjectArchiveLimits(val metadataBytes: Long = 32L * 1024 * 1024,
    val assetBytes: Long = 128L * 1024 * 1024, val totalBytes: Long = 256L * 1024 * 1024,
    val entryCount: Int = 4096) {
    init { require(metadataBytes > 0 && assetBytes > 0 && totalBytes > 0 && entryCount > 0) }
}

/** Photos are opaque bytes. No ZIP entry is ever extracted to a filesystem path. */
class ProjectArchive(project: Project, assets: Map<String, ByteArray>) {
    val project = project
    private val contents = assets.mapValues { it.value.copyOf() }
    val assets: Map<String, ByteArray> get() = contents.mapValues { it.value.copyOf() }
    init { require(assets.keys == project.assets.keys) }
}

/** Caller owns its streams. Container and metadata failures carry no user content. */
class AndroidProjectArchive(private val limits: ProjectArchiveLimits = ProjectArchiveLimits()) {
    fun write(project: Project, assets: Map<String, ByteArray>, output: OutputStream) {
        validateAssets(project, assets.keys)
        val entries = linkedMapOf<String, ByteArray>()
        ProjectSerializer.encode(project).forEach { (path, text) -> entries[path] = text.toByteArray(Charsets.UTF_8) }
        project.assets.values.forEach { entries[it.path] = assets.getValue(it.id) }
        validateSizes(entries)
        ZipOutputStream(object : FilterOutputStream(output) {
            override fun write(bytes: ByteArray, offset: Int, length: Int) { out.write(bytes, offset, length) }
            override fun close() { flush() }
        }).use { zip ->
            entries.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path).apply { time = 0 })
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    fun read(input: InputStream): ProjectArchive {
        val entries = linkedMapOf<String, ByteArray>()
        var total = 0L
        try {
            ZipInputStream(object : FilterInputStream(input) {
                override fun close() = Unit
            }).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    validatePath(entry.name)
                    if (entry.isDirectory) fail(ProjectArchiveFailure.INVALID_PATH)
                    if (entry.name in entries) fail(ProjectArchiveFailure.DUPLICATE_ENTRY)
                    if (entries.size >= limits.entryCount) fail(ProjectArchiveFailure.SIZE_LIMIT)
                    val limit = if (entry.name in ProjectSerializer.entries) limits.metadataBytes else limits.assetBytes
                    val bytes = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        total += count
                        if (bytes.size().toLong() + count > limit || total > limits.totalBytes) fail(ProjectArchiveFailure.SIZE_LIMIT)
                        bytes.write(buffer, 0, count)
                    }
                    entries[entry.name] = bytes.toByteArray()
                    zip.closeEntry()
                }
            }
        } catch (_: ZipException) { fail(ProjectArchiveFailure.INVALID_ARCHIVE) }
        catch (_: java.io.EOFException) { fail(ProjectArchiveFailure.INVALID_ARCHIVE) }
        if (entries.isEmpty()) fail(ProjectArchiveFailure.INVALID_ARCHIVE)
        val metadata = entries.filterKeys { it in ProjectSerializer.entries }.mapValues { decodeText(it.value) }
        val project = ProjectSerializer.decode(metadata)
        val assetEntries = entries.filterKeys { it !in ProjectSerializer.entries }
        val expectedPaths = project.assets.values.map { it.path }.toSet()
        if (!assetEntries.keys.containsAll(expectedPaths)) fail(ProjectArchiveFailure.MISSING_ASSET)
        if (assetEntries.keys != expectedPaths) fail(ProjectArchiveFailure.UNKNOWN_ASSET)
        return ProjectArchive(project, project.assets.mapValues { assetEntries.getValue(it.value.path) })
    }

    private fun validatePath(path: String) {
        if (path in ProjectSerializer.entries) return
        try { ProjectAsset("entry", path, "application/octet-stream") }
        catch (_: IllegalArgumentException) { fail(ProjectArchiveFailure.INVALID_PATH) }
    }
    private fun validateAssets(project: Project, ids: Set<String>) {
        if (!ids.containsAll(project.assets.keys)) fail(ProjectArchiveFailure.MISSING_ASSET)
        if (ids != project.assets.keys) fail(ProjectArchiveFailure.UNKNOWN_ASSET)
    }
    private fun validateSizes(entries: Map<String, ByteArray>) {
        if (entries.size > limits.entryCount) fail(ProjectArchiveFailure.SIZE_LIMIT)
        var total = 0L
        for ((path, bytes) in entries) {
            val limit = if (path in ProjectSerializer.entries) limits.metadataBytes else limits.assetBytes
            total += bytes.size
            if (bytes.size > limit || total > limits.totalBytes) fail(ProjectArchiveFailure.SIZE_LIMIT)
        }
    }
    private fun decodeText(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: java.nio.charset.CharacterCodingException) { fail(ProjectArchiveFailure.INVALID_TEXT) }
    private fun fail(reason: ProjectArchiveFailure): Nothing = throw ProjectArchiveException(reason)
}
