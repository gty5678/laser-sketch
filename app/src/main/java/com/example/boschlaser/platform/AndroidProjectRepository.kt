package com.example.boschlaser.platform

import com.example.boschlaser.shared.model.Project
import com.example.boschlaser.shared.model.ProjectInfo
import com.example.boschlaser.shared.storage.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Dedicated internal directory only. Legacy project directories are never modified here. */
class AndroidProjectRepository(directory: File,
    private val archive: AndroidProjectArchive = AndroidProjectArchive()) : ProjectRepository {
    private val root = directory.canonicalFile
    private val mutex = Mutex()

    override suspend fun save(project: Project) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val existing = read(project.manifest.projectId)
            val retained = existing?.assets.orEmpty().filterKeys { it in project.assets }
            write(project, retained)
        }
    }
    suspend fun save(project: Project, assets: Map<String, ByteArray>) = mutex.withLock {
        withContext(Dispatchers.IO) { write(project, assets) }
    }
    /** Migration is idempotent and cannot overwrite later edits to an already migrated project. */
    suspend fun saveIfAbsent(project: Project, assets: Map<String, ByteArray>): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (projectFile(project.manifest.projectId).exists() || migrationFile(project.manifest.projectId).exists()) false
            else { write(project, assets); markMigrated(project.manifest.projectId); true }
        }
    }
    suspend fun wasMigrated(id: String): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (migrationFile(id).isFile) true
            else if (projectFile(id).isFile) { read(id); markMigrated(id); true }
            else false
        }
    }
    override suspend fun load(id: String): Project? = mutex.withLock {
        withContext(Dispatchers.IO) { read(id)?.project }
    }
    suspend fun loadAsset(id: String, assetId: String): ByteArray? = mutex.withLock {
        withContext(Dispatchers.IO) { read(id)?.assets?.get(assetId) }
    }
    override suspend fun list(): List<ProjectInfo> = mutex.withLock {
        withContext(Dispatchers.IO) {
            root.listFiles().orEmpty().filter { it.isFile && it.extension == "lsk" }.map { file ->
                val project = file.inputStream().use { archive.read(it).project }
                check(file == projectFile(project.manifest.projectId))
                project.manifest.let { ProjectInfo(it.projectId, it.name, it.updatedAt) }
            }.sortedWith(compareByDescending<ProjectInfo> { it.updatedAt }.thenBy { it.id })
        }
    }
    override suspend fun delete(id: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (projectFile(id).exists() && (id.startsWith("sketch-") || id.startsWith("annotation-"))) markMigrated(id)
            Files.deleteIfExists(projectFile(id).toPath())
            Unit
        }
    }
    suspend fun import(input: InputStream, copyId: String? = null, updatedAt: Long? = null): Project = mutex.withLock {
        withContext(Dispatchers.IO) {
            val imported = archive.read(input)
            val project = if (copyId == null) imported.project else imported.project.copy(
                manifest = imported.project.manifest.copy(projectId = copyId, updatedAt = updatedAt ?: imported.project.manifest.updatedAt))
            check(!projectFile(project.manifest.projectId).exists()) { "Project already exists" }
            write(project, imported.assets)
            project
        }
    }
    suspend fun export(id: String, output: OutputStream) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val stored = read(id) ?: throw NoSuchElementException()
            archive.write(stored.project, stored.assets, output)
        }
    }
    private fun read(id: String): ProjectArchive? {
        val file = projectFile(id)
        if (!file.isFile) return null
        return file.inputStream().use { archive.read(it) }.also { check(it.project.manifest.projectId == id) }
    }
    private fun write(project: Project, assets: Map<String, ByteArray>) {
        check(root.isDirectory || root.mkdirs())
        val target = projectFile(project.manifest.projectId)
        val temporary = File.createTempFile("project-", ".tmp", root)
        try {
            FileOutputStream(temporary).use { output ->
                archive.write(project, assets, output)
                output.fd.sync()
            }
            // Fail without replacing the prior project if the filesystem cannot commit atomically.
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary.toPath()) }
    }
    private fun projectFile(id: String) = File(root, "${fileKey(id)}.lsk")
    private fun migrationFile(id: String) = File(root, "${fileKey(id)}.migrated")
    private fun markMigrated(id: String) {
        val target = migrationFile(id)
        if (target.isFile) return
        val temporary = File.createTempFile("migration-", ".tmp", root)
        try {
            FileOutputStream(temporary).use { output -> output.write(1); output.fd.sync() }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary.toPath()) }
    }
    private fun fileKey(id: String): String {
        require(id.isNotBlank())
        return MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8))
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    }
}
