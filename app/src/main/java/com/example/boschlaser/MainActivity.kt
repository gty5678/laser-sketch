package com.example.boschlaser

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.colorResource
import androidx.compose.runtime.Composable
import com.example.boschlaser.ui.HomeScreen
import com.example.boschlaser.ui.HomeLabels
import com.example.boschlaser.ui.HomeStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.example.boschlaser.ui.AndroidMeasurements
import com.example.boschlaser.ui.AndroidProjectList
import com.example.boschlaser.ui.ProjectLabels
import com.example.boschlaser.ui.DeviceLabels
import com.example.boschlaser.ui.DeviceScreen
import com.example.boschlaser.ui.DeviceScreenState
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import com.example.boschlaser.shared.measurement.MeasurementRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), BoschBleManager.Listener {
    private lateinit var ble: BoschBleManager
    private var showDevice by mutableStateOf(false)
    private var deviceUi by mutableStateOf(DeviceScreenState("", ""))

    private val measurementScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val projectServices by lazy { ProjectServices.get(this) }
    private var projectJob: Job? = null
    private var pendingExportProjectId: String? = null
    private val measurementServices by lazy { MeasurementServices.get(this) }
    private val measurements: List<Measurement>
        get() = measurementServices.service.records.value.map { it.toLegacyMeasurement(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var autoReconnectEnabled = true
    private var reconnectAttempt = 0

    private val reconnectRunnable = Runnable {
        if (!autoReconnectEnabled || ble.isConnected || ble.savedAddress == null || !hasPermissions()) return@Runnable
        reconnectAttempt++
        deviceUi = deviceUi.copy(status = getString(R.string.device_reconnecting, reconnectAttempt))
        ble.connectSaved()
        handler.removeCallbacks(reconnectTimeout)
        handler.postDelayed(reconnectTimeout, 10_000)
    }

    private val reconnectTimeout = Runnable {
        if (!autoReconnectEnabled || ble.isConnected) return@Runnable
        deviceUi = deviceUi.copy(status = getString(R.string.device_retry_timeout))
        ble.disconnect()
        scheduleAutoReconnect(reconnectDelay())
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.all { it }) {
            updateDisconnectedControls()
            deviceUi = deviceUi.copy(status = getString(R.string.device_permission_granted))
            startAutoReconnect()
        } else {
            deviceUi = deviceUi.copy(status = getString(R.string.device_permission_needed))
        }
    }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val pending = measurementServices.exportCsv(uri)
        measurementScope.launch {
            try { pending.await(); toast(getString(R.string.measurement_csv_complete)) }
            catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { toast(getString(R.string.measurement_csv_failed)) }
        }
    }

    private val projectImportLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val pending = projectServices.importProject(uri)
        measurementScope.launch {
            try { toast(getString(R.string.project_import_complete, pending.await().manifest.name)) }
            catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { projectServices.acknowledgeError(); toast(getString(R.string.project_import_failed)) }
        }
    }
    private val projectExportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val id = pendingExportProjectId
        pendingExportProjectId = null
        if (uri == null || id == null) return@registerForActivityResult
        val pending = projectServices.exportProject(id, uri)
        measurementScope.launch {
            try { pending.await(); toast(getString(R.string.project_export_complete)) }
            catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { projectServices.acknowledgeError(); toast(getString(R.string.project_export_failed)) }
        }
    }
    private fun exportProject(id: String) {
        pendingExportProjectId = id
        projectExportLauncher.launch("LaserSketch-${dateForFile()}.lsk")
    }
    override fun onSaveInstanceState(outState: Bundle) {
        pendingExportProjectId?.let { outState.putString("exportProjectId", it) }
        super.onSaveInstanceState(outState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingExportProjectId = savedInstanceState?.getString("exportProjectId")
        deviceUi = DeviceScreenState(getString(R.string.device_disconnected), getString(R.string.device_none_saved))
        title = "LaserSketch"
        buildInterface()
        measurementScope.launch {
            measurementServices.errors.collect { toast(getString(R.string.measurement_storage_failed)) }
        }
        ble = BoschBleManager(this, this)
        deviceUi = deviceUi.copy(device = if (ble.savedAddress != null) {
            getString(R.string.device_saved, ble.savedName ?: getString(R.string.ble_default_device), ble.savedAddress)
        } else getString(R.string.device_none_saved))
        updateDisconnectedControls()
        ensurePermissions()
    }

    override fun onStart() {
        super.onStart()
        projectJob = measurementScope.launch {
            launch { projectServices.errors.collect { pending ->
                if (pending) { toast(getString(R.string.project_storage_failed)); projectServices.acknowledgeError() }
            } }
            launch { projectServices.migrationNotices.collect { pending ->
                if (pending) { toast(getString(R.string.project_migration_notice)); projectServices.acknowledgeMigrationNotice() }
            } }
        }
    }

    override fun onStop() {
        projectJob?.cancel()
        projectJob = null
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        projectServices.refresh()
    }

    private fun buildInterface() {
        setContentView(ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme {
                    HomeScreen(
                        labels = HomeLabels(getString(R.string.project_import_copy), getString(R.string.home_photos),
                            getString(R.string.home_sketches), getString(R.string.home_add_photo),
                            getString(R.string.home_add_sketch), getString(R.string.device_title), getString(R.string.home_add_symbol)),
                        style = HomeStyle(colorResource(R.color.annotation_tool_active), colorResource(R.color.home_sketch_action),
                            colorResource(R.color.home_device_connected), colorResource(R.color.home_device_disconnected), colorResource(R.color.white)),
                        connected = deviceUi.connected, showDevice = showDevice,
                        onImport = { projectImportLauncher.launch(arrayOf("*/*")) },
                        onAddPhoto = ::showImageSourceChooser, onAddSketch = ::createSketchProject,
                        onToggleDevice = { showDevice = !showDevice },
                        photos = { ProjectList(sketch = false) }, sketches = { ProjectList(sketch = true) },
                        measurements = {
                            AndroidMeasurements(measurementServices.service, deviceUi.connected,
                                onMeasure = { ble.send(BoschProtocol.greenMeasureKey) }, onClear = ::confirmClearMeasurements,
                                onExport = { exportLauncher.launch("bosch-glm-${dateForFile()}.csv") },
                                onDelete = { id -> measurementServices.enqueue { delete(id) } })
                        },
                        device = {
                            DeviceScreen(deviceUi, DeviceLabels(getString(R.string.device_title), getString(R.string.device_scan),
                                getString(R.string.device_connect_saved), getString(R.string.device_disconnect)),
                                onScan = { requirePermissionThen { autoReconnectEnabled = false; cancelAutoReconnect(); ble.scanAndConnect() } },
                                onConnectSaved = { requirePermissionThen { autoReconnectEnabled = true; reconnectAttempt = 0; scheduleAutoReconnect(0) } },
                                onDisconnect = { autoReconnectEnabled = false; cancelAutoReconnect(); ble.disconnect(); onDisconnected() })
                        },
                        deviceIcon = { Icon(painterResource(R.drawable.ic_device), contentDescription = null) },
                    )
                }
            }
        })
    }

    private fun showImageSourceChooser() {
        AlertDialog.Builder(this)
            .setTitle("添加尺寸标注")
            .setItems(arrayOf("拍照", "从图片中选择")) { _, position ->
                val source = if (position == 0) {
                    AnnotationActivity.SOURCE_CAMERA
                } else {
                    AnnotationActivity.SOURCE_GALLERY
                }
                startActivity(
                    Intent(this, AnnotationActivity::class.java).apply {
                        putExtra(AnnotationActivity.EXTRA_START_SOURCE, source)
                    },
                )
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun createSketchProject() {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val pending = projectServices.createSketch(getString(R.string.project_sketch_default_name, date))
        measurementScope.launch {
            try { openSketchProject(pending.await().manifest.projectId) }
            catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { /* The active project error subscription presents the failure. */ }
        }
    }

    @Composable
    private fun ProjectList(sketch: Boolean) {
        val labels = ProjectLabels(getString(if (sketch) R.string.project_empty_sketch else R.string.project_empty_photo),
            getString(R.string.project_actions), getString(R.string.project_rename), getString(R.string.project_delete), getString(R.string.project_export))
        AndroidProjectList(projectServices, sketch, labels,
            updatedLabel = { getString(R.string.project_updated, SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it))) },
            onOpen = { if (sketch) openSketchProject(it) else openAnnotationProject(it) },
            onRename = { id -> projectServices.projects.value.firstOrNull { it.info.id == id }?.info?.let {
                if (sketch) showRenameSketchDialog(SketchProjectSummary(it.id, it.name, it.updatedAt))
                else showRenameProjectDialog(AnnotationProjectSummary(it.id, it.name, it.updatedAt))
            } },
            onDelete = { id -> projectServices.projects.value.firstOrNull { it.info.id == id }?.info?.let {
                if (sketch) confirmDeleteSketch(SketchProjectSummary(it.id, it.name, it.updatedAt))
                else confirmDeleteProject(AnnotationProjectSummary(it.id, it.name, it.updatedAt))
            } },
            onExport = ::exportProject)
    }

    private fun showRenameProjectDialog(project: AnnotationProjectSummary) {
        val input = EditText(this).apply {
            setText(project.name)
            setSelection(text.length)
            setSingleLine(true)
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle("修改文件名字")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) toast("名字不能为空")
                else projectServices.rename(project.id, name)
            }
            .show()
    }

    private fun confirmDeleteProject(project: AnnotationProjectSummary) {
        AlertDialog.Builder(this)
            .setTitle("删除“${project.name}”？")
            .setMessage(R.string.project_delete_message)
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                projectServices.delete(project.id)
            }
            .show()
    }

    private fun openAnnotationProject(projectId: String) {
        val photos = projectServices.projects.value.firstOrNull { it.info.id == projectId }?.photoAssetIds.orEmpty()
        if (photos.size > 1) {
            AlertDialog.Builder(this).setTitle(R.string.project_photo_choose)
                .setItems(photos.mapIndexed { index, _ -> getString(R.string.project_photo_number, index + 1) }.toTypedArray()) { _, index ->
                    launchAnnotationProject(projectId, photos[index])
                }.show()
        } else launchAnnotationProject(projectId, photos.firstOrNull())
    }

    private fun launchAnnotationProject(projectId: String, assetId: String?) {
        startActivity(
            Intent(this, AnnotationActivity::class.java).apply {
                putExtra(AnnotationActivity.EXTRA_PROJECT_ID, projectId)
                putExtra(AnnotationActivity.EXTRA_PHOTO_ASSET_ID, assetId)
            },
        )
    }

    override fun onConnectionStatus(message: String) {
        deviceUi = deviceUi.copy(status = message)
    }

    override fun onConnected(name: String, address: String) {
        autoReconnectEnabled = true
        cancelAutoReconnect()
        reconnectAttempt = 0
        deviceUi = deviceUi.copy(status = getString(R.string.device_ready))
        deviceUi = deviceUi.copy(device = getString(R.string.device_connected_name, name, address))
        updateConnectedControls()
    }

    override fun onDisconnected() {
        handler.removeCallbacksAndMessages(null)
        deviceUi = deviceUi.copy(status = getString(R.string.device_connection_closed))
        updateDisconnectedControls()
        if (autoReconnectEnabled) scheduleAutoReconnect(reconnectDelay())
    }

    override fun onMeasurement(measurement: MeasurementRecord) {
        measurementServices.enqueue { record(measurement) }
    }

    override fun onScanCandidates(devices: List<BoschBleManager.ScanCandidate>) {
        if (devices.isEmpty() || isFinishing) return
        val visible = devices.take(30)
        com.example.boschlaser.platform.showBleDevicePicker(this, visible.map { it.displayText }) { position ->
            val selected = visible[position]
            deviceUi = deviceUi.copy(device = getString(R.string.device_trying, selected.displayText))
            ble.connectAddress(selected.address)
        }
    }

    override fun onError(message: String) {
        deviceUi = deviceUi.copy(status = message)
        if (!ble.isConnected) {
            updateDisconnectedControls()
            if (autoReconnectEnabled) scheduleAutoReconnect(reconnectDelay()) else toast(message)
        } else {
            toast(message)
        }
    }

    private fun confirmClearMeasurements() {
        if (measurementServices.service.records.value.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle(R.string.measurement_clear_title)
            .setMessage(R.string.measurement_clear_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.measurement_clear) { _, _ -> measurementServices.enqueue { clear() } }
            .show()
    }

    private fun updateConnectedControls() {
        deviceUi = deviceUi.copy(connected = true, canConnectSaved = false)
    }

    private fun updateDisconnectedControls() {
        val permitted = hasPermissions()
        deviceUi = deviceUi.copy(connected = false, canConnectSaved = permitted && ::ble.isInitialized && ble.savedAddress != null)
    }

    private fun ensurePermissions() {
        if (!hasPermissions()) permissionLauncher.launch(requiredPermissions)
        else startAutoReconnect()
    }

    private fun startAutoReconnect() {
        if (ble.savedAddress == null) {
            deviceUi = deviceUi.copy(status = getString(R.string.device_no_saved_hint))
            return
        }
        autoReconnectEnabled = true
        reconnectAttempt = 0
        scheduleAutoReconnect(0)
    }

    private fun scheduleAutoReconnect(delayMillis: Long) {
        if (!autoReconnectEnabled || ble.isConnected || ble.savedAddress == null || !hasPermissions()) return
        handler.removeCallbacks(reconnectRunnable)
        handler.removeCallbacks(reconnectTimeout)
        handler.postDelayed(reconnectRunnable, delayMillis)
    }

    private fun reconnectDelay(): Long = when {
        reconnectAttempt < 2 -> 1_000
        reconnectAttempt < 5 -> 2_500
        else -> 5_000
    }

    private fun cancelAutoReconnect() {
        handler.removeCallbacks(reconnectRunnable)
        handler.removeCallbacks(reconnectTimeout)
    }

    private fun requirePermissionThen(action: () -> Unit) {
        if (hasPermissions()) action() else permissionLauncher.launch(requiredPermissions)
    }

    private fun hasPermissions(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private val requiredPermissions = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
    )

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun dateForFile(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::ble.isInitialized) ble.close()
        measurementScope.cancel()
        super.onDestroy()
    }

    private fun showRenameSketchDialog(project: SketchProjectSummary) {
        val input = EditText(this).apply { setText(project.name); setSelection(text.length); setSingleLine(true); selectAll() }
        AlertDialog.Builder(this)
            .setTitle("修改草稿名字").setView(input).setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) toast("名字不能为空")
                else projectServices.rename(project.id, name)
            }.show()
    }

    private fun confirmDeleteSketch(project: SketchProjectSummary) {
        AlertDialog.Builder(this).setTitle("删除“${project.name}”？")
            .setMessage(R.string.project_delete_message)
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                projectServices.delete(project.id)
            }.show()
    }

    private fun openSketchProject(projectId: String) {
        startActivity(Intent(this, SketchActivity::class.java).apply {
            putExtra(SketchActivity.EXTRA_PROJECT_ID, projectId)
        })
    }
}
