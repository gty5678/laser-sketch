@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.example.boschlaser.ui

import com.example.boschlaser.shared.ble.*
import com.example.boschlaser.shared.bosch.BoschProtocol
import kotlinx.coroutines.*
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.CoreBluetooth.*
import platform.Foundation.*
import platform.darwin.NSObject

/** All GATT and delegate state is main-queue confined. A new central isolates each connection session. */
internal class IosBleTransport : BleTransport {
    private val delegate = Delegate()
    private val mutableState = MutableStateFlow(BleState())
    override val state = mutableState.asStateFlow()
    private val chunks = IosNotificationChannel(state)
    override val notifications = chunks.flow
    val found = MutableStateFlow<List<BleDevice>>(emptyList())
    private val power = MutableStateFlow(CBManagerStateUnknown)
    private var manager: CBCentralManager? = null
    private var peripheral: CBPeripheral? = null
    private val characteristics = mutableMapOf<String, CBCharacteristic>()
    private var connection: CompletableDeferred<Unit>? = null
    private var subscription: CompletableDeferred<Unit>? = null
    private var writing: CompletableDeferred<Unit>? = null
    private var scanStop: ((BleDevice) -> Boolean)? = null
    private var scanning: CompletableDeferred<Unit>? = null
    private val operations = Mutex()
    private var session = 0L

