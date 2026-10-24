package com.example.boschlaser

import android.content.Context
import android.os.SystemClock
import com.example.boschlaser.platform.AndroidBleTransport
import com.example.boschlaser.shared.ble.*
import com.example.boschlaser.shared.bosch.BoschLaserDevice
import com.example.boschlaser.shared.measurement.MeasurementRecord
import com.example.boschlaser.shared.measurement.LaserDevicePhase
import kotlinx.coroutines.*

/** Android host compatibility controller; protocol and GATT live below this boundary. */
class BoschBleManager(context: Context, private val listener: Listener) {
    data class ScanCandidate(val name: String, val address: String, val rssi: Int, val advertisedServices: List<String>) {
        val displayText: String get() = "$name  ·  $address  ·  ${rssi} dBm"
    }
    interface Listener {
        fun onConnectionStatus(message: String)
        fun onConnected(name: String, address: String)
        fun onDisconnected()
        fun onMeasurement(measurement: MeasurementRecord)
        fun onScanCandidates(devices: List<ScanCandidate>)
        fun onError(message: String)
    }
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("bosch_glm", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val transport = AndroidBleTransport(appContext)
    private val device = BoschLaserDevice(transport, scope, System::currentTimeMillis, SystemClock::elapsedRealtime)
    private var operation: Job? = null
    private var closed = false
    val savedAddress: String? get() = preferences.getString("address", null)
    val savedName: String? get() = preferences.getString("name", null)
    val isConnected: Boolean get() = device.state.value.phase == LaserDevicePhase.READY
    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            transport.state.collect { state ->
                val resource = when (state.phase) {
                    BlePhase.SCANNING -> R.string.ble_scanning
                    BlePhase.CONNECTING -> R.string.ble_connecting
                    BlePhase.DISCOVERING -> R.string.ble_discovering
                    BlePhase.SUBSCRIBING -> R.string.ble_subscribing
                    BlePhase.CONNECTED -> R.string.ble_service_found
                    else -> null
                }
                if (resource != null) status(resource)
            }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var connectedId: String? = null
            device.state.collect { state ->
                if (state.phase == LaserDevicePhase.READY && connectedId != state.deviceId) {
                    val current = transport.state.value.device ?: return@collect
                    connectedId = current.id
                    val name = current.name ?: appContext.getString(R.string.ble_default_device)
                    preferences.edit().putString("address", current.id).putString("name", name).apply()
                    listener.onConnected(name, current.id)
                } else if (state.phase != LaserDevicePhase.READY && connectedId != null) {
                    connectedId = null
                    listener.onDisconnected()
                }
                if (state.phase == LaserDevicePhase.FAILED) state.failure?.let { report(BleException(it)) }
            }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            device.events.collect { event ->
                val item = event.measurement ?: return@collect
                listener.onMeasurement(MeasurementRecord(item, event.frame))
            }
        }
    }
    fun scanAndConnect() = startOperation {
        val devices = transport.scan { BoschLaserDevice.matches(it, savedAddress) }
        val matched = devices.firstOrNull { BoschLaserDevice.matches(it, savedAddress) }
        if (matched != null) device.connect(matched)
        else if (devices.isEmpty()) status(R.string.ble_scan_empty)
        else {
            status(R.string.ble_choose_device)
            listener.onScanCandidates(devices.map { ScanCandidate(it.name ?: appContext.getString(R.string.ble_unnamed),
                it.id, it.rssi ?: 0, it.advertisedServices) })
        }
    }
    fun connectSaved(): Boolean {
        val address = savedAddress ?: return false
        connectAddress(address, savedName)
        return true
    }
    fun connectAddress(address: String) = connectAddress(address, null)
    private fun connectAddress(address: String, name: String?) = startOperation { device.connect(BleDevice(address, name)) }
    fun disconnect() {
        operation?.cancel()
        transport.close()
    }
    fun send(payload: ByteArray): Boolean {
        if (!isConnected || closed) { report(BleException(BleFailure.NOT_CONNECTED)); return false }
        scope.launch { perform { device.send(payload.copyOf()) } }
        return true
    }
    fun close() {
        closed = true
        disconnect()
        device.close()
        scope.cancel()
    }
    private fun startOperation(action: suspend () -> Unit) {
        if (closed) return
        val previous = operation
        previous?.cancel()
        transport.close()
        operation = scope.launch {
            previous?.join()
            perform(action)
        }
    }
    private suspend fun perform(action: suspend () -> Unit) {
        try { action() }
        catch (failure: CancellationException) { throw failure }
        catch (failure: BleException) { report(failure) }
        catch (failure: SecurityException) { report(BleException(BleFailure.PERMISSION)) }
    }
    private fun status(resource: Int) { listener.onConnectionStatus(appContext.getString(resource)) }
    private fun report(failure: BleException) {
        val resource = when (failure.reason) {
            BleFailure.PERMISSION -> R.string.ble_permission_required
            BleFailure.DISABLED -> R.string.ble_disabled
            BleFailure.SCAN -> R.string.ble_scan_failed
            BleFailure.CONNECT -> R.string.ble_connect_failed
            BleFailure.SERVICE -> R.string.ble_service_missing
            BleFailure.SUBSCRIBE -> R.string.ble_subscribe_failed
            BleFailure.WRITE -> R.string.ble_write_failed
            BleFailure.TIMEOUT -> R.string.ble_timeout
            BleFailure.INVALID_DEVICE -> R.string.ble_invalid_device
            BleFailure.OVERFLOW -> R.string.ble_notification_overflow
            BleFailure.DISCONNECTED, BleFailure.NOT_CONNECTED -> R.string.ble_not_connected
        }
        listener.onError(appContext.getString(resource))
    }
}
