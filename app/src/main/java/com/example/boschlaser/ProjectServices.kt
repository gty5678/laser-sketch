package com.example.boschlaser

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.boschlaser.platform.*
import com.example.boschlaser.shared.model.*
import com.example.boschlaser.shared.annotation.AnnotationDocument
import com.example.boschlaser.shared.storage.ProjectService
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.security.MessageDigest
import java.util.UUID

data class PhotoProject(val project: Project, val assetId: String, val bytes: ByteArray)

/** Application-owned queue accepts immutable documents, never Views or Activity callbacks. */
class ProjectServices private constructor(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val files = context.applicationContext.filesDir
    private val previews = File(context.applicationContext.cacheDir, "project-previews")
    private val repository = AndroidProjectRepository(File(files, "projects"))
    private val migration = AndroidLegacyProjectMigration(files, repository)
    private val service = ProjectService(repository, System::currentTimeMillis) { UUID.randomUUID().toString() }
    val projects = service.projects
    private val failures = MutableStateFlow(false)
    val errors = failures.asStateFlow()
    fun acknowledgeError() { failures.value = false }
    private val notices = MutableStateFlow(false)
    val migrationNotices = notices.asStateFlow()
    fun acknowledgeMigrationNotice() { notices.value = false }
    private val reported = mutableSetOf<LegacyMigrationItem>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requests = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private var initialized = false
    init {
        scope.launch { for (request in requests) request() }
        submit { Unit }
    }
    private fun <T> submit(action: suspend () -> T): Deferred<T> {
        val result = CompletableDeferred<T>()
        check(requests.trySend {
            try {
                if (!initialized) { migrateLegacyProjects(); service.refresh(); initialized = true }
                result.complete(action())
            } catch (failure: CancellationException) { result.cancel(failure); throw failure }
            catch (failure: Exception) { result.completeExceptionally(failure); failures.value = true }
        }.isSuccess)
        return result
    }
    private suspend fun migrateLegacyProjects() {
        migration.migrate().forEach { item ->
            if ((item.status == LegacyMigrationStatus.FAILED || item.notices.isNotEmpty()) && reported.add(item)) notices.value = true
        }
    }
    fun refresh() = submit { migrateLegacyProjects(); service.refresh() }
    fun createSketch(name: String) = submit { service.createSketch(name) }
    fun loadSketch(id: String): Deferred<Project?> = submit {
        service.load(id) ?: if (runCatching { UUID.fromString(id) }.isSuccess) {
            migrateLegacyProjects()
            service.refresh()
            service.load(LegacyProjectKind.SKETCH.prefix + id)
        } else null
    }
    fun createAnnotation(name: String, jpeg: ByteArray, width: Int, height: Int, id: String = UUID.randomUUID().toString()): Deferred<Project> {
        val bytes = jpeg.copyOf()
        return submit {
            service.load(id)?.let { return@submit it }
            val asset = ProjectAsset(UUID.randomUUID().toString(), "assets/photo.jpg", "image/jpeg", width, height)
            val project = Project(ProjectManifest(id, name.trim(), System.currentTimeMillis()),
                annotations = mapOf(asset.id to AnnotationDocument(photoAssetId = asset.id)), assets = mapOf(asset.id to asset))
            repository.save(project, mapOf(asset.id to bytes))
            service.refresh()
            project
        }
    }
    fun loadAnnotation(id: String, assetId: String? = null): Deferred<PhotoProject?> = submit {
        val project = service.load(id) ?: if (runCatching { UUID.fromString(id) }.isSuccess) {
            migrateLegacyProjects()
            service.refresh()
            service.load(LegacyProjectKind.ANNOTATION.prefix + id)
        } else null
        if (project == null) return@submit null
        val photoId = assetId ?: project.annotations.keys.firstOrNull() ?: return@submit null
        require(photoId in project.annotations)
        val bytes = repository.loadAsset(project.manifest.projectId, photoId) ?: throw NoSuchElementException()
        PhotoProject(project, photoId, bytes)
    }
    fun saveSketch(id: String, document: SketchDocument, preview: ByteArray?) = submit {
        val project = service.saveSketch(id, document)
        writePreview(id, preview)
        project
    }
    fun saveAnnotation(id: String, assetId: String, document: AnnotationDocument, preview: ByteArray?) = submit {
        val project = service.saveAnnotation(id, assetId, document)
        writePreview("$id/$assetId", preview)
        project
    }
    private fun writePreview(key: String, preview: ByteArray?) {
        if (preview == null) return
        check(previews.isDirectory || previews.mkdirs())
        val target = previewFile(key)
        val temporary = File.createTempFile("preview-", ".tmp", previews)
        try {
            temporary.writeBytes(preview)
            java.nio.file.Files.move(temporary.toPath(), target.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } finally { temporary.delete() }
    }
    fun rename(id: String, name: String) = submit { service.rename(id, name) }
    fun delete(id: String) = submit {
        val project = service.load(id)
        service.delete(id)
        previewFile(id).delete()
        project?.annotations?.keys?.forEach { previewFile("$id/$it").delete() }
        Unit
    }
    /** Picker grants are used immediately; the application queue owns and closes every stream. */
    fun importProject(uri: Uri): Deferred<Project> = submit {
        val imported = resolver.openInputStream(uri).use { input ->
            repository.import(requireNotNull(input), UUID.randomUUID().toString(), System.currentTimeMillis())
        }
        service.refresh()
        imported
    }
    fun exportProject(id: String, uri: Uri): Deferred<Unit> = submit {
        resolver.openOutputStream(uri, "wt").use { output -> repository.export(id, requireNotNull(output)) }
    }
    fun flush() = submit { Unit }
    suspend fun thumbnail(id: String, targetSize: Int, assetId: String? = null) = withContext(Dispatchers.IO) {
        flush().await()
        require(targetSize > 0)
        val cached = previewFile(if (assetId == null) id else "$id/$assetId")
        val kind = if (assetId == null) LegacyProjectKind.SKETCH else LegacyProjectKind.ANNOTATION
        val legacyId = id.removePrefix(kind.prefix)
        val file = if (cached.isFile) cached else if (id.startsWith(kind.prefix) &&
            runCatching { UUID.fromString(legacyId).toString() == legacyId.lowercase() }.getOrDefault(false))
            File(files, "${kind.directory}/$legacyId/preview.png") else null
        if (file == null || !file.isFile) {
            if (assetId == null) return@withContext null
            val bytes = repository.loadAsset(id, assetId) ?: return@withContext null
            return@withContext com.example.boschlaser.platform.AndroidPhotoCodec.decode(bytes, targetSize)
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > targetSize || bounds.outHeight / sample > targetSize) sample *= 2
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }
    private fun previewFile(id: String): File {
        val key = MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8))
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        return File(previews, "$key.png")
    }
    companion object {
        @Volatile private var instance: ProjectServices? = null
        fun get(context: Context): ProjectServices = instance ?: synchronized(this) {
            instance ?: ProjectServices(context.applicationContext).also { instance = it }
        }
    }
}
