@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.example.boschlaser.ui

import com.example.boschlaser.shared.ble.*
import com.example.boschlaser.shared.bosch.BoschLaserDevice
import com.example.boschlaser.shared.measurement.*
import com.example.boschlaser.shared.model.Project
import com.example.boschlaser.shared.storage.ProjectService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.*
import platform.UIKit.*

internal class IosAppServices(private val storageRoot: String? = null) {
    val preferences = storageRoot?.let { NSUserDefaults(suiteName = "LaserSketchUITest.${it.substringAfterLast('-')}") } ?: NSUserDefaults.standardUserDefaults
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val store = IosProjectStore(storageRoot?.let { "$it/projects" })
    val projects = ProjectService(IosProjectRepository(store), ::now, ::uuid)
    private val accepted = mutableSetOf<Job>()
    val transport = IosBleTransport()
    val device = BoschLaserDevice(transport, scope, ::now, { (NSProcessInfo.processInfo.systemUptime * 1000).toLong() }, ::uuid)
    val measurements = IosMeasurementRepository(storageRoot?.let { "$it/measurements.json" })
    val failure = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val connectionFailure = MutableStateFlow<BleFailure?>(null)
    private var wanted: BleDevice? = if (storageRoot == null) preferences.stringForKey("laserDevice")?.let { BleDevice(it) } else null
    val connectionRequested = MutableStateFlow(wanted != null)
    private var foreground = true
    private var reconnect: Job? = null
    private var scanJob: Job? = null
    private val connectionActions = Mutex()
    private val observers = mutableListOf<Any>()
    init {
        storageRoot?.let { check(NSFileManager.defaultManager.createDirectoryAtPath(it, true, null, null)) }
        if (storageRoot == null) {
            val documents = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String
            check(NSFileManager.defaultManager.createDirectoryAtPath("$documents/Exports", true, null, null))
        }
        scope.launch {
            try { measurements.refresh() } catch (_: Exception) { failure.value = label("storage_error") }
        }
        scope.launch {
            device.events.collect { event -> event.measurement?.let {
                try { measurements.save(MeasurementRecord(it, event.frame)) }
                catch (_: Exception) { failure.value = label("storage_error") }
            } }
        }
        observers += NSNotificationCenter.defaultCenter.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue) {
            foreground = false; scanJob?.cancel()
            scope.launch { connectionActions.withLock { stopReconnect(); device.disconnect() } }
        }
        observers += NSNotificationCenter.defaultCenter.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) {
            foreground = true
            scope.launch { connectionActions.withLock { startReconnect() } }
        }
        startReconnect()
    }
    fun run(action: suspend () -> Unit): Job = scope.launch {
        try { action() } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { failure.value = if (error is BleException) label("ble_${error.reason.name.lowercase()}") else label("operation_failed") }
    }
    /** Accepted saves survive page disposal; a background task grants time on app suspension. */
    fun persist(action: suspend () -> Project, onSaved: (Project) -> Unit) {
        lateinit var job: Job
        val background = UIApplication.sharedApplication.beginBackgroundTaskWithExpirationHandler { job.cancel() }
        job = scope.launch(start = CoroutineStart.LAZY) {
            try { onSaved(action()) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failure.value = label("storage_error") }
            finally { if (background != UIBackgroundTaskInvalid) UIApplication.sharedApplication.endBackgroundTask(background) }
        }
        accepted += job
        job.invokeOnCompletion { accepted.remove(job) }
        job.start()
    }
    private suspend fun stopReconnect() {
        val previous = reconnect
        reconnect = null
        previous?.cancelAndJoin()
    }
    fun scan() {
        scanJob?.cancel()
        scanJob = run { connectionActions.withLock {
            stopReconnect()
            device.disconnect()
            busy.value = true
            try { transport.scan() } finally { busy.value = false }
        } }
    }
    fun connect(target: BleDevice) {
        scanJob?.cancel()
        run { connectionActions.withLock {
            stopReconnect()
            device.disconnect()
            wanted = target
            connectionRequested.value = true
            preferences.setObject(target.id, "laserDevice")
            startReconnect()
        } }
    }
    fun disconnect() {
        scanJob?.cancel()
        run { connectionActions.withLock {
            stopReconnect()
            connectionFailure.value = null
            wanted = null
            connectionRequested.value = false
            preferences.removeObjectForKey("laserDevice")
            device.disconnect()
        } }
    }
    private fun startReconnect() {
        if (reconnect?.isActive == true || !foreground || wanted == null) return
        reconnect = scope.launch {
            var attempt = 0
            while (isActive && foreground && wanted != null) {
                if (device.state.value.phase != LaserDevicePhase.READY) {
                    busy.value = true
                    try { device.connect(checkNotNull(wanted)); attempt = 0; connectionFailure.value = null }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { attempt++; connectionFailure.value = (error as? BleException)?.reason ?: BleFailure.CONNECT }
                    finally { busy.value = false }
                }
                delay(if (attempt > 2) 10_000 else 2_000)
            }
        }
    }
    fun close() {
        observers.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }; observers.clear()
        scanJob?.cancel()
        scope.launch {
            try { accepted.toList().joinAll(); connectionActions.withLock { stopReconnect(); device.disconnect() } }
            finally { device.close(); scope.cancel() }
        }
    }
}