    private fun fail(reason: BleFailure) {
        val error = BleException(reason)
        connection?.completeExceptionally(error); subscription?.completeExceptionally(error)
        writing?.completeExceptionally(error); scanning?.completeExceptionally(error)
        mutableState.value = state.value.copy(phase = BlePhase.FAILED, failure = reason)
    }
    private fun release() {
        manager?.stopScan()
        peripheral?.let { it.delegate = null; manager?.cancelPeripheralConnection(it) }
        manager?.delegate = null
        manager = null; peripheral = null; characteristics.clear()
        val error = BleException(BleFailure.DISCONNECTED)
        connection?.completeExceptionally(error); subscription?.completeExceptionally(error)
        writing?.completeExceptionally(error); scanning?.completeExceptionally(error)
        connection = null; subscription = null; writing = null; scanning = null; scanStop = null
        session++
        mutableState.value = BleState(sessionId = session)
    }
    private suspend fun central(): CBCentralManager {
        if (manager == null) {
            power.value = CBManagerStateUnknown
            manager = CBCentralManager(delegate, null)
        }
        val ready = withTimeout(10_000) { power.first { it != CBManagerStateUnknown && it != CBManagerStateResetting } }
        if (ready != CBManagerStatePoweredOn) throw BleException(
            if (ready == CBManagerStateUnauthorized) BleFailure.PERMISSION else BleFailure.DISABLED)
        return checkNotNull(manager)
    }
    override suspend fun scan(stopWhen: (BleDevice) -> Boolean): List<BleDevice> = withContext(Dispatchers.Main) {
        operations.withLock {
            if (peripheral != null) throw BleException(BleFailure.CONNECT)
            found.value = emptyList()
            try {
                val central = central()
                val completion = CompletableDeferred<Unit>()
                scanning = completion; scanStop = stopWhen
                mutableState.value = BleState(BlePhase.SCANNING, sessionId = session)
                central.scanForPeripheralsWithServices(null, null)
                withTimeoutOrNull(8_000) { completion.await() }
                central.stopScan(); scanning = null; scanStop = null
                mutableState.value = BleState(sessionId = session)
                found.value
            } catch (error: Throwable) {
                manager?.stopScan(); scanning = null; scanStop = null
                if (error !is CancellationException) fail((error as? BleException)?.reason ?: BleFailure.SCAN)
                else mutableState.value = BleState(sessionId = session)
                throw error
            }
        }
    }
    override suspend fun connect(device: BleDevice) = withContext(Dispatchers.Main) {
        operations.withLock {
            release()
            try {
                val central = central()
                val id = NSUUID(uUIDString = device.id) ?: throw BleException(BleFailure.INVALID_DEVICE)
                val target = central.retrievePeripheralsWithIdentifiers(listOf(id)).firstOrNull() as? CBPeripheral
                    ?: throw BleException(BleFailure.INVALID_DEVICE)
                peripheral = target; target.delegate = delegate
                mutableState.value = BleState(BlePhase.CONNECTING, device, sessionId = session)
                val completion = CompletableDeferred<Unit>(); connection = completion
                central.connectPeripheral(target, null)
                withTimeout(15_000) { completion.await() }
                connection = null
            } catch (error: Throwable) {
                release()
                val reason = if (error is TimeoutCancellationException) BleFailure.TIMEOUT else (error as? BleException)?.reason ?: BleFailure.CONNECT
                fail(reason)
                throw if (error is CancellationException && error !is TimeoutCancellationException) error else BleException(reason)
            }
        }
    }
    override suspend fun disconnect() = withContext(Dispatchers.Main) { release() }
    private fun characteristic(service: String, characteristic: String): CBCharacteristic {
        val value = characteristics[characteristic.uppercase()] ?: throw BleException(BleFailure.SERVICE)
        if (!value.service?.UUID?.UUIDString.equals(service, true)) throw BleException(BleFailure.SERVICE)
        return value
    }
    override suspend fun subscribe(serviceUuid: String, characteristicUuid: String) = withContext(Dispatchers.Main) {
        operations.withLock {
            val target = peripheral ?: throw BleException(BleFailure.NOT_CONNECTED)
            val value = characteristic(serviceUuid, characteristicUuid)
            val completion = CompletableDeferred<Unit>(); subscription = completion
            mutableState.value = state.value.copy(phase = BlePhase.SUBSCRIBING)
            try {
                target.setNotifyValue(true, value)
                withTimeout(8_000) { completion.await() }
                mutableState.value = state.value.copy(phase = BlePhase.READY)
            } catch (error: Throwable) {
                release(); fail(if (error is TimeoutCancellationException) BleFailure.TIMEOUT else BleFailure.SUBSCRIBE)
                throw error
            } finally { subscription = null }
        }
    }
    override suspend fun write(serviceUuid: String, characteristicUuid: String, data: ByteArray) = withContext(Dispatchers.Main) {
        operations.withLock {
            val target = peripheral ?: throw BleException(BleFailure.NOT_CONNECTED)
            if (state.value.phase != BlePhase.READY) throw BleException(BleFailure.NOT_CONNECTED)
            val value = characteristic(serviceUuid, characteristicUuid)
            require(data.isNotEmpty() && data.size.toULong() <= target.maximumWriteValueLengthForType(CBCharacteristicWriteWithResponse))
            val completion = CompletableDeferred<Unit>(); writing = completion
            try {
                target.writeValue(data.nsData(), value, CBCharacteristicWriteWithResponse)
                withTimeout(8_000) { completion.await() }
            } catch (error: Throwable) {
                release(); fail(if (error is TimeoutCancellationException) BleFailure.TIMEOUT else BleFailure.WRITE)
                throw error
            } finally { writing = null }
        }
    }
    private inner class Delegate : NSObject(), CBCentralManagerDelegateProtocol, CBPeripheralDelegateProtocol {
        override fun centralManagerDidUpdateState(central: CBCentralManager) {
            if (central != manager) return
            power.value = central.state
            if (central.state != CBManagerStatePoweredOn && central.state != CBManagerStateUnknown && central.state != CBManagerStateResetting) {
                fail(if (central.state == CBManagerStateUnauthorized) BleFailure.PERMISSION else BleFailure.DISABLED)
            }
        }
        override fun centralManager(central: CBCentralManager, didDiscoverPeripheral: CBPeripheral, advertisementData: Map<Any?, *>, RSSI: NSNumber) {
            if (central != manager || state.value.phase != BlePhase.SCANNING) return
            val services = (advertisementData[CBAdvertisementDataServiceUUIDsKey] as? List<*>)
                ?.filterIsInstance<CBUUID>()?.map { it.UUIDString }.orEmpty()
            val device = BleDevice(didDiscoverPeripheral.identifier.UUIDString,
                advertisementData[CBAdvertisementDataLocalNameKey] as? String ?: didDiscoverPeripheral.name, RSSI.intValue, services)
            if (!com.example.boschlaser.shared.bosch.BoschLaserDevice.matches(device)) return
            found.value = found.value.filterNot { it.id == device.id } + device
            try {
                if (scanStop?.invoke(device) == true) scanning?.complete(Unit)
            } catch (_: Exception) { scanning?.completeExceptionally(BleException(BleFailure.SCAN)) }
        }
        override fun centralManager(central: CBCentralManager, didConnectPeripheral: CBPeripheral) {
            if (central != manager || peripheral != didConnectPeripheral) return
            mutableState.value = state.value.copy(phase = BlePhase.DISCOVERING)
            didConnectPeripheral.discoverServices(listOf(CBUUID.UUIDWithString(BoschProtocol.SERVICE_UUID)))
        }
        @ObjCSignatureOverride
        override fun centralManager(central: CBCentralManager, didFailToConnectPeripheral: CBPeripheral, error: NSError?) {
            if (central == manager && peripheral == didFailToConnectPeripheral) fail(BleFailure.CONNECT)
        }
        @ObjCSignatureOverride
        override fun centralManager(central: CBCentralManager, didDisconnectPeripheral: CBPeripheral, error: NSError?) {
            if (central != manager || peripheral != didDisconnectPeripheral) return
            fail(BleFailure.DISCONNECTED)
            peripheral?.delegate = null; peripheral = null; characteristics.clear()
        }
        override fun peripheral(peripheral: CBPeripheral, didDiscoverServices: NSError?) {
            if (peripheral != this@IosBleTransport.peripheral) return
            val service = peripheral.services?.filterIsInstance<CBService>()?.firstOrNull { it.UUID.UUIDString.equals(BoschProtocol.SERVICE_UUID, true) }
            if (didDiscoverServices != null || service == null) { fail(BleFailure.SERVICE); return }
            peripheral.discoverCharacteristics(listOf(CBUUID.UUIDWithString(BoschProtocol.CHARACTERISTIC_UUID)), service)
        }
        override fun peripheral(peripheral: CBPeripheral, didDiscoverCharacteristicsForService: CBService, error: NSError?) {
            if (peripheral != this@IosBleTransport.peripheral) return
            val values = didDiscoverCharacteristicsForService.characteristics?.filterIsInstance<CBCharacteristic>().orEmpty()
            values.forEach { characteristics[it.UUID.UUIDString.uppercase()] = it }
            if (error != null || characteristics[BoschProtocol.CHARACTERISTIC_UUID.uppercase()] == null) { fail(BleFailure.SERVICE); return }
            mutableState.value = state.value.copy(phase = BlePhase.CONNECTED)
            connection?.complete(Unit)
        }
        @ObjCSignatureOverride
        override fun peripheral(peripheral: CBPeripheral, didUpdateNotificationStateForCharacteristic: CBCharacteristic, error: NSError?) {
            if (peripheral != this@IosBleTransport.peripheral || didUpdateNotificationStateForCharacteristic != characteristics[BoschProtocol.CHARACTERISTIC_UUID.uppercase()]) return
            if (error == null && didUpdateNotificationStateForCharacteristic.isNotifying) subscription?.complete(Unit)
            else subscription?.completeExceptionally(BleException(BleFailure.SUBSCRIBE))
        }
        @ObjCSignatureOverride
        override fun peripheral(peripheral: CBPeripheral, didWriteValueForCharacteristic: CBCharacteristic, error: NSError?) {
            if (peripheral != this@IosBleTransport.peripheral || didWriteValueForCharacteristic != characteristics[BoschProtocol.CHARACTERISTIC_UUID.uppercase()]) return
            if (error == null) writing?.complete(Unit) else writing?.completeExceptionally(BleException(BleFailure.WRITE))
        }
        @ObjCSignatureOverride
        override fun peripheral(peripheral: CBPeripheral, didUpdateValueForCharacteristic: CBCharacteristic, error: NSError?) {
            if (peripheral != this@IosBleTransport.peripheral || state.value.phase != BlePhase.READY ||
                didUpdateValueForCharacteristic != characteristics[BoschProtocol.CHARACTERISTIC_UUID.uppercase()]) return
            if (error != null) { fail(BleFailure.DISCONNECTED); return }
            val data = didUpdateValueForCharacteristic.value ?: return
            if (data.length > 64uL * 1024uL) { manager?.cancelPeripheralConnection(peripheral); fail(BleFailure.OVERFLOW); return }
            val bytes = data.byteArray()
            if (!chunks.emit(bytes)) { manager?.cancelPeripheralConnection(peripheral); fail(BleFailure.OVERFLOW) }
        }
    }
}
