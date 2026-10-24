package com.example.boschlaser.platform

import com.example.boschlaser.shared.model.ProjectAsset
import com.example.boschlaser.shared.storage.MigrationNotice
import com.example.boschlaser.shared.storage.ProjectFormatException
import com.example.boschlaser.shared.storage.ProjectFormatFailure
import com.example.boschlaser.shared.storage.ProjectMigration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID

enum class LegacyProjectKind(val directory: String, val prefix: String) {
    SKETCH("sketch-projects", "sketch-"), ANNOTATION("annotation-projects", "annotation-")
}
enum class LegacyMigrationStatus { MIGRATED, ALREADY_PRESENT, FAILED }
enum class LegacyMigrationFailure { INVALID_SOURCE, UNSUPPORTED_VERSION, IO_FAILURE, SIZE_LIMIT }
data class LegacyMigrationItem(val kind: LegacyProjectKind, val legacyId: String, val status: LegacyMigrationStatus,
    val notices: List<MigrationNotice> = emptyList(), val failure: LegacyMigrationFailure? = null)

/** Copies one old project at a time. Original metadata, photos and previews are always retained. */
class AndroidLegacyProjectMigration(private val filesDirectory: File, private val repository: AndroidProjectRepository) {
    suspend fun migrate(kinds: Set<LegacyProjectKind> = LegacyProjectKind.entries.toSet()): List<LegacyMigrationItem> = withContext(Dispatchers.IO) {
        buildList {
            for (kind in LegacyProjectKind.entries.filter { it in kinds }) {
                val root = File(filesDirectory, kind.directory).canonicalFile
                for (directory in root.listFiles().orEmpty().sortedBy { it.name }) {
                    if (!directory.isDirectory || !validId(directory.name)) continue
                    add(migrateOne(kind, root, directory))
                }
            }
        }
    }
    private suspend fun migrateOne(kind: LegacyProjectKind, root: File, directory: File): LegacyMigrationItem {
        val id = directory.name
        val targetId = kind.prefix + id
        try {
            if (directory.canonicalFile.parentFile != root) return failed(kind, id, LegacyMigrationFailure.INVALID_SOURCE)
            if (repository.wasMigrated(targetId)) return LegacyMigrationItem(kind, id, LegacyMigrationStatus.ALREADY_PRESENT)
            val metadata = child(directory, "project.json")
            val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            val text = decoder.decode(ByteBuffer.wrap(readBounded(metadata, 32 * 1024 * 1024))).toString()
            val asset = ProjectAsset("photo", "assets/photo.jpg", "image/jpeg")
            val migrated = when (kind) {
                LegacyProjectKind.SKETCH -> ProjectMigration.androidSketch(text)
                LegacyProjectKind.ANNOTATION -> ProjectMigration.androidAnnotation(text, asset)
            }
            if (migrated.project.manifest.projectId != id) return failed(kind, id, LegacyMigrationFailure.INVALID_SOURCE)
            val project = migrated.project.copy(manifest = migrated.project.manifest.copy(projectId = targetId))
            val assets = if (kind == LegacyProjectKind.ANNOTATION) mapOf(asset.id to readBounded(child(directory, "photo.jpg"), 128 * 1024 * 1024)) else emptyMap()
            val saved = repository.saveIfAbsent(project, assets)
            return LegacyMigrationItem(kind, id, if (saved) LegacyMigrationStatus.MIGRATED else LegacyMigrationStatus.ALREADY_PRESENT, migrated.notices)
        } catch (failure: ProjectFormatException) {
            return failed(kind, id, when (failure.reason) {
                ProjectFormatFailure.UNSUPPORTED_VERSION -> LegacyMigrationFailure.UNSUPPORTED_VERSION
                ProjectFormatFailure.SIZE_LIMIT -> LegacyMigrationFailure.SIZE_LIMIT
                else -> LegacyMigrationFailure.INVALID_SOURCE
            })
        } catch (_: java.nio.charset.CharacterCodingException) { return failed(kind, id, LegacyMigrationFailure.INVALID_SOURCE) }
        catch (_: IOException) { return failed(kind, id, LegacyMigrationFailure.IO_FAILURE) }
        catch (_: ProjectArchiveException) { return failed(kind, id, LegacyMigrationFailure.INVALID_SOURCE) }
    }
    private fun child(directory: File, name: String): File = File(directory, name).canonicalFile.also {
        if (it.parentFile != directory.canonicalFile) throw ProjectFormatException(ProjectFormatFailure.INVALID_DATA)
    }
    private fun readBounded(file: File, limit: Int): ByteArray = file.inputStream().use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size().toLong() + count > limit) throw ProjectFormatException(ProjectFormatFailure.SIZE_LIMIT)
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }
    private fun validId(id: String) = runCatching { UUID.fromString(id).toString() == id.lowercase() }.getOrDefault(false)
    private fun failed(kind: LegacyProjectKind, id: String, failure: LegacyMigrationFailure) =
        LegacyMigrationItem(kind, id, LegacyMigrationStatus.FAILED, failure = failure)
}
