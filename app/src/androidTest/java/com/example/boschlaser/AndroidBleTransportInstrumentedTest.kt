package com.example.boschlaser

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.boschlaser.platform.AndroidBleTransport
import com.example.boschlaser.shared.ble.BleDevice
import com.example.boschlaser.shared.ble.BleException
import com.example.boschlaser.shared.ble.BleFailure
import com.example.boschlaser.shared.ble.BlePhase
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidBleTransportInstrumentedTest {
    private fun deniedContext(): Context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        override fun getApplicationContext(): Context = this
        override fun checkSelfPermission(permission: String): Int = PackageManager.PERMISSION_DENIED
    }
    @Test fun deniedScanFailsBeforeStartingPlatformScanner() = runBlocking {
        val transport = AndroidBleTransport(deniedContext())
        try { transport.scan(); fail("Expected permission failure") }
        catch (failure: BleException) { assertEquals(BleFailure.PERMISSION, failure.reason) }
        assertEquals(BlePhase.DISCONNECTED, transport.state.value.phase)
        transport.disconnect()
    }
    @Test fun deniedConnectFailsBeforeOpeningGattAndCanBeClosed() = runBlocking {
        val transport = AndroidBleTransport(deniedContext())
        try { transport.connect(BleDevice("test-device")); fail("Expected permission failure") }
        catch (failure: BleException) { assertEquals(BleFailure.PERMISSION, failure.reason) }
        assertEquals(BlePhase.DISCONNECTED, transport.state.value.phase)
        transport.disconnect()
        assertEquals(BlePhase.DISCONNECTED, transport.state.value.phase)
    }
    @Test fun permissionFailureInCallbackFailsEveryPendingOperationAndInvalidatesSession() = runBlocking {
        val transport = AndroidBleTransport(deniedContext())
        val session = transport.state.value.sessionId
        supervisorScope {
            val fields = listOf("scanContinuation", "connectContinuation", "subscribeContinuation", "writeContinuation")
            val waiting = fields.map { name ->
                async(start = CoroutineStart.UNDISPATCHED) {
                    suspendCancellableCoroutine<Unit> { continuation ->
                        AndroidBleTransport::class.java.getDeclaredField(name).apply { isAccessible = true }
                            .set(transport, continuation)
                    }
                }
            }
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                transport.handleCallback { throw SecurityException("Synthetic permission revocation") }
            }
            for (operation in waiting) {
                try { withTimeout(1000) { operation.await() }; fail("Pending operation must fail") }
                catch (failure: BleException) { assertEquals(BleFailure.PERMISSION, failure.reason) }
            }
            assertEquals(BlePhase.FAILED, transport.state.value.phase)
            assertEquals(BleFailure.PERMISSION, transport.state.value.failure)
            assertTrue(transport.state.value.sessionId > session)
            for (name in fields) {
                assertEquals(null, AndroidBleTransport::class.java.getDeclaredField(name)
                    .apply { isAccessible = true }.get(transport))
            }
        }
        transport.disconnect()
        assertEquals(BlePhase.DISCONNECTED, transport.state.value.phase)
    }

    @Test fun permissionRevocationDuringSubscriptionAndWriteBecomesTypedFailure() = runBlocking {
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getApplicationContext(): Context = this
            override fun checkSelfPermission(permission: String): Int = throw SecurityException("Synthetic revoked permission")
        }
        val transport = AndroidBleTransport(context)
        val uuid = "00000000-0000-0000-0000-000000000001"
        try { transport.subscribe(uuid, uuid); fail("Expected subscription permission failure") }
        catch (failure: BleException) { assertEquals(BleFailure.PERMISSION, failure.reason) }
        assertEquals(BlePhase.FAILED, transport.state.value.phase)
        transport.disconnect()
        try { transport.write(uuid, uuid, byteArrayOf(1)); fail("Expected write permission failure") }
        catch (failure: BleException) { assertEquals(BleFailure.PERMISSION, failure.reason) }
        assertEquals(BlePhase.FAILED, transport.state.value.phase)
        transport.disconnect()
    }

}
