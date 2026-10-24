package com.example.boschlaser.shared.ble

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class BleDevice(val id: String, val name: String? = null, val rssi: Int? = null,
    val advertisedServices: List<String> = emptyList()) {
    init { require(id.isNotBlank()) }
}
enum class BlePhase { DISCONNECTED, SCANNING, CONNECTING, DISCOVERING, CONNECTED, SUBSCRIBING, READY, FAILED }
enum class BleFailure { PERMISSION, DISABLED, SCAN, CONNECT, SERVICE, SUBSCRIBE, WRITE, DISCONNECTED, TIMEOUT, NOT_CONNECTED, INVALID_DEVICE, OVERFLOW }
data class BleState(val phase: BlePhase = BlePhase.DISCONNECTED, val device: BleDevice? = null,
    val failure: BleFailure? = null, val platformCode: Int? = null, val sessionId: Long = 0L)
class BleException(val reason: BleFailure, val platformCode: Int? = null) : Exception(reason.name)

/** Transport carries raw notification chunks and completes writes only after platform acknowledgement. */
interface BleTransport {
    val state: StateFlow<BleState>
    val notifications: Flow<ByteArray>
    suspend fun scan(stopWhen: (BleDevice) -> Boolean = { false }): List<BleDevice>
    suspend fun connect(device: BleDevice)
    suspend fun disconnect()
    suspend fun write(serviceUuid: String, characteristicUuid: String, data: ByteArray)
    suspend fun subscribe(serviceUuid: String, characteristicUuid: String)
}
