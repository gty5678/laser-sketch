@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.example.boschlaser.ui

import com.example.boschlaser.shared.measurement.*
import com.example.boschlaser.shared.model.Project
import com.example.boschlaser.shared.model.ProjectManifest
import com.example.boschlaser.shared.storage.ProjectSerializer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.*

/** Publish after an atomic disk write. The same record flow feeds history and annotation pickers. */
internal class IosMeasurementRepository(private val overridePath: String? = null) : MeasurementRepository {
    private val path get(): String {
        overridePath?.let { return it }
        val root = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String
        val directory = "$root/LaserSketch"
        check(NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null))
        return "$directory/measurements.json"
    }
    private val values = MutableStateFlow<List<MeasurementRecord>>(emptyList())
    override val records = values.asStateFlow()
    private val lock = Mutex()
    private fun load(): List<MeasurementRecord> {
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return emptyList()
        val text = checkNotNull(NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null))
        val files = ProjectSerializer.encode(Project(ProjectManifest("history", "history", 0))).toMutableMap()
        files["measurements.json"] = text
        return ProjectSerializer.decode(files).measurements
    }
    private fun persist(records: List<MeasurementRecord>) {
        val text = ProjectSerializer.encode(Project(ProjectManifest("history", "history", 0), measurements = records))
            .getValue("measurements.json")
        check((text as NSString).writeToFile(path, true, NSUTF8StringEncoding, null))
        values.value = records
    }
    override suspend fun refresh() = withContext(Dispatchers.Default) { lock.withLock { values.value = load() } }
    override suspend fun save(record: MeasurementRecord) = withContext(Dispatchers.Default) { lock.withLock {
        val loaded = load()
        val next = if (loaded.any { it.measurement.id == record.measurement.id })
            loaded.map { if (it.measurement.id == record.measurement.id) record else it } else loaded + record
        persist(next.sortedBy { it.measurement.timestamp })
    } }
    override suspend fun delete(id: String) = withContext(Dispatchers.Default) { lock.withLock { persist(load().filterNot { it.measurement.id == id }) } }
    override suspend fun clear() = withContext(Dispatchers.Default) { lock.withLock { persist(emptyList()) } }
}
