package com.example.boschlaser

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.ArrayDeque
import java.util.UUID

class BoschBleManager(context: Context, private val listener: Listener) {
    data class ScanCandidate(
        val name: String,
        val address: String,
        val rssi: Int,
        val advertisedServices: List<String>,
    ) {
        val displayText: String get() = "$name  ·  $address  ·  ${rssi} dBm"
    }

    interface Listener {
        fun onConnectionStatus(message: String)
        fun onConnected(name: String, address: String)
        fun onDisconnected()
        fun onFrame(frame: ByteArray)
        fun onScanCandidates(devices: List<ScanCandidate>)
        fun onError(message: String)
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val serviceUuid = UUID.fromString(BoschProtocol.SERVICE_UUID)
    private val characteristicUuid = UUID.fromString(BoschProtocol.CHARACTERISTIC_UUID)
    private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private val assembler = MtFrameAssembler()
    private var gatt: BluetoothGatt? = null
    private var characteristic: BluetoothGattCharacteristic? = null
    private var scanning = false
    private var ready = false
    private val writeQueue = ArrayDeque<ByteArray>()
    private var writeInProgress = false
    private val scanCandidates = linkedMapOf<String, ScanCandidate>()

    private val preferences = appContext.getSharedPreferences("bosch_glm", Context.MODE_PRIVATE)
    val savedAddress: String? get() = preferences.getString("address", null)
    val savedName: String? get() = preferences.getString("name", null)
    val isConnected: Boolean get() = ready && gatt != null

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val advertisedServices = result.scanRecord?.serviceUuids.orEmpty()
            val name = result.scanRecord?.deviceName ?: result.device.name.orEmpty()
            val address = result.device.address
            val likelyGlm = advertisedServices.any { it.uuid == serviceUuid } ||
                name.contains("GLM", true) || name.contains("BOSCH", true) ||
                address.equals(savedAddress, true)
            val candidate = ScanCandidate(
                name.ifBlank { "未命名BLE设备" },
                address,
                result.rssi,
                advertisedServices.map { it.uuid.toString() },
            )
            scanCandidates[address] = candidate
            Log.d("BoschBLE", "scan name=${candidate.name} address=$address rssi=${result.rssi} services=${candidate.advertisedServices}")
            if (likelyGlm) {
                Log.i("BoschBLE", "Bosch GLM candidate matched: ${candidate.displayText}")
                stopScan()
                connect(result.device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            postError("扫描失败，Android错误码：$errorCode")
        }
    }

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(currentGatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                postStatus("蓝牙已连接，正在发现Bosch服务…")
                if (!currentGatt.discoverServices()) failAndClose("无法开始发现GATT服务")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (closeGatt(currentGatt)) {
                    mainHandler.post { listener.onDisconnected() }
                }
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                failAndClose("连接失败，GATT状态：$status")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(currentGatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failAndClose("发现服务失败，GATT状态：$status")
                return
            }
            val found = currentGatt.getService(serviceUuid)?.getCharacteristic(characteristicUuid)
            if (found == null) {
                failAndClose("设备没有Bosch GLM通信特征")
                return
            }
            characteristic = found
            if (!currentGatt.setCharacteristicNotification(found, true)) {
                failAndClose("无法启用测量通知")
                return
            }
            val descriptor = found.getDescriptor(cccdUuid)
            if (descriptor == null) {
                failAndClose("Bosch通信特征没有通知描述符")
                return
            }
            val useIndication = found.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
            val cccdValue = if (useIndication) {
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            } else {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            }
            postStatus("已找到Bosch服务，正在订阅测量通知…")
            if (!currentGatt.writeDescriptor(descriptor, cccdValue)) failAndClose("写入通知描述符失败")
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(currentGatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.uuid != cccdUuid) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failAndClose("启用通知失败，GATT状态：$status")
                return
            }
            ready = true
            val device = currentGatt.device
            val name = try { device.name ?: "Bosch GLM 50-27 CG" } catch (_: SecurityException) { "Bosch GLM 50-27 CG" }
            preferences.edit().putString("address", device.address).putString("name", name).apply()
            mainHandler.post { listener.onConnected(name, device.address) }
            enqueueWrite(BoschProtocol.enableMeasurements)
        }

        override fun onCharacteristicChanged(
            currentGatt: BluetoothGatt,
            changed: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            consumeChunk(value)
        }

        @Deprecated("Called on Android 12")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(currentGatt: BluetoothGatt, changed: BluetoothGattCharacteristic) {
            consumeChunk(changed.value?.clone() ?: return)
        }

