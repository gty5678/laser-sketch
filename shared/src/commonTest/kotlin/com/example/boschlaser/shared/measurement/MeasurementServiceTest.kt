package com.example.boschlaser.shared.measurement

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class MeasurementServiceTest {
    private fun record(id: String, value: Double, time: Long) = MeasurementRecord(Measurement(id, value, time))
    @Test fun listenerReceivesNewestMeasurementsImmediately() = runTest {
        val service = MeasurementService(InMemoryMeasurementRepository())
        val snapshots = mutableListOf<List<Double>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            service.records.collect { snapshots += service.newestFirst().map { it.measurement.valueMm } }
        }
        service.record(record("first", 1200.0, 1))
        service.record(record("second", 2350.5, 2))
        assertEquals(listOf(2350.5, 1200.0), snapshots.last())
    }
    @Test fun timestampCollisionsKeepBothIdsAndDeleteOnlyRequestedRecord() = runTest {
        val service = MeasurementService(InMemoryMeasurementRepository())
        service.record(record("first", 1000.0, 1))
        service.record(record("second", 2000.0, 1))
        assertEquals(listOf("second", "first"), service.newestFirst().map { it.measurement.id })
        service.delete("first")
        assertEquals(listOf("second"), service.records.value.map { it.measurement.id })
        service.clear()
        assertTrue(service.records.value.isEmpty())
    }
    @Test fun recentLimitDoesNotTruncateHistoryAndUpdatesPreserveTieOrder() = runTest {
        val service = MeasurementService(InMemoryMeasurementRepository())
        repeat(205) { service.record(record("record-$it", it.toDouble(), 1)) }
        service.record(record("record-0", 42.0, 1))
        assertEquals(205, service.records.value.size)
        assertEquals(200, service.newestFirst().size)
        assertEquals("record-204", service.newestFirst().first().measurement.id)
        assertEquals("record-0", service.records.value.first().measurement.id)
        assertEquals(42.0, service.records.value.first().measurement.valueMm)
        assertTrue(service.newestFirst(0).isEmpty())
        assertFailsWith<IllegalArgumentException> { service.newestFirst(-1) }
    }
    @Test fun failedPersistenceIsPropagatedWithoutPublishingPhantomRecords() = runTest {
        val repository = InMemoryMeasurementRepository()
        val failing = object : MeasurementRepository by repository {
            override suspend fun save(record: MeasurementRecord) { throw IllegalStateException("test failure") }
        }
        val service = MeasurementService(failing)
        assertFailsWith<IllegalStateException> { service.record(record("failed", 1000.0, 1)) }
        assertTrue(service.records.value.isEmpty())
    }
    @Test fun acquisitionUsesGenericDeviceAndStopsWhenOwnerCancels() = runTest {
        val input = MutableSharedFlow<Measurement>()
        val device = object : LaserDevice {
            override val state = MutableStateFlow(LaserDeviceState(LaserDevicePhase.READY))
            override val measurements = input.asSharedFlow()
            override suspend fun measure() = Unit
            override suspend fun startContinuous() = Unit
            override suspend fun stopContinuous() = Unit
        }
        val service = MeasurementService(InMemoryMeasurementRepository())
        val job = service.acquire(device, backgroundScope)
        runCurrent()
        input.emit(Measurement("device-measurement", 1234.56789, 1)); runCurrent()
        assertEquals(1234.56789, service.records.value.single().measurement.valueMm)
        job.cancelAndJoin()
        input.emit(Measurement("ignored", 1000.0, 2)); runCurrent()
        assertEquals(1, service.records.value.size)
        assertTrue(backgroundScope.isActive)
    }
    @Test fun recordsCopyDiagnosticBytesAndCompareTheirContents() {
        val data = byteArrayOf(1, 2, 3)
        val measurement = Measurement("record", 1000.0, 1)
        val record = MeasurementRecord(measurement, data)
        data[0] = 99
        record.raw[1] = 99
        assertContentEquals(byteArrayOf(1, 2, 3), record.raw)
        assertEquals(MeasurementRecord(measurement, byteArrayOf(1, 2, 3)), record)
        assertFailsWith<IllegalArgumentException> { Measurement("invalid", Double.NaN, 1) }
        assertFailsWith<IllegalArgumentException> { Measurement("invalid", -1.0, 1) }
    }
}
