package com.example.boschlaser.shared.measurement

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Session storage for tests, previews and hosts without durable storage configured. */
class InMemoryMeasurementRepository : MeasurementRepository {
    private val mutableRecords = MutableStateFlow<List<MeasurementRecord>>(emptyList())
    override val records = mutableRecords.asStateFlow()
    private val mutex = Mutex()
    override suspend fun refresh() = Unit
    override suspend fun save(record: MeasurementRecord) = mutex.withLock {
        val updated = records.value.toMutableList()
        val index = updated.indexOfFirst { it.measurement.id == record.measurement.id }
        if (index < 0) updated += record else updated[index] = record
        mutableRecords.value = updated.sortedBy { it.measurement.timestamp }
    }
    override suspend fun delete(id: String) = mutex.withLock {
        mutableRecords.value = records.value.filter { it.measurement.id != id }
    }
    override suspend fun clear() = mutex.withLock { mutableRecords.value = emptyList() }
}
