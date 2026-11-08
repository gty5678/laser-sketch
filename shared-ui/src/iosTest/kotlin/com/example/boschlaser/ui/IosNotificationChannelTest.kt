package com.example.boschlaser.ui

import com.example.boschlaser.shared.ble.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.test.*

class IosNotificationChannelTest {
    @Test fun reconnectDropsQueuedOldSessionChunksWithoutRelabelingThem() = runBlocking {
        val state = MutableStateFlow(BleState(BlePhase.READY, sessionId = 1))
        val channel = IosNotificationChannel(state)
        val firstReceived = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val collected = async(start = CoroutineStart.UNDISPATCHED) {
            val bytes = mutableListOf<ByteArray>()
            channel.flow.take(2).collect {
                bytes += it
                if (bytes.size == 1) { firstReceived.complete(Unit); release.await() }
            }
            bytes
        }
        channel.emit(byteArrayOf(1)); firstReceived.await()
        channel.emit(byteArrayOf(2))
        state.value = BleState(BlePhase.READY, sessionId = 2)
        val next = byteArrayOf(3); channel.emit(next); next[0] = 9
        release.complete(Unit)
        val output = collected.await()
        assertContentEquals(byteArrayOf(1), output[0])
        assertContentEquals(byteArrayOf(3), output[1])
    }
    @Test fun slowConsumerReportsOverflowRatherThanSilentlyDroppingBytes() = runBlocking {
        val state = MutableStateFlow(BleState(BlePhase.READY, sessionId = 1))
        val channel = IosNotificationChannel(state)
        val blocked = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            channel.flow.collect { blocked.complete(Unit); release.await() }
        }
        channel.emit(byteArrayOf(0)); blocked.await()
        repeat(64) { assertTrue(channel.emit(byteArrayOf(it.toByte()))) }
        assertFalse(channel.emit(byteArrayOf(99)))
        collector.cancelAndJoin()
    }
}
