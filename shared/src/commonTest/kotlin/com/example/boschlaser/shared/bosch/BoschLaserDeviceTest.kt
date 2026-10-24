package com.example.boschlaser.shared.bosch

import com.example.boschlaser.shared.ble.*
import com.example.boschlaser.shared.measurement.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class BoschLaserDeviceTest {
    private val candidate = BleDevice("test-device", "Test GLM")
    private class FakeTransport : BleTransport {
        val connection = MutableStateFlow(BleState())
        override val state = connection.asStateFlow()
        val chunks = MutableSharedFlow<ByteArray>()
        override val notifications = chunks.asSharedFlow()
        val calls = mutableListOf<String>()
        val writes = mutableListOf<ByteArray>()
        var subscribeFails = false
        var connectGate: CompletableDeferred<Unit>? = null
        var enableGate: CompletableDeferred<Unit>? = null
        override suspend fun scan(stopWhen: (BleDevice) -> Boolean) = emptyList<BleDevice>()
        override suspend fun connect(device: BleDevice) {
            calls += "connect"
            connection.value = BleState(BlePhase.CONNECTING, device, sessionId = connection.value.sessionId + 1)
            connectGate?.await()
            connection.value = connection.value.copy(phase = BlePhase.CONNECTED)
        }
        override suspend fun subscribe(serviceUuid: String, characteristicUuid: String) {
            calls += "subscribe"
            assertEquals(BoschProtocol.SERVICE_UUID, serviceUuid)
            assertEquals(BoschProtocol.CHARACTERISTIC_UUID, characteristicUuid)
            if (subscribeFails) throw BleException(BleFailure.SUBSCRIBE)
            connection.value = connection.value.copy(phase = BlePhase.READY)
        }
        override suspend fun disconnect() { calls += "disconnect"; connection.value = BleState(sessionId = connection.value.sessionId + 1) }
        override suspend fun write(serviceUuid: String, characteristicUuid: String, data: ByteArray) {
            calls += "write"
            assertEquals(BoschProtocol.SERVICE_UUID, serviceUuid)
            assertEquals(BoschProtocol.CHARACTERISTIC_UUID, characteristicUuid)
            if (data.contentEquals(BoschProtocol.enableMeasurements)) enableGate?.await()
            writes += data.copyOf()
        }
    }
    private fun TestScope.device(transport: FakeTransport, clock: () -> Long = { 0L }) = BoschLaserDevice(
        transport, backgroundScope, { 123456L }, clock, { "test-measurement" })
    @Test fun readyRequiresSubscriptionAndAcknowledgedInitialization() = runTest {
        val transport = FakeTransport().apply { enableGate = CompletableDeferred() }
        val device = device(transport)
        val connection = async { device.connect(candidate) }
        runCurrent()
        assertEquals(LaserDevicePhase.CONNECTING, device.state.value.phase)
        assertFailsWith<BleException> { device.measure() }
        transport.enableGate!!.complete(Unit)
        connection.await(); runCurrent()
        assertEquals(listOf("connect", "subscribe", "write"), transport.calls)
        assertEquals(LaserDevicePhase.READY, device.state.value.phase)
        assertContentEquals(BoschProtocol.enableMeasurements, transport.writes.single())
    }
    @Test fun deviceCommandsUseProtocolAndRejectDisconnectedWrites() = runTest {
        val transport = FakeTransport()
        val device = device(transport)
        assertFailsWith<BleException> { device.measure() }
        device.connect(candidate); runCurrent()
        device.measure(); device.startContinuous(); device.stopContinuous()
        assertContentEquals(BoschProtocol.greenMeasureKey, transport.writes[1])
        assertContentEquals(BoschProtocol.continuousPoll, transport.writes[2])
        assertContentEquals(BoschProtocol.continuousStop, transport.writes[3])
        device.disconnect(); runCurrent()
        assertFailsWith<BleException> { device.startContinuous() }
    }
    @Test fun fragmentedMeasurementUsesInjectedIdentityTimeAndMillimeters() = runTest {
        val transport = FakeTransport()
        var elapsed = 0L
        val device = device(transport) { elapsed }
        val values = mutableListOf<Measurement>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { device.measurements.collect { values += it } }
        device.connect(candidate); runCurrent()
        val frame = measurementFrame()
        transport.chunks.emit(frame.copyOf(5)); elapsed = 100L
        transport.chunks.emit(frame.copyOfRange(5, frame.size)); runCurrent()
        assertEquals(1, values.size)
        assertEquals(2500.0, values.single().valueMm)
        assertEquals("test-measurement", values.single().id)
        assertEquals(123456L, values.single().timestamp)
        assertEquals(candidate.id, values.single().deviceId)
        transport.chunks.emit(frame.copyOf().apply { this[7] = 99 }); runCurrent()
        assertEquals(1, values.size)
    }
    @Test fun disconnectAndReconnectDiscardOldPartialPackets() = runTest {
        val transport = FakeTransport()
        val device = device(transport)
        val values = mutableListOf<Measurement>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { device.measurements.collect { values += it } }
        device.connect(candidate); runCurrent()
        transport.chunks.emit(measurementFrame().copyOf(5)); runCurrent()
        device.disconnect(); runCurrent()
        transport.chunks.emit(measurementFrame()); runCurrent()
        assertTrue(values.isEmpty())
        device.connect(candidate); runCurrent()
        transport.chunks.emit(measurementFrame()); runCurrent()
        assertEquals(1, values.size)
    }
    @Test fun failedSubscriptionClosesTransportAndDoesNotEnableMeasurements() = runTest {
        val transport = FakeTransport().apply { subscribeFails = true }
        val device = device(transport)
        assertFailsWith<BleException> { device.connect(candidate) }
        runCurrent()
        assertEquals(listOf("connect", "subscribe", "disconnect"), transport.calls)
        assertTrue(transport.writes.isEmpty())
        assertEquals(LaserDevicePhase.DISCONNECTED, device.state.value.phase)
    }
    @Test fun cancellingConnectionReleasesTransportAndAllowsNextConnection() = runTest {
        val transport = FakeTransport().apply { connectGate = CompletableDeferred() }
        val device = device(transport)
        val pending = launch { device.connect(candidate) }
        runCurrent(); pending.cancelAndJoin(); runCurrent()
        assertEquals(BlePhase.DISCONNECTED, transport.state.value.phase)
        transport.connectGate = null
        device.connect(candidate); runCurrent()
        assertEquals(LaserDevicePhase.READY, device.state.value.phase)
    }
    @Test fun closingDeviceStopsCollectorsWithoutCancellingOwnerScope() = runTest {
        val transport = FakeTransport()
        val device = device(transport)
        val events = mutableListOf<BoschFrameEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { device.events.collect { events += it } }
        device.connect(candidate); runCurrent()
        device.close()
        assertTrue(backgroundScope.isActive)
        transport.chunks.emit(measurementFrame()); runCurrent()
        assertTrue(events.isEmpty())
        assertEquals(LaserDevicePhase.DISCONNECTED, device.state.value.phase)
    }
    @Test fun bufferedFramesFromPreviousSessionDoNotBecomeNewSessionMeasurements() = runTest {
        val transport = FakeTransport()
        val device = device(transport)
        val firstEvent = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<BoschFrameEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            device.events.collect {
                events += it
                if (events.size == 1) { firstEvent.complete(Unit); release.await() }
            }
        }
        device.connect(candidate); runCurrent()
        val notification = launch { transport.chunks.emit(measurementFrame() + measurementFrame()) }
        firstEvent.await()
        device.disconnect(); runCurrent()
        device.connect(candidate); runCurrent()
        release.complete(Unit); notification.join(); runCurrent()
        assertEquals(1, events.size)
        transport.chunks.emit(measurementFrame()); runCurrent()
        assertEquals(2, events.size)
    }
    @Test fun closedDeviceCannotBeReused() = runTest {
        val transport = FakeTransport()
        val device = device(transport)
        device.close()
        assertFailsWith<BleException> { device.connect(candidate) }
        assertTrue(transport.calls.isEmpty())
    }
    @Test fun matchingUsesServiceNameOrSavedIdentity() {
        assertTrue(BoschLaserDevice.matches(candidate))
        assertTrue(BoschLaserDevice.matches(BleDevice("test-device", advertisedServices = listOf(BoschProtocol.SERVICE_UUID.uppercase()))))
        assertTrue(BoschLaserDevice.matches(BleDevice("saved-test"), "saved-test"))
        assertFalse(BoschLaserDevice.matches(BleDevice("other-test", "Unrelated")))
    }
    private fun measurementFrame(): ByteArray {
        val body = ByteArray(19)
        body[0] = 0xC0.toByte(); body[1] = 85; body[2] = 16; body[3] = 4
        for (index in 0..3) body[7 + index] = (2.5f.toBits() ushr (index * 8)).toByte()
        return body + BoschProtocol.crc8(body).toByte()
    }
}
