package com.example.boschlaser.shared.bosch

import com.example.boschlaser.shared.ble.*
import com.example.boschlaser.shared.measurement.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

data class BoschFrameEvent(val frame: ByteArray, val measurement: Measurement?, val sessionId: Long)

/** The owner supplies a serial dispatcher and clocks; closing never cancels the owner's scope. */
class BoschLaserDevice(
    private val transport: BleTransport,
    scope: CoroutineScope,
    private val epochMillis: () -> Long,
    private val elapsedMillis: () -> Long,
    private val idFactory: () -> String = { Random.nextBytes(16).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') } },
) : LaserDevice {
    private val job = SupervisorJob(scope.coroutineContext[Job])
    private val deviceScope = CoroutineScope(scope.coroutineContext + job)
    private val assembler = MtFrameAssembler()
    private val mutableState = MutableStateFlow(LaserDeviceState())
    override val state = mutableState.asStateFlow()
    private val mutableEvents = MutableSharedFlow<BoschFrameEvent>()
    val events: Flow<BoschFrameEvent> = mutableEvents.filter {
        it.sessionId == transport.state.value.sessionId && transport.state.value.phase == BlePhase.READY
    }
    override val measurements: Flow<Measurement> = events.mapNotNull { it.measurement }
    private var activeDevice: String? = null
    private var activeSession = -1L
    private var initializedDevice: String? = null
    private val connections = Mutex()
    init {
        deviceScope.launch(start = CoroutineStart.UNDISPATCHED) {
            transport.state.collect { connection ->
                if (connection.phase != BlePhase.READY || connection.device?.id != activeDevice || connection.sessionId != activeSession) {
                    assembler.reset()
                }
                if (connection.phase != BlePhase.READY) initializedDevice = null
                activeDevice = connection.device?.id
                activeSession = connection.sessionId
                mutableState.value = LaserDeviceState(when (connection.phase) {
                    BlePhase.READY -> if (initializedDevice == connection.device?.id) LaserDevicePhase.READY else LaserDevicePhase.CONNECTING
                    BlePhase.DISCONNECTED -> LaserDevicePhase.DISCONNECTED
                    BlePhase.FAILED -> LaserDevicePhase.FAILED
                    else -> LaserDevicePhase.CONNECTING
                }, connection.device?.id, connection.failure)
            }
        }
        deviceScope.launch(start = CoroutineStart.UNDISPATCHED) {
            transport.notifications.collect { chunk ->
                if (transport.state.value.phase != BlePhase.READY) return@collect
                val session = transport.state.value.sessionId
                val deviceId = transport.state.value.device?.id
                for (frame in assembler.feed(chunk, elapsedMillis())) {
                    if (transport.state.value.sessionId != session || transport.state.value.phase != BlePhase.READY) break
                    val parsed = BoschProtocol.parseMeasurement(frame)
                        ?: BoschProtocol.parseContinuousMillimeters(frame)?.let { BoschMeasurement(it) }
                    val measurement = parsed?.let { Measurement(idFactory(), it.millimeters, epochMillis(),
                        deviceId, it.operation, it.accumulatedMillimeters) }
                    mutableEvents.emit(BoschFrameEvent(frame, measurement, session))
                }
            }
        }
    }
    suspend fun connect(device: BleDevice) = connections.withLock {
        if (!job.isActive) throw BleException(BleFailure.DISCONNECTED)
        initializedDevice = null
        assembler.reset()
        try {
            transport.connect(device)
            transport.subscribe(BoschProtocol.SERVICE_UUID, BoschProtocol.CHARACTERISTIC_UUID)
            transport.write(BoschProtocol.SERVICE_UUID, BoschProtocol.CHARACTERISTIC_UUID, BoschProtocol.enableMeasurements)
            initializedDevice = device.id
            mutableState.value = LaserDeviceState(LaserDevicePhase.READY, device.id)
        } catch (failure: Throwable) {
            withContext(NonCancellable) { transport.disconnect() }
            throw failure
        }
    }
    suspend fun disconnect() = connections.withLock { initializedDevice = null; assembler.reset(); transport.disconnect() }
    suspend fun send(payload: ByteArray) {
        if (state.value.phase != LaserDevicePhase.READY || transport.state.value.phase != BlePhase.READY) throw BleException(BleFailure.NOT_CONNECTED)
        transport.write(BoschProtocol.SERVICE_UUID, BoschProtocol.CHARACTERISTIC_UUID, payload.copyOf())
    }
    override suspend fun measure() = send(BoschProtocol.greenMeasureKey)
    override suspend fun startContinuous() = send(BoschProtocol.continuousPoll)
    override suspend fun stopContinuous() = send(BoschProtocol.continuousStop)
    fun close() { assembler.reset(); initializedDevice = null; mutableState.value = LaserDeviceState(); job.cancel() }
    companion object {
        fun matches(device: BleDevice, savedId: String? = null): Boolean =
            device.advertisedServices.any { it.equals(BoschProtocol.SERVICE_UUID, true) } ||
                device.name?.let { it.contains("GLM", true) || it.contains("BOSCH", true) } == true ||
                savedId != null && device.id.equals(savedId, true)
    }
}