        override fun onCharacteristicWrite(currentGatt: BluetoothGatt, changed: BluetoothGattCharacteristic, status: Int) {
            synchronized(writeQueue) {
                writeInProgress = false
            }
            if (status != BluetoothGatt.GATT_SUCCESS) postError("蓝牙写入失败，GATT状态：$status")
            writeNext()
        }
    }

    @SuppressLint("MissingPermission")
    fun scanAndConnect() {
        disconnect()
        val bluetoothAdapter = adapter
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            postError("请先打开手机蓝牙")
            return
        }
        val scanner = bluetoothAdapter.bluetoothLeScanner ?: run {
            postError("无法取得BLE扫描器")
            return
        }
        postStatus("正在扫描Bosch GLM…")
        scanCandidates.clear()
        scanning = true
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        // GLM 50-27 CG does not expose its 128-bit service UUID consistently in
        // Android scan records. Scan without a controller-level filter, then
        // identify it by service, name or the previously saved address here.
        scanner.startScan(null, settings, scanCallback)
        mainHandler.postDelayed({
            if (scanning) {
                stopScan()
                val candidates = scanCandidates.values.sortedByDescending { it.rssi }
                if (candidates.isEmpty()) {
                    postError("12秒内没有发现任何BLE设备，请检查附近设备权限和手机蓝牙")
                } else {
                    postStatus("没有自动识别到GLM，请从附近设备中选择")
                    mainHandler.post { listener.onScanCandidates(candidates) }
                }
            }
        }, 12_000)
    }

    @SuppressLint("MissingPermission")
    fun connectSaved(): Boolean {
        val address = savedAddress ?: return false
        if (adapter?.isEnabled != true) {
            postError("手机蓝牙未打开，等待打开后自动重连")
            return false
        }
        val device = try { adapter?.getRemoteDevice(address) } catch (_: IllegalArgumentException) { null }
        if (device == null) {
            postError("保存的设备地址无效，请重新扫描")
            return false
        }
        disconnect()
        postStatus("正在直接连接上次设备：${savedName ?: address}…")
        connect(device)
        return true
    }

    @SuppressLint("MissingPermission")
    fun connectAddress(address: String) {
        val device = try { adapter?.getRemoteDevice(address) } catch (_: IllegalArgumentException) { null }
        if (device == null) {
            postError("设备地址无效：$address")
            return
        }
        stopScan()
        disconnect()
        connect(device)
    }

    @SuppressLint("MissingPermission")
    private fun connect(device: android.bluetooth.BluetoothDevice) {
        assembler.reset()
        ready = false
        postStatus("发现GLM，正在连接 ${device.address}…")
        gatt = device.connectGatt(
            appContext,
            false,
            callback,
            android.bluetooth.BluetoothDevice.TRANSPORT_LE,
            android.bluetooth.BluetoothDevice.PHY_LE_1M_MASK,
            null,
        )
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!scanning) return
        scanning = false
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopScan()
        ready = false
        characteristic = null
        assembler.reset()
        synchronized(writeQueue) {
            writeQueue.clear()
            writeInProgress = false
        }
        val oldGatt = gatt
        gatt = null
        oldGatt?.disconnect()
        oldGatt?.close()
    }

    fun send(payload: ByteArray): Boolean {
        if (!isConnected) {
            postError("GLM尚未连接")
            return false
        }
        enqueueWrite(payload)
        return true
    }

    private fun enqueueWrite(payload: ByteArray) {
        synchronized(writeQueue) { writeQueue.add(payload.clone()) }
        writeNext()
    }

    @SuppressLint("MissingPermission")
    private fun writeNext() {
        val currentGatt = gatt ?: return
        val currentCharacteristic = characteristic ?: return
        val payload = synchronized(writeQueue) {
            if (writeInProgress || writeQueue.isEmpty()) return
            writeInProgress = true
            writeQueue.removeFirst()
        }
        if (!currentGatt.writeCharacteristic(
                currentCharacteristic,
                payload,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            )
        ) {
            synchronized(writeQueue) { writeInProgress = false }
            postError("Android未接受蓝牙写入请求")
            writeNext()
        }
    }

    private fun consumeChunk(chunk: ByteArray) {
        for (frame in assembler.feed(chunk)) {
            mainHandler.post { listener.onFrame(frame) }
        }
    }

    private fun BluetoothGatt.writeDescriptor(
        descriptor: BluetoothGattDescriptor,
        value: ByteArray,
    ): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        writeDescriptor(descriptor, value) == android.bluetooth.BluetoothStatusCodes.SUCCESS
    } else {
        writeDescriptorLegacy(descriptor, value)
    }

    @Suppress("DEPRECATION")
    private fun BluetoothGatt.writeDescriptorLegacy(
        descriptor: BluetoothGattDescriptor,
        value: ByteArray,
    ): Boolean {
        descriptor.value = value
        return writeDescriptor(descriptor)
    }

    private fun BluetoothGatt.writeCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int,
    ): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        writeCharacteristic(characteristic, value, writeType) == android.bluetooth.BluetoothStatusCodes.SUCCESS
    } else {
        writeCharacteristicLegacy(characteristic, value, writeType)
    }

    @Suppress("DEPRECATION")
    private fun BluetoothGatt.writeCharacteristicLegacy(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int,
    ): Boolean {
        characteristic.writeType = writeType
        characteristic.value = value
        return writeCharacteristic(characteristic)
    }

    @SuppressLint("MissingPermission")
    private fun failAndClose(message: String) {
        val oldGatt = gatt
        ready = false
        characteristic = null
        gatt = null
        oldGatt?.disconnect()
        oldGatt?.close()
        postError(message)
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt(currentGatt: BluetoothGatt): Boolean {
        val wasCurrent = gatt === currentGatt
        if (wasCurrent) {
            gatt = null
            ready = false
            characteristic = null
            synchronized(writeQueue) {
                writeQueue.clear()
                writeInProgress = false
            }
        }
        currentGatt.close()
        return wasCurrent
    }

    private fun postStatus(message: String) = mainHandler.post { listener.onConnectionStatus(message) }
    private fun postError(message: String) = mainHandler.post { listener.onError(message) }
}
