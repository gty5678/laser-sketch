package com.example.boschlaser

import android.content.Context
import android.net.Uri
import com.example.boschlaser.platform.AndroidMeasurementCsv
import com.example.boschlaser.platform.AndroidMeasurementRepository
import com.example.boschlaser.shared.measurement.MeasurementService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Application-owned acquisition/storage jobs survive screen navigation and hold no Activity. */
class MeasurementServices private constructor(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val csv = AndroidMeasurementCsv(context.applicationContext)
    val service = MeasurementService(AndroidMeasurementRepository(context.applicationContext))
    private val failures = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val errors = failures.asSharedFlow()
    init { enqueue { refresh() } }
    fun enqueue(action: suspend MeasurementService.() -> Unit): Job = scope.launch {
        try { service.action() }
        catch (failure: CancellationException) { throw failure }
        catch (_: Exception) { failures.emit(Unit) }
    }
    /** Capture the full immutable history at acceptance; screen cancellation cannot cancel the write. */
    fun exportCsv(uri: Uri): Deferred<Unit> {
        val snapshot = service.records.value.toList()
        return scope.async { csv.export(uri, snapshot) }
    }
    companion object {
        @Volatile private var instance: MeasurementServices? = null
        fun get(context: Context): MeasurementServices = instance ?: synchronized(this) {
            instance ?: MeasurementServices(context.applicationContext).also { instance = it }
        }
    }
}
