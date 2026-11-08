package com.example.boschlaser.ui

import com.example.boschlaser.shared.ble.BlePhase
import com.example.boschlaser.shared.ble.BleState
import kotlinx.coroutines.flow.*

/** Queue entries retain their originating session; reconnects cannot relabel buffered old bytes. */
internal class IosNotificationChannel(private val state: StateFlow<BleState>) {
    private data class Chunk(val session: Long, val bytes: ByteArray)
    private val chunks = MutableSharedFlow<Chunk>(extraBufferCapacity = 64)
    val flow: Flow<ByteArray> = chunks.filter {
        state.value.phase == BlePhase.READY && state.value.sessionId == it.session
    }.map { it.bytes.copyOf() }
    fun emit(bytes: ByteArray): Boolean = chunks.tryEmit(Chunk(state.value.sessionId, bytes.copyOf()))
}
