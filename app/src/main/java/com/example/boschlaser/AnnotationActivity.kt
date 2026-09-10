package com.example.boschlaser

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

class AnnotationActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_MEASUREMENTS_MM = "measurements_mm"
        const val EXTRA_PROJECT_ID = "annotation_project_id"
        const val EXTRA_START_SOURCE = "start_source"
        const val SOURCE_CAMERA = "camera"
        const val SOURCE_GALLERY = "gallery"
        private const val STATE_IMAGE_URI = "annotation_image_uri"
        private const val STATE_CAMERA_URI = "annotation_camera_uri"
        private const val STATE_PROJECT_ID = "annotation_project_id_state"
    }

    private lateinit var annotationView: ImageAnnotationView
    private lateinit var hintText: TextView
    private lateinit var selectionPanel: LinearLayout
    private lateinit var distanceRow: LinearLayout
    private lateinit var distanceInput: EditText
    private lateinit var unitSpinner: Spinner
    private lateinit var textRow: LinearLayout
    private lateinit var textInput: EditText
    private lateinit var angleRow: LinearLayout
    private lateinit var angleInput: EditText
    private lateinit var areaRow: LinearLayout
    private lateinit var areaInput: EditText
    private lateinit var areaUnitSpinner: Spinner
    private lateinit var sizeRow: LinearLayout
    private lateinit var smallTextButton: Button
    private lateinit var mediumTextButton: Button
    private lateinit var largeTextButton: Button
    private lateinit var textBackgroundRow: LinearLayout
    private lateinit var whiteBackgroundCheck: CheckBox
    private lateinit var thicknessRow: LinearLayout
    private lateinit var thinLineButton: Button
    private lateinit var mediumLineButton: Button
    private lateinit var thickLineButton: Button
    private lateinit var moveModeButton: ImageButton
    private lateinit var dimensionModeButton: ImageButton
    private lateinit var textModeButton: ImageButton
    private lateinit var angleModeButton: ImageButton
    private lateinit var areaModeButton: ImageButton
    private lateinit var deleteSelectedButton: ImageButton
    private lateinit var deleteAreaPointButton: ImageButton
    private lateinit var recentOverlay: LinearLayout
    private val recentButtons = mutableListOf<TextView>()
    private val colorButtons = mutableListOf<Pair<Int, TextView>>()
    private val markColors = listOf(
        "红色" to Color.rgb(214, 32, 42),
        "绿色" to Color.rgb(0, 112, 82),
        "蓝色" to Color.rgb(21, 101, 192),
        "橙色" to Color.rgb(239, 108, 0),
        "黑色" to Color.rgb(34, 34, 34),
    )
    private var selectedMark: DimensionMark? = null
    private var selectedTextMark: TextMark? = null
    private var selectedAngleMark: AngleMark? = null
    private var selectedAreaMark: AreaMark? = null
    private var updatingEditor = false
    private var latestMeasurements: List<Float> = emptyList()
    private var observedMeasurementSnapshot: List<Float>? = null
    private var cameraUri: Uri? = null
    private var currentImageUri: Uri? = null
    private var currentProjectId: String? = null
    private val initialMeasurementValues by lazy {
        intent.getFloatArrayExtra(EXTRA_MEASUREMENTS_MM)?.toList().orEmpty().asReversed()
    }
    private val measurementListener: (List<Float>) -> Unit = { values ->
        runOnUiThread { handleMeasurementSnapshot(values) }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            loadImage(uri)
        } else {
            hintText.text = "没有选择图片"
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = cameraUri
        if (success && uri != null) loadImage(uri) else toast("没有拍摄照片")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        buildInterface()
        currentProjectId = savedInstanceState?.getString(STATE_PROJECT_ID)
            ?: intent.getStringExtra(EXTRA_PROJECT_ID)
        cameraUri = savedInstanceState?.getString(STATE_CAMERA_URI)?.let(Uri::parse)
        val restoredImageUri = savedInstanceState?.getString(STATE_IMAGE_URI)?.let(Uri::parse)
        when {
            currentProjectId != null -> loadProject(currentProjectId!!)
            restoredImageUri != null -> loadImage(restoredImageUri)
            savedInstanceState == null -> {
                annotationView.post {
                    when (intent.getStringExtra(EXTRA_START_SOURCE)) {
                        SOURCE_CAMERA -> takePhoto()
                        SOURCE_GALLERY -> openGallery()
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        currentImageUri?.let { outState.putString(STATE_IMAGE_URI, it.toString()) }
        cameraUri?.let { outState.putString(STATE_CAMERA_URI, it.toString()) }
        currentProjectId?.let { outState.putString(STATE_PROJECT_ID, it) }
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        currentProjectId?.let { projectId ->
            if (::annotationView.isInitialized && annotationView.hasImage()) {
                val preview = annotationView.exportPreviewBitmap()
                try {
                    runCatching {
                        AnnotationProjectStore.save(this, projectId, annotationView.snapshotState(), preview)
                    }
                } finally {
                    preview?.recycle()
                }
            }
        }
        super.onPause()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onStart() {
        super.onStart()
        MeasurementStore.addListener(measurementListener)
    }

    override fun onStop() {
        MeasurementStore.removeListener(measurementListener)
        super.onStop()
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(10))
        }
        setContentView(root)
        applyWindowInsets(root)

        val imageActions = row().apply {
            setPadding(dp(6), 0, dp(6), 0)
        }
        imageActions.addView(iconButton(R.drawable.ic_back, "返回") { finish() }.apply {
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(42)).apply { marginEnd = dp(2) }
        })
        moveModeButton = modeIconButton(R.drawable.ic_move, "移动标注") {
            setEditorMode(AnnotationMode.MOVE)
        }
        dimensionModeButton = modeIconButton(R.drawable.ic_dimension, "尺寸标注") {
            setEditorMode(AnnotationMode.DIMENSION)
        }
        textModeButton = modeIconButton(R.drawable.ic_text, "文字标注") {
            setEditorMode(AnnotationMode.TEXT)
        }
        angleModeButton = modeIconButton(R.drawable.ic_angle, "角度标注") {
            setEditorMode(AnnotationMode.ANGLE)
        }
        areaModeButton = modeIconButton(R.drawable.ic_area, "面积标注") {
            setEditorMode(AnnotationMode.AREA)
        }
        imageActions.addView(moveModeButton)
        imageActions.addView(dimensionModeButton)
        imageActions.addView(textModeButton)
        imageActions.addView(angleModeButton)
        imageActions.addView(areaModeButton)
        imageActions.addView(
            android.view.View(this),
            LinearLayout.LayoutParams(0, 1, 1f),
        )
        imageActions.addView(iconButton(R.drawable.ic_share, "分享到微信") { shareToWeChat() })
        imageActions.addView(iconButton(R.drawable.ic_save, "保存到相册") { saveToGallery() })
        root.addView(imageActions)
        updateModeButtons(AnnotationMode.DIMENSION)

        distanceRow = row()
        distanceInput = EditText(this).apply {
            hint = "选中尺寸后填写距离"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            layoutParams = LinearLayout.LayoutParams(0, -2, 2f).apply { marginEnd = dp(6) }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    updateSelectedLabelFromEditor()
                }
            })
        }
        unitSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@AnnotationActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("mm", "m", "cm"),
            )
            layoutParams = LinearLayout.LayoutParams(0, -2, 0.8f)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    updateSelectedLabelFromEditor()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        distanceRow.addView(distanceInput)
        distanceRow.addView(unitSpinner)

        textRow = row()
        textInput = EditText(this).apply {
            hint = "输入单行文字"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (!updatingEditor && selectedTextMark != null) {
                        annotationView.updateSelectedText(s?.toString().orEmpty())
                    }
                }
            })
        }
        textRow.addView(textInput, LinearLayout.LayoutParams(-1, -2))

        angleRow = row()
        angleInput = EditText(this).apply {
            hint = "输入角度"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (!updatingEditor && selectedAngleMark != null) {
                        annotationView.updateSelectedAngleLabel(s?.toString().orEmpty())
                    }
                }
            })
        }
        angleRow.addView(angleInput, LinearLayout.LayoutParams(0, -2, 1f))
        angleRow.addView(TextView(this).apply {
            text = "°"
            textSize = 22f
            setPadding(dp(8), 0, dp(8), 0)
        })

        areaRow = row()
        areaInput = EditText(this).apply {
            hint = "输入面积数值"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            layoutParams = LinearLayout.LayoutParams(0, -2, 2f).apply { marginEnd = dp(6) }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    updateSelectedAreaLabelFromEditor()
                }
            })
        }
        areaUnitSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@AnnotationActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("m²", "cm²", "mm²"),
            )
            layoutParams = LinearLayout.LayoutParams(0, -2, 0.9f)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    updateSelectedAreaLabelFromEditor()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        areaRow.addView(areaInput)
        areaRow.addView(areaUnitSpinner)

        sizeRow = row().apply {
            setPadding(dp(6), dp(4), dp(6), dp(4))
            addView(TextView(this@AnnotationActivity).apply {
                text = "字号"
                textSize = 14f
                setTextColor(Color.DKGRAY)
                setPadding(0, 0, dp(8), 0)
            })
        }
        smallTextButton = modeButton("小") { selectTextSize(0) }
        mediumTextButton = modeButton("中") { selectTextSize(1) }
        largeTextButton = modeButton("大") { selectTextSize(2) }
        sizeRow.addView(smallTextButton)
        sizeRow.addView(mediumTextButton)
        sizeRow.addView(largeTextButton)

        textBackgroundRow = row().apply {
            setPadding(dp(6), dp(2), dp(6), dp(2))
        }
        whiteBackgroundCheck = CheckBox(this).apply {
            text = "白色文字底色"
            isChecked = true
            setOnCheckedChangeListener { _, checked ->
                if (!updatingEditor && selectedTextMark != null) {
                    annotationView.updateSelectedTextBackground(checked)
                    selectedTextMark = selectedTextMark?.copy(whiteBackground = checked)
                }
            }
        }
        textBackgroundRow.addView(whiteBackgroundCheck)

        thicknessRow = row().apply {
            setPadding(dp(6), dp(4), dp(6), dp(4))
            addView(TextView(this@AnnotationActivity).apply {
                text = "线宽"
                textSize = 14f
                setTextColor(Color.DKGRAY)
                setPadding(0, 0, dp(8), 0)
            })
        }
        thinLineButton = modeButton("细") { selectLineThickness(0) }
        mediumLineButton = modeButton("中") { selectLineThickness(1) }
        thickLineButton = modeButton("粗") { selectLineThickness(2) }
        thicknessRow.addView(thinLineButton)
        thicknessRow.addView(mediumLineButton)
        thicknessRow.addView(thickLineButton)

        deleteSelectedButton = iconButton(R.drawable.ic_delete, "删除选中") {
            if (!annotationView.deleteSelected()) toast("请先选择一条标注")
        }.apply { setColorFilter(Color.rgb(190, 35, 45)) }
        deleteAreaPointButton = iconButton(R.drawable.ic_delete_point, "删除面积控制点") {
            if (!annotationView.deleteSelectedAreaPoint()) {
                toast("请先选择控制点；面积至少保留三个点")
            }
        }.apply { setColorFilter(Color.rgb(230, 110, 0)) }

        hintText = TextView(this).apply {
            text = "先画空尺寸线；点按尺寸线可选中，再填写或选择测量记录"
            textSize = 13f
            setPadding(dp(10), dp(5), dp(10), dp(7))
        }

        annotationView = ImageAnnotationView(this).apply {
            onMessage = { message ->
                hintText.text = message
                if (message.startsWith("已放置") || message.startsWith("角度标注已完成")) {
                    toast(message)
                }
            }
            onSelectionChanged = { selection -> showSelection(selection) }
            onCanvasTouched = { finishDistanceEditing() }
            onManipulationStarted = { dismissSelectionPanel() }
        }
        val imageContainer = FrameLayout(this)
        imageContainer.addView(annotationView, FrameLayout.LayoutParams(-1, -1))
        recentOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(5), dp(6), dp(5))
            visibility = android.view.View.GONE
        }
        repeat(3) { index ->
            val recentButton = TextView(this).apply {
                textSize = 14f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(9), dp(10), dp(9))
                background = GradientDrawable().apply {
                    setColor(Color.rgb(0, 112, 82))
                    cornerRadius = dp(8).toFloat()
                }
                setOnClickListener { applyRecentMeasurement(index) }
            }
            recentButtons += recentButton
            recentOverlay.addView(
                recentButton,
                LinearLayout.LayoutParams(0, -2, 1f).apply {
                    if (index < 2) marginEnd = dp(5)
                },
            )
        }

        val colorRow = row().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
            addView(TextView(this@AnnotationActivity).apply {
                text = "颜色"
                textSize = 14f
                setTextColor(Color.DKGRAY)
                setPadding(0, 0, dp(8), 0)
            })
            markColors.forEach { (name, color) ->
                val swatch = TextView(this@AnnotationActivity).apply {
                    contentDescription = name
                    setOnClickListener { selectMarkColor(color) }
                    layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply {
                        marginEnd = dp(9)
                    }
                }
                colorButtons += color to swatch
                addView(swatch)
            }
        }

        selectionPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            elevation = dp(10).toFloat()
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.argb(245, 255, 255, 255))
                cornerRadius = dp(14).toFloat()
            }
            visibility = android.view.View.GONE
            val handleArea = FrameLayout(this@AnnotationActivity).apply {
                contentDescription = "向下滑动收起属性"
                addView(
                    android.view.View(this@AnnotationActivity).apply {
                        background = GradientDrawable().apply {
                            setColor(Color.rgb(170, 174, 178))
                            cornerRadius = dp(2).toFloat()
                        }
                    },
                    FrameLayout.LayoutParams(dp(42), dp(4), Gravity.CENTER),
                )
            }
            addView(handleArea, LinearLayout.LayoutParams(-1, dp(24)))
            enablePanelSwipeDismiss(handleArea)
            addView(distanceRow, LinearLayout.LayoutParams(-1, -2))
            addView(textRow, LinearLayout.LayoutParams(-1, -2))
            addView(angleRow, LinearLayout.LayoutParams(-1, -2))
            addView(areaRow, LinearLayout.LayoutParams(-1, -2))
            addView(recentOverlay, LinearLayout.LayoutParams(-1, -2))
            addView(colorRow, LinearLayout.LayoutParams(-1, -2))
            addView(sizeRow, LinearLayout.LayoutParams(-1, -2))
            addView(textBackgroundRow, LinearLayout.LayoutParams(-1, -2))
            addView(thicknessRow, LinearLayout.LayoutParams(-1, -2))
            addView(row().apply {
                gravity = Gravity.END
                addView(deleteAreaPointButton, LinearLayout.LayoutParams(dp(52), dp(52)))
                addView(deleteSelectedButton, LinearLayout.LayoutParams(dp(52), dp(52)))
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        }
        imageContainer.addView(
            selectionPanel,
            FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                setMargins(dp(10), dp(10), dp(10), dp(10))
            },
        )
        root.addView(imageContainer, LinearLayout.LayoutParams(-1, 0, 1f))
        updateRecentMeasurements(availableMeasurements())
    }

    private fun showSelection(selection: AnnotationSelection?) {
        selectedMark = (selection as? AnnotationSelection.Dimension)?.mark
        selectedTextMark = (selection as? AnnotationSelection.Text)?.mark
        selectedAngleMark = (selection as? AnnotationSelection.Angle)?.mark
        selectedAreaMark = (selection as? AnnotationSelection.Area)?.mark
        selectionPanel.animate().cancel()
        selectionPanel.translationY = 0f
        selectionPanel.alpha = 1f
        selectionPanel.visibility = if (selection != null) android.view.View.VISIBLE else android.view.View.GONE
        distanceRow.visibility = if (selectedMark != null) android.view.View.VISIBLE else android.view.View.GONE
        textRow.visibility = if (selectedTextMark != null) android.view.View.VISIBLE else android.view.View.GONE
        angleRow.visibility = if (selectedAngleMark != null) android.view.View.VISIBLE else android.view.View.GONE
        areaRow.visibility = if (selectedAreaMark != null) android.view.View.VISIBLE else android.view.View.GONE
        sizeRow.visibility = if (selectedTextMark != null) android.view.View.VISIBLE else android.view.View.GONE
        textBackgroundRow.visibility = if (selectedTextMark != null) android.view.View.VISIBLE else android.view.View.GONE
        thicknessRow.visibility = if (selectedMark != null) android.view.View.VISIBLE else android.view.View.GONE
        deleteAreaPointButton.visibility = if (
            (selection as? AnnotationSelection.Area)?.selectedPointIndex?.let { it >= 0 } == true
        ) android.view.View.VISIBLE else android.view.View.GONE
        updateRecentMeasurements(availableMeasurements())
        updatingEditor = true
        try {
            if (selection == null) {
                distanceInput.setText("")
                textInput.setText("")
                angleInput.setText("")
                areaInput.setText("")
                return
            }
            when (selection) {
                is AnnotationSelection.Dimension -> {
                    val mark = selection.mark
                    val parts = mark.label.trim().split(" ")
                    distanceInput.setText(parts.firstOrNull().orEmpty())
                    distanceInput.setSelection(distanceInput.text.length)
                    unitSpinner.setSelection(when (parts.getOrNull(1)) {
                        "m" -> 1
                        "cm" -> 2
                        else -> 0
                    })
                    updateColorButtons(mark.color)
                    updateThicknessButtons(mark.thickness)
                }
                is AnnotationSelection.Text -> {
                    val mark = selection.mark
                    textInput.setText(mark.text)
                    textInput.setSelection(textInput.text.length)
                    updateColorButtons(mark.color)
                    updateSizeButtons(mark.textSize)
                    whiteBackgroundCheck.isChecked = mark.whiteBackground
                }
                is AnnotationSelection.Angle -> {
                    val mark = selection.mark
                    angleInput.setText(mark.label)
                    angleInput.setSelection(angleInput.text.length)
                    updateColorButtons(mark.color)
                }
                is AnnotationSelection.Area -> {
                    val mark = selection.mark
                    val parts = mark.label.trim().split(" ")
                    areaInput.setText(parts.firstOrNull().orEmpty())
                    areaInput.setSelection(areaInput.text.length)
                    areaUnitSpinner.setSelection(when (parts.getOrNull(1)) {
                        "cm²" -> 1
                        "mm²" -> 2
                        else -> 0
                    })
                    updateColorButtons(mark.color)
                }
            }
        } finally {
            updatingEditor = false
        }
    }

    private fun updateSelectedLabelFromEditor() {
        if (updatingEditor || selectedMark == null || !::annotationView.isInitialized) return
        val value = distanceInput.text.toString().trim()
        val label = if (value.isEmpty()) "" else "$value ${unitSpinner.selectedItem}"
        annotationView.updateSelectedLabel(label)
    }

    private fun updateSelectedAreaLabelFromEditor() {
        if (updatingEditor || selectedAreaMark == null || !::annotationView.isInitialized) return
        val value = areaInput.text.toString().trim()
        val label = if (value.isEmpty()) "" else "$value ${areaUnitSpinner.selectedItem}"
        annotationView.updateSelectedAreaLabel(label)
        selectedAreaMark = selectedAreaMark?.copy(label = label)
    }

    private fun setEditorMode(mode: AnnotationMode) {
        annotationView.setMode(mode)
        updateModeButtons(mode)
        hintText.text = when (mode) {
            AnnotationMode.MOVE -> "移动模式：拖动尺寸、文字、角度或面积标注调整位置"
            AnnotationMode.DIMENSION -> "尺寸模式：在图片上拖动添加尺寸线"
            AnnotationMode.TEXT -> "文字模式：点按图片位置添加单行文字"
            AnnotationMode.ANGLE -> "角度模式：点按图片添加角度，拖动三个点调整位置"
            AnnotationMode.AREA -> "面积模式：第三个点后自动闭合，可继续点击添加更多控制点"
        }
    }

    private fun updateModeButtons(mode: AnnotationMode) {
        styleModeButton(moveModeButton, mode == AnnotationMode.MOVE)
        styleModeButton(dimensionModeButton, mode == AnnotationMode.DIMENSION)
        styleModeButton(textModeButton, mode == AnnotationMode.TEXT)
        styleModeButton(angleModeButton, mode == AnnotationMode.ANGLE)
        styleModeButton(areaModeButton, mode == AnnotationMode.AREA)
    }

    private fun selectMarkColor(color: Int) {
        if (!annotationView.updateSelectedColor(color)) return
        selectedMark = selectedMark?.copy(color = color)
        selectedTextMark = selectedTextMark?.copy(color = color)
        selectedAngleMark = selectedAngleMark?.copy(color = color)
        selectedAreaMark = selectedAreaMark?.copy(color = color)
        updateColorButtons(color)
        hintText.text = "已更改选中标注的颜色"
    }

    private fun selectTextSize(textSize: Int) {
        if (!annotationView.updateSelectedTextSize(textSize)) return
        selectedTextMark = selectedTextMark?.copy(textSize = textSize)
        updateSizeButtons(textSize)
    }

    private fun updateSizeButtons(textSize: Int) {
        styleChoiceButton(smallTextButton, textSize == 0)
        styleChoiceButton(mediumTextButton, textSize == 1)
        styleChoiceButton(largeTextButton, textSize == 2)
    }

    private fun selectLineThickness(thickness: Int) {
        if (!annotationView.updateSelectedLineThickness(thickness)) return
        selectedMark = selectedMark?.copy(thickness = thickness)
        updateThicknessButtons(thickness)
    }

    private fun updateThicknessButtons(thickness: Int) {
        styleChoiceButton(thinLineButton, thickness == 0)
        styleChoiceButton(mediumLineButton, thickness == 1)
        styleChoiceButton(thickLineButton, thickness == 2)
    }

    private fun updateColorButtons(selectedColor: Int) {
        colorButtons.forEach { (color, button) ->
            button.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                if (color == selectedColor) {
                    setStroke(dp(3), Color.rgb(0, 188, 212))
                } else {
                    setStroke(dp(2), Color.WHITE)
                }
            }
        }
    }

    private fun enablePanelSwipeDismiss(handle: android.view.View) {
        var touchStartY = 0f
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartY = event.rawY
                    selectionPanel.animate().cancel()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val distance = (event.rawY - touchStartY).coerceAtLeast(0f)
                    selectionPanel.translationY = distance
                    selectionPanel.alpha = (1f - distance / selectionPanel.height.coerceAtLeast(1) * 0.55f)
                        .coerceIn(0.45f, 1f)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (selectionPanel.translationY >= dp(56)) {
                        dismissSelectionPanel()
                    } else {
                        selectionPanel.animate()
                            .translationY(0f)
                            .alpha(1f)
                            .setDuration(140)
                            .start()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun dismissSelectionPanel() {
        if (!::selectionPanel.isInitialized || selectionPanel.visibility != android.view.View.VISIBLE) return
        finishDistanceEditing()
        selectionPanel.animate().cancel()
        selectionPanel.animate()
            .translationY(selectionPanel.height.toFloat() + dp(20))
            .alpha(0f)
            .setDuration(180)
            .withEndAction {
                selectionPanel.visibility = android.view.View.GONE
                selectionPanel.translationY = 0f
                selectionPanel.alpha = 1f
            }
            .start()
    }

    private fun finishDistanceEditing() {
        distanceInput.clearFocus()
        textInput.clearFocus()
        angleInput.clearFocus()
        areaInput.clearFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(selectionPanel.windowToken, 0)
    }

    private fun updateRecentMeasurements(values: List<Float>) {
        latestMeasurements = values.take(3)
        if (::annotationView.isInitialized) {
            annotationView.defaultDimensionLabel = latestMeasurements.firstOrNull()?.let {
                "${formatMillimeters(it)} mm"
            }.orEmpty()
        }
        recentOverlay.visibility = if (selectedMark != null && latestMeasurements.isNotEmpty()) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
        recentButtons.forEachIndexed { index, button ->
            val value = latestMeasurements.getOrNull(index)
            button.visibility = if (value == null) android.view.View.GONE else android.view.View.VISIBLE
            if (value != null) button.text = "${formatMillimeters(value)} mm"
        }
    }

    private fun handleMeasurementSnapshot(values: List<Float>) {
        val previous = observedMeasurementSnapshot
        observedMeasurementSnapshot = values
        updateRecentMeasurements(values)

        val receivedNewValue = previous != null && values.isNotEmpty() && when {
            values.size == previous.size + 1 -> values.drop(1) == previous
            values.size == previous.size -> values.drop(1) == previous.dropLast(1)
            else -> false
        }
        if (receivedNewValue && selectedMark != null) {
            applyMeasurementToSelected(values.first(), automatic = true)
        }
    }

    private fun applyRecentMeasurement(index: Int) {
        val value = latestMeasurements.getOrNull(index) ?: return
        if (selectedMark == null) {
            toast("请先点按选择一条尺寸线")
            return
        }
        applyMeasurementToSelected(value, automatic = false)
    }

    private fun applyMeasurementToSelected(value: Float, automatic: Boolean) {
        val text = formatMillimeters(value)
        updatingEditor = true
        distanceInput.setText(text)
        distanceInput.setSelection(distanceInput.text.length)
        unitSpinner.setSelection(0)
        updatingEditor = false
        annotationView.updateSelectedLabel("$text mm")
        selectedMark = selectedMark?.copy(label = "$text mm")
        if (automatic) {
            hintText.text = "已将最新测量值 $text mm 更新到选中的尺寸线"
            toast("尺寸已更新为 $text mm")
        } else {
            hintText.text = "已将 $text mm 填入选中的尺寸线"
        }
    }

    private fun availableMeasurements(): List<Float> =
        MeasurementStore.newestFirst().ifEmpty { initialMeasurementValues }

    private fun shareToWeChat() {
        val result = annotationView.exportBitmap() ?: run {
            toast("请先拍照或选择图片")
            return
        }
        hintText.text = "正在准备分享…"
        Thread {
            val sharedUri = runCatching {
                val directory = File(cacheDir, "shared").apply { mkdirs() }
                val file = File(directory, "GLM标注分享.png")
                FileOutputStream(file).use { stream ->
                    check(result.compress(Bitmap.CompressFormat.PNG, 100, stream))
                }
                FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            }
            result.recycle()
            runOnUiThread {
                sharedUri.onSuccess { uri ->
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newRawUri("标注图片", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        setPackage("com.tencent.mm")
                    }
                    try {
                        startActivity(shareIntent)
                        hintText.text = "请选择微信联系人分享"
                    } catch (_: ActivityNotFoundException) {
                        hintText.text = "未找到微信"
                        toast("请先安装微信")
                    }
                }.onFailure {
                    hintText.text = "分享图片生成失败：${it.message}"
                    toast("无法生成分享图片")
                }
            }
        }.start()
    }

    private fun saveToGallery() {
        val result = annotationView.exportBitmap() ?: run {
            toast("请先拍照或选择图片")
            return
        }
        hintText.text = "正在保存到相册…"
        Thread {
            var imageUri: Uri? = null
            val saved = runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "GLM标注-${fileTime()}.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/LaserSketch")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("无法创建相册图片")
                contentResolver.openOutputStream(imageUri!!)?.use { stream ->
                    check(result.compress(Bitmap.CompressFormat.PNG, 100, stream))
                } ?: error("无法写入相册图片")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(imageUri!!, values, null, null)
            }
            result.recycle()
            if (saved.isFailure) imageUri?.let { contentResolver.delete(it, null, null) }
            runOnUiThread {
                saved.onSuccess {
                    hintText.text = "标注图片已保存到相册"
                    toast("已保存到相册")
                }.onFailure {
                    hintText.text = "保存失败：${it.message}"
                    toast("保存到相册失败")
                }
            }
        }.start()
    }

    private fun takePhoto() {
        val directory = File(cacheDir, "camera").apply { mkdirs() }
        val file = File.createTempFile("glm-photo-", ".jpg", directory)
        cameraUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        cameraLauncher.launch(cameraUri)
    }

    private fun openGallery() {
        hintText.text = "请选择一张图片…"
        galleryLauncher.launch(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            },
        )
    }

    private fun loadImage(uri: Uri) {
        currentImageUri = uri
        hintText.text = "正在读取图片…"
        Thread {
            val prepared = runCatching {
                val source = ImageDecoder.createSource(contentResolver, uri)
                val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val longest = max(info.size.width, info.size.height)
                    decoder.setTargetSampleSize(max(1, ceil(longest / 3000.0).toInt()))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                val projectId = currentProjectId ?: AnnotationProjectStore.create(this, bitmap)
                projectId to bitmap
            }
            runOnUiThread {
                prepared.onSuccess { (projectId, bitmap) ->
                    currentProjectId = projectId
                    annotationView.setBitmap(bitmap)
                    hintText.text = "图片已载入并创建标注文件"
                }.onFailure {
                    if (currentImageUri == uri) currentImageUri = null
                    hintText.text = "图片读取失败：${it.message}"
                    toast("无法读取这张图片")
                }
            }
        }.start()
    }

    private fun loadProject(projectId: String) {
        hintText.text = "正在打开标注文件…"
        Thread {
            val loaded = runCatching {
                AnnotationProjectStore.load(this, projectId) ?: error("标注文件不存在或已损坏")
            }
            runOnUiThread {
                loaded.onSuccess { project ->
                    if (currentProjectId != projectId) {
                        project.bitmap.recycle()
                        return@onSuccess
                    }
                    annotationView.setBitmap(project.bitmap)
                    annotationView.restoreState(project.state)
                    hintText.text = "已打开 ${project.summary.name}"
                }.onFailure {
                    toast("无法打开标注文件：${it.message}")
                    finish()
                }
            }
        }.start()
    }

    private fun formatMillimeters(value: Float): String =
        if (value % 1f < 0.05f) value.toInt().toString() else "%.1f".format(Locale.US, value)

    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) }
    }

    private fun iconButton(icon: Int, description: String, action: () -> Unit) = ImageButton(this).apply {
        setImageResource(icon)
        contentDescription = description
        setColorFilter(Color.rgb(0, 112, 82))
        setPadding(dp(9), dp(9), dp(9), dp(9))
        setBackgroundColor(Color.TRANSPARENT)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(dp(40), dp(44)).apply { marginStart = dp(1) }
    }

    private fun modeButton(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 13f
        isAllCaps = false
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(13), 0, dp(13), 0)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-2, dp(42)).apply { marginEnd = dp(6) }
    }

    private fun modeIconButton(icon: Int, description: String, action: () -> Unit) = ImageButton(this).apply {
        setImageResource(icon)
        contentDescription = description
        setPadding(dp(7), dp(9), dp(7), dp(9))
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(dp(36), dp(42)).apply { marginEnd = dp(1) }
    }

    private fun styleModeButton(button: ImageButton, selected: Boolean) {
        button.setColorFilter(if (selected) Color.WHITE else Color.rgb(55, 55, 55))
        button.background = GradientDrawable().apply {
            cornerRadius = dp(9).toFloat()
            setColor(if (selected) Color.rgb(0, 112, 82) else Color.rgb(232, 234, 236))
        }
    }

    private fun styleChoiceButton(button: Button, selected: Boolean) {
        button.setTextColor(if (selected) Color.WHITE else Color.rgb(55, 55, 55))
        button.background = GradientDrawable().apply {
            cornerRadius = dp(9).toFloat()
            setColor(if (selected) Color.rgb(0, 112, 82) else Color.rgb(232, 234, 236))
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun applyWindowInsets(root: LinearLayout) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // AppCompat already accounts for the ActionBar height.
            view.setPadding(0, bars.top + dp(8), 0, max(dp(10), bars.bottom))
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun fileTime(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
}
