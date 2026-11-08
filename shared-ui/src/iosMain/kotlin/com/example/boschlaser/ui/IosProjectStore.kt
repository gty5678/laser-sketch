@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.example.boschlaser.ui

import com.example.boschlaser.shared.model.Project
import com.example.boschlaser.shared.storage.ProjectSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.Foundation.*

/** Private iOS storage. Semantic entries use the shared codec; this is not an exported .lsk archive. */
internal class IosProjectStore(private val rootOverride: String? = null) {
    private val directory: String
        get() {
            val root = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String
            val path = rootOverride ?: "$root/LaserSketch/projects"
            check(NSFileManager.defaultManager.createDirectoryAtPath(path, true, null, null))
            return path
        }

    private fun path(id: String): String {
        require(id.isNotEmpty() && id.all { it.isLetterOrDigit() || it == '-' })
        return "$directory/$id.json"
    }

    fun exists(id: String): Boolean = NSFileManager.defaultManager.fileExistsAtPath(path(id))

    fun load(id: String): Project {
        val text = NSString.stringWithContentsOfFile(path(id), NSUTF8StringEncoding, null)
            ?: error("Project read failed")
        return ProjectSerializer.decode(Json.parseToJsonElement(text).jsonObject.mapValues { it.value.jsonPrimitive.content })
            .also { require(it.manifest.projectId == id) }
    }

    fun list(): List<Project> = (NSFileManager.defaultManager.contentsOfDirectoryAtPath(directory, null)
        ?: error("Project directory read failed"))
        .filterIsInstance<String>().filter { it.endsWith(".json") }
        .map { load(it.removeSuffix(".json")) }.sortedByDescending { it.manifest.updatedAt }

    fun asset(project: Project, id: String): ByteArray {
        val asset = project.assets.getValue(id)
        return checkNotNull(NSData.dataWithContentsOfFile(assetPath(project.manifest.projectId, asset.path))).byteArray()
    }

    private fun assetPath(projectId: String, relative: String): String {
        path(projectId) // Validate the project ID before using it as a directory name.
        com.example.boschlaser.shared.model.ProjectAsset("entry", relative, "application/octet-stream")
        return "$directory/$projectId/$relative"
    }

    fun putAsset(projectId: String, relative: String, bytes: ByteArray) {
        val destination = assetPath(projectId, relative)
        require(bytes.size <= 128 * 1024 * 1024)
        check(!NSFileManager.defaultManager.fileExistsAtPath(destination))
        check(NSFileManager.defaultManager.createDirectoryAtPath(destination.substringBeforeLast('/'), true, null, null))
        check(bytes.nsData().writeToFile(destination, true))
    }

    fun export(project: Project): ByteArray = IosProjectArchive.write(project, project.assets.mapValues { asset(project, it.key) })

    fun import(bytes: ByteArray): Project {
        val archive = IosProjectArchive.read(bytes)
        var id = uuid()
        while (exists(id) || NSFileManager.defaultManager.fileExistsAtPath("$directory/$id")) id = uuid()
        val project = archive.project.copy(manifest = archive.project.manifest.copy(projectId = id, updatedAt = now()))
        try {
            archive.assets.forEach { (assetId, bytes) -> putAsset(id, project.assets.getValue(assetId).path, bytes) }
            save(project)
            return project
        } catch (error: Exception) {
            NSFileManager.defaultManager.removeItemAtPath("$directory/$id", null)
            throw error
        }
    }

    fun delete(id: String) {
        check(NSFileManager.defaultManager.removeItemAtPath(path(id), null))
        // Asset cleanup follows metadata removal; failed cleanup cannot revive a deleted project.
        NSFileManager.defaultManager.removeItemAtPath("$directory/$id", null)
    }

    fun save(project: Project) {
        val text = JsonObject(ProjectSerializer.encode(project).mapValues { JsonPrimitive(it.value) }).toString()
        check((text as NSString).writeToFile(path(project.manifest.projectId), true, NSUTF8StringEncoding, null))
    }
}
