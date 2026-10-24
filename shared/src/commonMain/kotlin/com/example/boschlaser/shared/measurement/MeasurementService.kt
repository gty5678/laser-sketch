package com.example.boschlaser.shared.measurement

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Business entry point shared by acquisition, history and measurement pickers. */
class MeasurementService(private val repository: MeasurementRepository) {
    val records get() = repository.records
    fun newestFirst(limit: Int = 200): List<MeasurementRecord> {
        require(limit >= 0)
        return records.value.asReversed().take(limit)
    }
    suspend fun refresh() = repository.refresh()
    suspend fun record(record: MeasurementRecord) = repository.save(record)
    suspend fun delete(id: String) = repository.delete(id)
    suspend fun clear() = repository.clear()
    fun acquire(device: LaserDevice, scope: CoroutineScope): Job = scope.launch {
        device.measurements.collect { record(MeasurementRecord(it)) }
    }
}
