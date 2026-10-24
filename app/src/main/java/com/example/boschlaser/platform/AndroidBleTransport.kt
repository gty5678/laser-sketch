package com.example.boschlaser.platform

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.example.boschlaser.shared.ble.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** All platform operations and callbacks run on Main. Stale GATT/scan callbacks are ignored. */
@SuppressLint("MissingPermission")
class AndroidBleTransport(context: Context) : BleTransport {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val adapter = (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val mutableState = MutableStateFlow(BleState())
    override val state = mutableState.asStateFlow()
    private data class Notification(val sessionId: Long, val data: ByteArray)
    private val chunks = MutableSharedFlow<Notification>(extraBufferCapacity = 64)
    override val notifications: Flow<ByteArray> = chunks.filter { it.sessionId == state.value.sessionId }.map { it.data }
    private var sessionId = 0L
    private var gatt: BluetoothGatt? = null
    private var scanCallback: ScanCallback? = null
    private var scanContinuation: CancellableContinuation<List<BleDevice>>? = null
    private var connectContinuation: CancellableContinuation<Unit>? = null
    private var subscribeContinuation: CancellableContinuation<Unit>? = null
    private var writeContinuation: CancellableContinuation<Unit>? = null
    private var subscribedCharacteristic: BluetoothGattCharacteristic? = null
    private var subscribingCharacteristic: BluetoothGattCharacteristic? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private val writes = Mutex()
    private val subscriptions = Mutex()
    private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(current: BluetoothGatt, status: Int, newState: Int) {
            if (gatt !== current) { try { current.close() } catch (_: SecurityException) { }; return }
            if (status != BluetoothGatt.GATT_SUCCESS) { fail(BleFailure.CONNECT, status); return }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    handleCallback {
                        mutableState.value = state.value.copy(phase = BlePhase.DISCOVERING)
                        if (!current.discoverServices()) fail(BleFailure.SERVICE)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    val device = state.value.device
                    close()
                    mutableState.value = BleState(BlePhase.DISCONNECTED, device, sessionId = sessionId)
                }
            }
        }
        override fun onServicesDiscovered(current: BluetoothGatt, status: Int) {
            if (gatt !== current) return
            if (status != BluetoothGatt.GATT_SUCCESS) { fail(BleFailure.SERVICE, status); return }
            mutableState.value = state.value.copy(phase = BlePhase.CONNECTED)
            connectContinuation?.let { connectContinuation = null; if (it.isActive) it.resume(Unit) }
        }
        override fun onDescriptorWrite(current: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (gatt !== current || descriptor.uuid != cccdUuid || descriptor.characteristic !== subscribingCharacteristic) return
            if (status != BluetoothGatt.GATT_SUCCESS) { fail(BleFailure.SUBSCRIBE, status); return }
            subscribedCharacteristic = subscribingCharacteristic
            subscribingCharacteristic = null
            mutableState.value = state.value.copy(phase = BlePhase.READY)
            subscribeContinuation?.let { subscribeContinuation = null; if (it.isActive) it.resume(Unit) }
        }
        override fun onCharacteristicWrite(current: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (gatt !== current || characteristic !== writeCharacteristic) return
            writeCharacteristic = null
            val continuation = writeContinuation ?: return
            writeContinuation = null
            if (continuation.isActive) {
                if (status == BluetoothGatt.GATT_SUCCESS) continuation.resume(Unit)
                else continuation.resumeWithException(BleException(BleFailure.WRITE, status))
            }
        }
        override fun onCharacteristicChanged(current: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            consume(current, characteristic, value)
        }
        @Deprecated("Android 12 callback")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(current: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (gatt !== current || characteristic !== subscribedCharacteristic) return
            handleCallback { consume(current, characteristic, characteristic.value ?: return@handleCallback) }
        }
    }
    /** Callback platform calls can lose permission after the initiating operation's check. */
    internal fun handleCallback(action: () -> Unit) {
        try { action() }
        catch (_: SecurityException) { fail(BleFailure.PERMISSION) }
    }

    private fun consume(current: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        if (gatt !== current || characteristic !== subscribedCharacteristic || state.value.phase != BlePhase.READY) return
        if (!chunks.tryEmit(Notification(sessionId, value.clone()))) fail(BleFailure.OVERFLOW)
    }
    private fun requireBluetooth(scan: Boolean = false) {
        val permissions = if (scan) listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
            else listOf(Manifest.permission.BLUETOOTH_CONNECT)
        if (permissions.any { appContext.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) throw BleException(BleFailure.PERMISSION)
        if (adapter?.isEnabled != true) throw BleException(BleFailure.DISABLED)
    }
    override suspend fun scan(stopWhen: (BleDevice) -> Boolean): List<BleDevice> = withContext(Dispatchers.Main.immediate) {
        close()
        requireBluetooth(scan = true)
        val scanner = adapter?.bluetoothLeScanner ?: throw BleException(BleFailure.DISABLED)
        val devices = linkedMapOf<String, BleDevice>()
        try {
            suspendCancellableCoroutine { continuation ->
                val active = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        if (scanCallback !== this) return
                        handleCallback {
                            val device = BleDevice(result.device.address, result.scanRecord?.deviceName ?: result.device.name,
                                result.rssi, result.scanRecord?.serviceUuids.orEmpty().map { it.uuid.toString() })
                            devices[device.id] = device
                            if (stopWhen(device)) finishScan(devices.values.toList())
                        }
                    }
                    override fun onScanFailed(errorCode: Int) {
                        if (scanCallback !== this) return
                        fail(BleFailure.SCAN, errorCode)
                    }
                }
                scanCallback = active
                scanContinuation = continuation
                mutableState.value = BleState(BlePhase.SCANNING, sessionId = sessionId)
                continuation.invokeOnCancellation { handler.post { if (scanCallback === active) stopScanning() } }
                try {
                    scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), active)
                    handler.postDelayed({ if (scanCallback === active) finishScan(devices.values.sortedByDescending { it.rssi }) }, 12_000)
                } catch (failure: SecurityException) { fail(BleFailure.PERMISSION) }
            }
        } finally {
            stopScanning()
            if (state.value.phase == BlePhase.SCANNING) mutableState.value = BleState(sessionId = sessionId)
        }
    }
    private fun finishScan(devices: List<BleDevice>) {
        val continuation = scanContinuation
        scanContinuation = null
        stopScanning()
        if (continuation?.isActive == true) continuation.resume(devices)
    }
    private fun stopScanning() {
        val active = scanCallback
        scanCallback = null
        if (active != null) try { adapter?.bluetoothLeScanner?.stopScan(active) } catch (_: SecurityException) { }
        scanContinuation?.cancel()
        scanContinuation = null
    }
    override suspend fun connect(device: BleDevice): Unit = withContext(Dispatchers.Main.immediate) {
        close()
        requireBluetooth()
        val remote = try { adapter?.getRemoteDevice(device.id) } catch (_: IllegalArgumentException) { null }
            ?: throw BleException(BleFailure.INVALID_DEVICE)
        mutableState.value = BleState(BlePhase.CONNECTING, device, sessionId = sessionId)
        try {
            withTimeout(10_000) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    connectContinuation = continuation
                    val current = remote.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE,
                        BluetoothDevice.PHY_LE_1M_MASK, handler)
                    gatt = current
                    continuation.invokeOnCancellation { handler.post { if (gatt === current) close() } }
                    if (current == null) fail(BleFailure.CONNECT)
                }
            }
        } catch (failure: TimeoutCancellationException) { fail(BleFailure.TIMEOUT); throw BleException(BleFailure.TIMEOUT) }
        catch (failure: SecurityException) { fail(BleFailure.PERMISSION); throw BleException(BleFailure.PERMISSION) }
    }
    override suspend fun subscribe(serviceUuid: String, characteristicUuid: String): Unit = withContext(Dispatchers.Main.immediate) {
        subscriptions.withLock {
            try {
                requireBluetooth()
                val current = gatt ?: throw BleException(BleFailure.NOT_CONNECTED)
                val found = current.getService(UUID.fromString(serviceUuid))?.getCharacteristic(UUID.fromString(characteristicUuid))
                    ?: throw BleException(BleFailure.SERVICE)
                val descriptor = found.getDescriptor(cccdUuid) ?: throw BleException(BleFailure.SUBSCRIBE)
                if (!current.setCharacteristicNotification(found, true)) throw BleException(BleFailure.SUBSCRIBE)
                val value = if (found.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0)
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                withTimeout(5_000) {
                    suspendCancellableCoroutine<Unit> { continuation ->
                        subscribeContinuation = continuation
                        subscribingCharacteristic = found
                        mutableState.value = state.value.copy(phase = BlePhase.SUBSCRIBING)
                        continuation.invokeOnCancellation { handler.post { if (gatt === current) close() } }
                        if (!current.writeDescriptorCompat(descriptor, value)) fail(BleFailure.SUBSCRIBE)
                    }
                }
            } catch (failure: TimeoutCancellationException) { fail(BleFailure.TIMEOUT); throw BleException(BleFailure.TIMEOUT) }
            catch (_: SecurityException) { fail(BleFailure.PERMISSION); throw BleException(BleFailure.PERMISSION) }
            catch (failure: BleException) {
                if (failure.reason == BleFailure.PERMISSION) fail(BleFailure.PERMISSION)
                throw failure
            }
        }
    }
    override suspend fun write(serviceUuid: String, characteristicUuid: String, data: ByteArray): Unit = withContext(Dispatchers.Main.immediate) {
        writes.withLock {
            try {
                requireBluetooth()
                val current = gatt ?: throw BleException(BleFailure.NOT_CONNECTED)
                val found = current.getService(UUID.fromString(serviceUuid))?.getCharacteristic(UUID.fromString(characteristicUuid))
                    ?: throw BleException(BleFailure.SERVICE)
                if (state.value.phase != BlePhase.READY) throw BleException(BleFailure.NOT_CONNECTED)
                withTimeout(5_000) {
                    suspendCancellableCoroutine<Unit> { continuation ->
                        writeContinuation = continuation
                        writeCharacteristic = found
                        continuation.invokeOnCancellation { handler.post { if (gatt === current) close() } }
                        if (!current.writeCharacteristicCompat(found, data.copyOf())) {
                            writeContinuation = null; writeCharacteristic = null
                            continuation.resumeWithException(BleException(BleFailure.WRITE))
                        }
                    }
                }
            } catch (failure: TimeoutCancellationException) { fail(BleFailure.TIMEOUT); throw BleException(BleFailure.TIMEOUT) }
            catch (_: SecurityException) { fail(BleFailure.PERMISSION); throw BleException(BleFailure.PERMISSION) }
            catch (failure: BleException) {
                if (failure.reason == BleFailure.PERMISSION) fail(BleFailure.PERMISSION)
                throw failure
            }
        }
    }
    override suspend fun disconnect() = withContext(Dispatchers.Main.immediate) { close() }
    /** UI lifecycle calls on Main; cleanup still works after permissions are revoked. */
    fun close() {
        sessionId++
        stopScanning()
        val current = gatt
        gatt = null
        subscribedCharacteristic = null; subscribingCharacteristic = null; writeCharacteristic = null
        val failure = BleException(BleFailure.DISCONNECTED)
        listOf(connectContinuation, subscribeContinuation, writeContinuation).forEach { if (it?.isActive == true) it.resumeWithException(failure) }
        connectContinuation = null; subscribeContinuation = null; writeContinuation = null
        try { current?.disconnect() } catch (_: SecurityException) { }
        try { current?.close() } catch (_: SecurityException) { }
        mutableState.value = BleState(sessionId = sessionId)
    }
    private fun fail(reason: BleFailure, code: Int? = null) {
        val device = state.value.device
        val failure = BleException(reason, code)
        listOf(connectContinuation, subscribeContinuation, writeContinuation).forEach { if (it?.isActive == true) it.resumeWithException(failure) }
        scanContinuation?.let { if (it.isActive) it.resumeWithException(failure) }
        scanContinuation = null; connectContinuation = null; subscribeContinuation = null; writeContinuation = null
        close()
        mutableState.value = BleState(BlePhase.FAILED, device, reason, code, sessionId)
    }
    private fun BluetoothGatt.writeDescriptorCompat(descriptor: BluetoothGattDescriptor, value: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= 33) writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
        else writeDescriptorLegacy(descriptor, value)
    @Suppress("DEPRECATION")
    private fun BluetoothGatt.writeDescriptorLegacy(descriptor: BluetoothGattDescriptor, value: ByteArray): Boolean {
        descriptor.value = value
        return writeDescriptor(descriptor)
    }
    private fun BluetoothGatt.writeCharacteristicCompat(characteristic: BluetoothGattCharacteristic, value: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= 33) writeCharacteristic(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
        else writeCharacteristicLegacy(characteristic, value)
    @Suppress("DEPRECATION")
    private fun BluetoothGatt.writeCharacteristicLegacy(characteristic: BluetoothGattCharacteristic, value: ByteArray): Boolean {
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        characteristic.value = value
        return writeCharacteristic(characteristic)
    }
}
