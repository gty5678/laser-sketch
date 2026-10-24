package com.example.boschlaser.shared.measurement

import kotlinx.coroutines.flow.StateFlow

/** Oldest first; equal timestamps preserve insertion order. Publish only after durable success. */
interface MeasurementRepository {
    val records: StateFlow<List<MeasurementRecord>>
    suspend fun refresh()
    suspend fun save(record: MeasurementRecord)
    suspend fun delete(id: String)
    suspend fun clear()
}
