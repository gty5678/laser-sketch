package com.example.boschlaser.shared.measurement

import com.example.boschlaser.shared.ble.BleFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class LaserDevicePhase { DISCONNECTED, CONNECTING, READY, FAILED }
data class LaserDeviceState(val phase: LaserDevicePhase = LaserDevicePhase.DISCONNECTED,
    val deviceId: String? = null, val failure: BleFailure? = null)
interface LaserDevice {
    val state: StateFlow<LaserDeviceState>
    val measurements: Flow<Measurement>
    suspend fun measure()
    suspend fun startContinuous()
    suspend fun stopContinuous()
}
