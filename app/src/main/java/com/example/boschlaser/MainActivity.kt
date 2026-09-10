package com.example.boschlaser

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), BoschBleManager.Listener {
    private lateinit var ble: BoschBleManager
    private lateinit var statusText: TextView
    private lateinit var deviceText: TextView
    private lateinit var distanceText: TextView
    private lateinit var historyContainer: LinearLayout
    private lateinit var projectContainer: LinearLayout
    private lateinit var sketchProjectContainer: LinearLayout
    private lateinit var deviceConnectionPanel: LinearLayout
    private lateinit var deviceFloatingButton: ImageButton
    private lateinit var connectSavedButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var greenButton: Button

    private val measurements = mutableListOf<Measurement>()
    private var visibleHistoryCount = 100
    private val handler = Handler(Looper.getMainLooper())
    private var autoReconnectEnabled = true
    private var reconnectAttempt = 0

    private val reconnectRunnable = Runnable {
        if (!autoReconnectEnabled || ble.isConnected || ble.savedAddress == null || !hasPermissions()) return@Runnable
        reconnectAttempt++
        statusText.text = "正在自动连接上次设备（第 ${reconnectAttempt} 次）…"
        ble.connectSaved()
        handler.removeCallbacks(reconnectTimeout)
        handler.postDelayed(reconnectTimeout, 10_000)
    }

    private val reconnectTimeout = Runnable {
        if (!autoReconnectEnabled || ble.isConnected) return@Runnable
        statusText.text = "本次连接超时，稍后继续重试…"
        ble.disconnect()
        scheduleAutoReconnect(reconnectDelay())
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.all { it }) {
            updateDisconnectedControls()
            statusText.text = "蓝牙权限已允许，准备自动连接GLM"
            startAutoReconnect()
        } else {
            statusText.text = "需要允许“附近设备”权限才能连接GLM"
        }
    }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8).use { writer ->
                requireNotNull(writer)
                writer.write("序号,时间,类型,本次毫米,本次米,累计毫米,原始报文\n")
                measurements.forEachIndexed { index, item ->
                    writer.write(
                        listOf(
                            index + 1,
                            item.timeText,
                            item.operation ?: "单次距离",
                            "%.1f".format(Locale.US, item.millimeters),
                            "%.4f".format(Locale.US, item.meters),
                            item.accumulatedMeters?.let { "%.1f".format(Locale.US, it * 1000f) }.orEmpty(),
                            item.raw.hex(),
                        ).joinToString(",") + "\n",
                    )
                }
            }
        }.onSuccess {
            toast("CSV已保存")
        }.onFailure {
            toast("导出失败：${it.message}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "LaserSketch"
        buildInterface()
        measurements += MeasurementHistoryStore.load(this)
        MeasurementStore.replaceAll(measurements.map { it.millimeters })
        renderMeasurementHistory()
        updateCurrentDistance()
        ble = BoschBleManager(this, this)
        deviceText.text = if (ble.savedAddress != null) {
            "上次设备：${ble.savedName ?: "Bosch GLM"} · ${ble.savedAddress}"
        } else {
            "尚未保存设备"
        }
        updateDisconnectedControls()
        ensurePermissions()
    }

    override fun onResume() {
        super.onResume()
        if (::projectContainer.isInitialized) renderAnnotationProjects()
        if (::sketchProjectContainer.isInitialized) renderSketchProjects()
    }

    private fun buildInterface() {
        val screen = FrameLayout(this)
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(104))
        }
        scroll.addView(root)
        screen.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        setContentView(screen)
        applyWindowInsets(root)

        statusText = bodyText("未连接")
        deviceText = smallText("尚未保存设备")
        val connectionButtons = row()
        connectionButtons.addView(actionButton("扫描并连接") {
            requirePermissionThen {
                autoReconnectEnabled = false
                cancelAutoReconnect()
                ble.scanAndConnect()
            }
        })
        connectSavedButton = actionButton("连接上次设备") {
            requirePermissionThen {
                autoReconnectEnabled = true
                reconnectAttempt = 0
                scheduleAutoReconnect(0)
            }
        }
        disconnectButton = actionButton("断开") {
            autoReconnectEnabled = false
            cancelAutoReconnect()
            ble.disconnect()
            onDisconnected()
        }
        connectionButtons.addView(connectSavedButton)
        connectionButtons.addView(disconnectButton)

        deviceConnectionPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            elevation = dp(14).toFloat()
            setPadding(dp(16), dp(12), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.argb(250, 255, 255, 255))
                cornerRadius = dp(16).toFloat()
            }
            visibility = android.view.View.GONE
            addView(sectionTitle("设备连接").apply { setPadding(0, 0, 0, dp(5)) })
            addView(statusText)
            addView(deviceText)
            addView(connectionButtons)
        }
        screen.addView(
            deviceConnectionPanel,
            FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
                setMargins(dp(16), dp(16), dp(16), dp(96))
            },
        )

        deviceFloatingButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_device)
            setColorFilter(Color.WHITE)
            setPadding(dp(15), dp(15), dp(15), dp(15))
            elevation = dp(12).toFloat()
            contentDescription = "设备连接"
            background = deviceButtonBackground(false)
            setOnClickListener {
                deviceConnectionPanel.visibility = if (deviceConnectionPanel.visibility == android.view.View.VISIBLE) {
                    android.view.View.GONE
                } else {
                    android.view.View.VISIBLE
                }
            }
        }
        screen.addView(
            deviceFloatingButton,
            FrameLayout.LayoutParams(dp(60), dp(60), Gravity.BOTTOM or Gravity.END).apply {
                marginEnd = dp(18)
                bottomMargin = dp(22)
            },
        )
        ViewCompat.setOnApplyWindowInsetsListener(deviceFloatingButton) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            (view.layoutParams as FrameLayout.LayoutParams).apply {
                bottomMargin = bars.bottom + dp(22)
                view.layoutParams = this
            }
            (deviceConnectionPanel.layoutParams as FrameLayout.LayoutParams).apply {
                bottomMargin = bars.bottom + dp(96)
                deviceConnectionPanel.layoutParams = this
            }
            insets
        }
        ViewCompat.requestApplyInsets(deviceFloatingButton)

        root.addView(sectionTitle("图片标注文件"))
        projectContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(projectContainer)

        root.addView(sectionTitle("平面草稿"))
        sketchProjectContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(sketchProjectContainer)

        root.addView(sectionTitle("当前距离"))
        distanceText = TextView(this).apply {
            text = "— mm"
            textSize = 34f
            setTextColor(Color.rgb(20, 80, 55))
            setPadding(0, dp(6), 0, dp(2))
        }
        root.addView(distanceText)
        greenButton = actionButton("测量并记录") {
            ble.send(BoschProtocol.greenMeasureKey)
        }
        val greenButtons = row()
        greenButtons.addView(greenButton)
        root.addView(greenButtons)

        root.addView(sectionTitle("测量记录"))
        val recordButtons = row()
        recordButtons.addView(actionButton("清空记录") {
            if (measurements.isEmpty()) {
                toast("还没有测量记录")
            } else {
                AlertDialog.Builder(this)
                    .setTitle("清空全部测量记录？")
                    .setMessage("清空后无法恢复。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("清空") { _, _ ->
                        MeasurementHistoryStore.clear(this)
                        measurements.clear()
                        MeasurementStore.clear()
                        visibleHistoryCount = 100
                        renderMeasurementHistory()
                        updateCurrentDistance()
                    }
                    .show()
            }
        })
        recordButtons.addView(actionButton("导出CSV") {
            if (measurements.isEmpty()) toast("还没有测量记录")
            else exportLauncher.launch("bosch-glm-${dateForFile()}.csv")
        })
        root.addView(recordButtons)
        historyContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        root.addView(historyContainer)

        val addAnnotationButton = floatingAddButton("添加图片尺寸标注", Color.rgb(0, 112, 82)) {
            showImageSourceChooser()
        }
        val addSketchButton = floatingAddButton("添加平面草稿", Color.rgb(28, 91, 145)) {
            createSketchProject()
        }
        val addButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(addSketchButton, LinearLayout.LayoutParams(dp(64), dp(64)).apply { marginEnd = dp(8) })
            addView(addAnnotationButton, LinearLayout.LayoutParams(dp(64), dp(64)).apply { marginStart = dp(8) })
        }
        screen.addView(
            addButtons,
            FrameLayout.LayoutParams(-2, dp(64), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = dp(20)
            },
        )
        ViewCompat.setOnApplyWindowInsetsListener(addButtons) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            (view.layoutParams as FrameLayout.LayoutParams).apply {
                bottomMargin = bars.bottom + dp(20)
                view.layoutParams = this
            }
            insets
        }
        ViewCompat.requestApplyInsets(addButtons)
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
                        putExtra(
                            AnnotationActivity.EXTRA_MEASUREMENTS_MM,
                            measurements.map { it.millimeters }.toFloatArray(),
                        )
                    },
                )
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun createSketchProject() {
        val id = runCatching { SketchProjectStore.create(this) }.getOrElse {
            toast("无法创建草稿：${it.message}")
            return
        }
        openSketchProject(id)
    }

    private fun renderAnnotationProjects() {
        projectContainer.removeAllViews()
        val projects = AnnotationProjectStore.list(this)
        if (projects.isEmpty()) {
            projectContainer.addView(smallText("暂无标注文件，点击下方 + 新建"))
            return
        }
        projects.chunked(2).forEach { pair ->
            val gridRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
            }
            pair.forEachIndexed { index, project ->
                gridRow.addView(
                    annotationProjectCard(project),
                    LinearLayout.LayoutParams(0, -2, 1f).apply {
                        if (index == 0) marginEnd = dp(5) else marginStart = dp(5)
                        bottomMargin = dp(10)
                    },
                )
            }
            if (pair.size == 1) {
                gridRow.addView(android.view.View(this), LinearLayout.LayoutParams(0, 1, 1f).apply { marginStart = dp(5) })
            }
            projectContainer.addView(gridRow)
        }
    }

    private fun annotationProjectCard(project: AnnotationProjectSummary): LinearLayout {
        val availableWidth = resources.displayMetrics.widthPixels - dp(32) - dp(10)
        val cardWidth = availableWidth / 2
        val previewHeight = (cardWidth * 4f / 3f).toInt()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            elevation = 0f
            setPadding(0, 0, 0, dp(6))
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
            isFocusable = true
            setOnClickListener { openAnnotationProject(project.id) }
        }
        val previewFrame = FrameLayout(this)
        previewFrame.addView(
            ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = "${project.name}预览图"
                AnnotationProjectStore.loadThumbnail(this@MainActivity, project.id, dp(180))?.let(::setImageBitmap)
            },
            FrameLayout.LayoutParams(-1, previewHeight),
        )
        val moreButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_more_vert)
            setColorFilter(Color.rgb(45, 45, 45))
            contentDescription = "${project.name}更多操作"
            setPadding(dp(9), dp(9), dp(9), dp(9))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(225, 255, 255, 255))
            }
            setOnClickListener { showProjectMenu(this, project) }
        }
        previewFrame.addView(
            moreButton,
            FrameLayout.LayoutParams(dp(40), dp(40), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(4)
                marginEnd = dp(4)
            },
        )
        card.addView(previewFrame, LinearLayout.LayoutParams(-1, previewHeight))
        card.addView(TextView(this).apply {
            text = project.name
            textSize = 14f
            setTextColor(Color.rgb(30, 30, 30))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(2), dp(8), dp(2), dp(2))
        }, LinearLayout.LayoutParams(-1, -2))
        card.addView(TextView(this).apply {
            text = "编辑于 " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(project.updatedAt))
            textSize = 11f
            setTextColor(Color.GRAY)
            maxLines = 1
            setPadding(dp(2), 0, dp(2), 0)
        }, LinearLayout.LayoutParams(-1, -2))
        return card
    }

    private fun showProjectMenu(anchor: android.view.View, project: AnnotationProjectSummary) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, "修改名字")
            menu.add(0, 2, 1, "删除")
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        showRenameProjectDialog(project)
                        true
                    }
                    2 -> {
                        confirmDeleteProject(project)
                        true
                    }
                    else -> false
                }
            }
            show()
        }
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
                else if (!AnnotationProjectStore.rename(this, project.id, name)) toast("修改名字失败")
                renderAnnotationProjects()
            }
            .show()
    }

    private fun confirmDeleteProject(project: AnnotationProjectSummary) {
        AlertDialog.Builder(this)
            .setTitle("删除“${project.name}”？")
            .setMessage("照片和可编辑标注将一起删除，且无法恢复。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                if (!AnnotationProjectStore.delete(this, project.id)) toast("删除失败")
                renderAnnotationProjects()
            }
            .show()
    }

    private fun openAnnotationProject(projectId: String) {
        startActivity(
            Intent(this, AnnotationActivity::class.java).apply {
                putExtra(AnnotationActivity.EXTRA_PROJECT_ID, projectId)
                putExtra(
                    AnnotationActivity.EXTRA_MEASUREMENTS_MM,
                    measurements.map { it.millimeters }.toFloatArray(),
                )
            },
        )
    }

    override fun onConnectionStatus(message: String) {
        statusText.text = message
    }

    override fun onConnected(name: String, address: String) {
        autoReconnectEnabled = true
        cancelAutoReconnect()
        reconnectAttempt = 0
        statusText.text = "已连接，测量通知已启用"
        deviceText.text = "$name · $address"
        updateConnectedControls()
    }

    override fun onDisconnected() {
        handler.removeCallbacksAndMessages(null)
        statusText.text = "连接已断开"
        updateDisconnectedControls()
        if (autoReconnectEnabled) scheduleAutoReconnect(reconnectDelay())
    }

    override fun onFrame(frame: ByteArray) {
        BoschProtocol.parseMeasurement(frame)?.let { addMeasurement(it) }
    }

    override fun onScanCandidates(devices: List<BoschBleManager.ScanCandidate>) {
        if (devices.isEmpty() || isFinishing) return
        val visible = devices.take(30)
        AlertDialog.Builder(this)
            .setTitle("选择附近的Bosch GLM")
            .setMessage("未能通过名称或服务UUID自动识别。请打开GLM蓝牙后，选择信号较强或名称包含GLM的设备。")
            .setItems(visible.map { it.displayText }.toTypedArray()) { _, position ->
                val selected = visible[position]
                deviceText.text = "正在尝试：${selected.displayText}"
                ble.connectAddress(selected.address)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onError(message: String) {
        statusText.text = message
        if (!ble.isConnected) {
            updateDisconnectedControls()
            if (autoReconnectEnabled) scheduleAutoReconnect(reconnectDelay()) else toast(message)
        } else {
            toast(message)
        }
    }

    private fun addMeasurement(item: Measurement) {
        measurements += item
        MeasurementHistoryStore.add(this, item)
        MeasurementStore.add(item.millimeters)
        distanceText.text = "%.1f mm".format(item.millimeters)
        renderMeasurementHistory()
    }

    private fun renderMeasurementHistory() {
        if (!::historyContainer.isInitialized) return
        historyContainer.removeAllViews()
        if (measurements.isEmpty()) {
            historyContainer.addView(smallText("暂无测量记录"))
            return
        }

        measurements.takeLast(visibleHistoryCount).asReversed().forEach { item ->
            val recordRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(3), 0, dp(3))
            }
            val total = item.accumulatedMeters?.let { "，累计 %.1f mm".format(it * 1000f) }.orEmpty()
            recordRow.addView(
                TextView(this).apply {
                    text = "${item.timeText}  ${item.operation ?: "距离"}  %.1f mm%s".format(item.millimeters, total)
                    textSize = 14f
                    setTextIsSelectable(true)
                },
                LinearLayout.LayoutParams(0, -2, 1f),
            )
            recordRow.addView(
                ImageButton(this).apply {
                    setImageResource(R.drawable.ic_delete)
                    setColorFilter(Color.rgb(180, 45, 45))
                    setBackgroundColor(Color.TRANSPARENT)
                    contentDescription = "删除这条测量记录"
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    setOnClickListener { deleteMeasurement(item) }
                },
                LinearLayout.LayoutParams(dp(48), dp(48)),
            )
            historyContainer.addView(recordRow)
        }

        val hiddenCount = measurements.size - visibleHistoryCount
        if (hiddenCount > 0) {
            historyContainer.addView(
                actionButton("显示更早记录（还有 $hiddenCount 条）") {
                    visibleHistoryCount += 100
                    renderMeasurementHistory()
                }.apply { layoutParams = LinearLayout.LayoutParams(-1, -2) },
            )
        }
    }

    private fun deleteMeasurement(item: Measurement) {
        val index = measurements.indexOfFirst { it.timestamp == item.timestamp }
        if (index < 0) return
        MeasurementHistoryStore.delete(this, item.timestamp)
        measurements.removeAt(index)
        MeasurementStore.replaceAll(measurements.map { it.millimeters })
        renderMeasurementHistory()
        updateCurrentDistance()
    }

    private fun updateCurrentDistance() {
        distanceText.text = measurements.lastOrNull()?.let { "%.1f mm".format(it.millimeters) } ?: "— mm"
    }

    private fun updateConnectedControls() {
        connectSavedButton.isEnabled = false
        disconnectButton.isEnabled = true
        greenButton.isEnabled = true
        if (::deviceFloatingButton.isInitialized) deviceFloatingButton.background = deviceButtonBackground(true)
    }

    private fun updateDisconnectedControls() {
        val permitted = hasPermissions()
        connectSavedButton.isEnabled = permitted && ::ble.isInitialized && ble.savedAddress != null
        disconnectButton.isEnabled = false
        greenButton.isEnabled = false
        if (::deviceFloatingButton.isInitialized) deviceFloatingButton.background = deviceButtonBackground(false)
    }

    private fun ensurePermissions() {
        if (!hasPermissions()) permissionLauncher.launch(requiredPermissions)
        else startAutoReconnect()
    }

    private fun startAutoReconnect() {
        if (ble.savedAddress == null) {
            statusText.text = "未保存设备，请先扫描并连接一次"
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
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 19f
        setTextColor(Color.rgb(30, 30, 30))
        setPadding(0, dp(18), 0, dp(6))
    }

    private fun bodyText(text: String) = TextView(this).apply {
        this.text = text
        textSize = 15f
        setPadding(0, dp(3), 0, dp(3))
    }

    private fun smallText(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.DKGRAY)
        setPadding(0, dp(3), 0, dp(5))
    }

    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.START
    }

    private fun actionButton(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) }
    }

    private fun floatingAddButton(description: String, color: Int, action: () -> Unit) = Button(this).apply {
        text = "+"
        textSize = 30f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        elevation = dp(12).toFloat()
        contentDescription = description
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
        setOnClickListener { action() }
    }

    private fun deviceButtonBackground(connected: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(if (connected) Color.rgb(0, 130, 92) else Color.rgb(92, 99, 105))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun applyWindowInsets(root: LinearLayout) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(16), bars.top + dp(8), dp(16), maxOf(dp(104), bars.bottom + dp(84)))
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun dateForFile(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::ble.isInitialized) ble.disconnect()
        super.onDestroy()
    }

    private fun renderSketchProjects() {
        sketchProjectContainer.removeAllViews()
        val projects = SketchProjectStore.list(this)
        if (projects.isEmpty()) {
            sketchProjectContainer.addView(smallText("暂无平面草稿，点击下方蓝色 + 新建"))
            return
        }
        projects.chunked(2).forEach { pair ->
            val gridRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
            pair.forEachIndexed { index, project ->
                gridRow.addView(
                    sketchProjectCard(project),
                    LinearLayout.LayoutParams(0, -2, 1f).apply {
                        if (index == 0) marginEnd = dp(5) else marginStart = dp(5)
                        bottomMargin = dp(10)
                    },
                )
            }
            if (pair.size == 1) gridRow.addView(android.view.View(this), LinearLayout.LayoutParams(0, 1, 1f).apply { marginStart = dp(5) })
            sketchProjectContainer.addView(gridRow)
        }
    }

    private fun sketchProjectCard(project: SketchProjectSummary): LinearLayout {
        val availableWidth = resources.displayMetrics.widthPixels - dp(32) - dp(10)
        val cardWidth = availableWidth / 2
        val previewHeight = (cardWidth * 3f / 4f).toInt()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(6))
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
            isFocusable = true
            setOnClickListener { openSketchProject(project.id) }
            addView(FrameLayout(this@MainActivity).apply {
                setBackgroundColor(Color.rgb(242, 244, 243))
                addView(ImageView(this@MainActivity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = "${project.name}预览图"
                    SketchProjectStore.loadThumbnail(this@MainActivity, project.id, dp(220))?.let(::setImageBitmap)
                }, FrameLayout.LayoutParams(-1, previewHeight))
                addView(ImageButton(this@MainActivity).apply {
                    setImageResource(R.drawable.ic_more_vert)
                    setColorFilter(Color.rgb(45, 45, 45))
                    contentDescription = "${project.name}更多操作"
                    setPadding(dp(9), dp(9), dp(9), dp(9))
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(225, 255, 255, 255)) }
                    setOnClickListener { showSketchProjectMenu(this, project) }
                }, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.TOP or Gravity.END).apply {
                    topMargin = dp(4); marginEnd = dp(4)
                })
            }, LinearLayout.LayoutParams(-1, previewHeight))
            addView(TextView(this@MainActivity).apply {
                text = project.name; textSize = 14f; setTextColor(Color.rgb(30, 30, 30))
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(dp(2), dp(8), dp(2), dp(2))
            })
            addView(TextView(this@MainActivity).apply {
                text = "编辑于 " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(project.updatedAt))
                textSize = 11f; setTextColor(Color.GRAY); maxLines = 1; setPadding(dp(2), 0, dp(2), 0)
            })
        }
    }

    private fun showSketchProjectMenu(anchor: android.view.View, project: SketchProjectSummary) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, "修改名字")
            menu.add(0, 2, 1, "删除")
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> { showRenameSketchDialog(project); true }
                    2 -> { confirmDeleteSketch(project); true }
                    else -> false
                }
            }
            show()
        }
    }

    private fun showRenameSketchDialog(project: SketchProjectSummary) {
        val input = EditText(this).apply { setText(project.name); setSelection(text.length); setSingleLine(true); selectAll() }
        AlertDialog.Builder(this)
            .setTitle("修改草稿名字").setView(input).setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) toast("名字不能为空")
                else if (!SketchProjectStore.rename(this, project.id, name)) toast("修改名字失败")
                renderSketchProjects()
            }.show()
    }

    private fun confirmDeleteSketch(project: SketchProjectSummary) {
        AlertDialog.Builder(this).setTitle("删除“${project.name}”？")
            .setMessage("草稿中的墙、柱、门窗和尺寸将一起删除，且无法恢复。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                if (!SketchProjectStore.delete(this, project.id)) toast("删除失败")
                renderSketchProjects()
            }.show()
    }

    private fun openSketchProject(projectId: String) {
        startActivity(Intent(this, SketchActivity::class.java).apply {
            putExtra(SketchActivity.EXTRA_PROJECT_ID, projectId)
            putExtra(SketchActivity.EXTRA_MEASUREMENTS_MM, measurements.map { it.millimeters }.toFloatArray())
        })
    }
}
