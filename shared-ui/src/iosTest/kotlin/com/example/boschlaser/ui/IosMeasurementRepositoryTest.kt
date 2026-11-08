@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.example.boschlaser.ui

import com.example.boschlaser.shared.measurement.*
import kotlinx.coroutines.runBlocking
import platform.Foundation.*
import kotlin.test.*

class IosMeasurementRepositoryTest {
    private val file = NSTemporaryDirectory() + NSUUID().UUIDString + ".json"
    @AfterTest fun clean() { NSFileManager.defaultManager.removeItemAtPath(file, null) }
    @Test fun durableUpdatesKeepIdsOrderingAndExactMillimeters() = runBlocking {
        val repository = IosMeasurementRepository(file)
        val first = MeasurementRecord(Measurement("first", 1234.567890123, 1), byteArrayOf(1, 2))
        val second = MeasurementRecord(Measurement("second", 1234.567890123, 2))
        repository.save(first); repository.save(second)
        val reopened = IosMeasurementRepository(file); reopened.refresh()
        assertEquals(listOf(first, second), reopened.records.value)
        val update = MeasurementRecord(first.measurement.copy(valueMm = 999.125))
        reopened.save(update)
        assertEquals(listOf(update, second), reopened.records.value)
        reopened.delete("first"); assertEquals(listOf(second), reopened.records.value)
        reopened.clear(); repository.refresh(); assertTrue(repository.records.value.isEmpty())
    }
    @Test fun failedWriteDoesNotPublishUndurableRecord() = runBlocking {
        val repository = IosMeasurementRepository(file + "/missing/file.json")
        assertFails { repository.save(MeasurementRecord(Measurement("id", 1.0, 1))) }
        assertTrue(repository.records.value.isEmpty())
    }
}
