package com.example.boschlaser

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import com.example.boschlaser.platform.AndroidPhotoCodec
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.dimensionResource
import com.example.boschlaser.ui.AnnotationPropertySheet
import com.example.boschlaser.ui.AnnotationPropertySheetStyle
import com.example.boschlaser.ui.AnnotationScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.colorResource
import com.example.boschlaser.ui.AnnotationToolbar
import com.example.boschlaser.ui.AnnotationToolItem
import com.example.boschlaser.ui.PlacementOptions
import com.example.boschlaser.ui.AnnotationActions
import com.example.boschlaser.ui.RecentMeasurements
import com.example.boschlaser.ui.AnnotationAreaPointAction
import com.example.boschlaser.ui.AnnotationStyleProperties
import com.example.boschlaser.ui.AnnotationStyleLabels
import com.example.boschlaser.ui.ColorPalette
import com.example.boschlaser.ui.ColorOption
import com.example.boschlaser.ui.AnnotationLabelProperties
import com.example.boschlaser.ui.AnnotationQuantityProperty
import com.example.boschlaser.shared.measurement.MeasurementRecord
import com.example.boschlaser.shared.measurement.isNewestMeasurementAdded
import com.example.boschlaser.shared.annotation.AnnotationQuantityLabel
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class AnnotationActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_MEASUREMENTS_MM = "measurements_mm"
        const val EXTRA_PROJECT_ID = "annotation_project_id"
        const val EXTRA_PHOTO_ASSET_ID = "annotation_photo_asset_id"
        private const val STATE_LOADING_IMAGE = "annotation_loading_image"
        private const val STATE_PENDING_PROJECT = "annotation_pending_project"
        const val EXTRA_START_SOURCE = "start_source"
        const val SOURCE_CAMERA = "camera"
        const val SOURCE_GALLERY = "gallery"
        private const val STATE_IMAGE_URI = "annotation_image_uri"
        private const val STATE_CAMERA_URI = "annotation_camera_uri"
        private const val STATE_PROJECT_ID = "annotation_project_id_state"
        private const val STATE_CONTINUOUS_PLACEMENT = "annotation_continuous_placement"
    }

    private lateinit var annotationView: ImageAnnotationView
    private lateinit var hintText: TextView
    private var propertyPanelVisible by mutableStateOf(false)
    private var propertyPanelResetKey by mutableStateOf(0)
    private var animatePropertyDismiss by mutableStateOf(false)
    private var distanceProperty by mutableStateOf<TextFieldValue?>(null)
    private var distanceUnit by mutableStateOf(0)
    private var textProperty by mutableStateOf<TextFieldValue?>(null)
    private var angleProperty by mutableStateOf<TextFieldValue?>(null)
    private var dismissPropertyFocus by mutableStateOf(0)
    private var areaProperty by mutableStateOf<TextFieldValue?>(null)
    private var areaUnit by mutableStateOf(0)
    private var hasAnnotationSelection by mutableStateOf(false)
    private var hasSelectedAreaPoint by mutableStateOf(false)
    private var recentMeasurementLabels by mutableStateOf<List<String>>(emptyList())
    private var selectedMark by mutableStateOf<DimensionMark?>(null)
    private var selectedTextMark by mutableStateOf<TextMark?>(null)
    private var selectedAngleMark by mutableStateOf<AngleMark?>(null)
    private var selectedAreaMark by mutableStateOf<AreaMark?>(null)
    private var editorMode by mutableStateOf(AnnotationMode.DIMENSION)
    private var continuousPlacement by mutableStateOf(false)
    private var latestMeasurements: List<Double> = emptyList()
    private var observedMeasurementIds: List<String>? = null
    private var cameraUri: Uri? = null
    private var currentImageUri: Uri? = null
    private var currentProjectId: String? = null
    private var currentPhotoAssetId: String? = null
    private var projectLoaded = false
    private var loadingNewImage = false
    private var pendingPhotoProjectId: String? = null
    private var imageJob: Job? = null
    private val projectServices by lazy { ProjectServices.get(this) }
    private val initialMeasurementValues by lazy {
        intent.getFloatArrayExtra(EXTRA_MEASUREMENTS_MM)?.map { it.toDouble() }.orEmpty().asReversed()
    }
    private val measurementScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var measurementJob: Job? = null
    private val measurementService by lazy { MeasurementServices.get(this).service }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            loadImage(uri)
        } else {
            hintText.text = getString(R.string.annotation_no_image_selected)
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = cameraUri
        if (success && uri != null) loadImage(uri) else toast(getString(R.string.annotation_no_photo_taken))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        buildInterface()
        continuousPlacement = savedInstanceState?.getBoolean(STATE_CONTINUOUS_PLACEMENT) ?: false
        currentProjectId = if (savedInstanceState?.getBoolean(STATE_LOADING_IMAGE) == true) null else
            savedInstanceState?.getString(STATE_PROJECT_ID) ?: intent.getStringExtra(EXTRA_PROJECT_ID)
        pendingPhotoProjectId = savedInstanceState?.getString(STATE_PENDING_PROJECT)
        currentPhotoAssetId = savedInstanceState?.getString(EXTRA_PHOTO_ASSET_ID) ?: intent.getStringExtra(EXTRA_PHOTO_ASSET_ID)
        measurementScope.launch {
            projectServices.migrationNotices.collect { pending ->
                if (pending) { toast(getString(R.string.project_migration_notice)); projectServices.acknowledgeMigrationNotice() }
            }
        }
        cameraUri = savedInstanceState?.getString(STATE_CAMERA_URI)?.let(Uri::parse)
        val restoredImageUri = savedInstanceState?.getString(STATE_IMAGE_URI)?.let(Uri::parse)
        if (savedInstanceState?.getBoolean(STATE_LOADING_IMAGE) == true) currentImageUri = restoredImageUri
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
        if (!loadingNewImage) currentProjectId?.let { outState.putString(STATE_PROJECT_ID, it) }
        currentPhotoAssetId?.let { outState.putString(EXTRA_PHOTO_ASSET_ID, it) }
        outState.putBoolean(STATE_LOADING_IMAGE, loadingNewImage)
        pendingPhotoProjectId?.let { outState.putString(STATE_PENDING_PROJECT, it) }
        outState.putBoolean(STATE_CONTINUOUS_PLACEMENT, continuousPlacement)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        if (::annotationView.isInitialized) annotationView.cancelGesture()
        saveProject()
        super.onPause()
    }

    private fun saveProject() {
        if (!projectLoaded || !annotationView.hasImage()) return
        val id = currentProjectId ?: return
        val assetId = currentPhotoAssetId ?: return
        val bitmap = annotationView.exportPreviewBitmap()
        try {
            val preview = bitmap?.let(AndroidPhotoCodec::png)
            val pending = projectServices.saveAnnotation(id, assetId, annotationView.snapshotDocument(), preview)
            measurementScope.launch {
                try { pending.await() }
                catch (failure: CancellationException) { throw failure }
                catch (_: Exception) { projectServices.acknowledgeError(); toast(getString(R.string.project_storage_failed)) }
            }
        } catch (_: Exception) { toast(getString(R.string.project_storage_failed)) }
        finally { bitmap?.recycle() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onStart() {
        super.onStart()
        measurementJob = measurementScope.launch {
            measurementService.records.collect {
                handleMeasurementSnapshot(measurementService.newestFirst())
            }
        }
    }

    override fun onStop() {
        measurementJob?.cancel()
        measurementJob = null
        super.onStop()
    }

    private fun buildInterface() {
        @Composable fun ImageToolbar() {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = colorResource(R.color.annotation_tool_active),
                onPrimary = colorResource(R.color.white),
            )) {
                AnnotationToolbar(
                    selected = editorMode,
                    tools = listOf(
                        AnnotationToolItem(AnnotationMode.MOVE, getString(R.string.annotation_tool_move)),
                        AnnotationToolItem(AnnotationMode.DIMENSION, getString(R.string.annotation_tool_dimension)),
                        AnnotationToolItem(AnnotationMode.TEXT, getString(R.string.annotation_tool_text)),
                        AnnotationToolItem(AnnotationMode.ANGLE, getString(R.string.annotation_tool_angle)),
                        AnnotationToolItem(AnnotationMode.AREA, getString(R.string.annotation_tool_area)),
                    ),
                    backLabel = getString(R.string.annotation_back),
                    shareLabel = getString(R.string.annotation_share),
                    saveLabel = getString(R.string.annotation_save_gallery),
                    onBack = { finish() }, onShare = ::shareToWeChat, onSave = ::saveToGallery,
                    onTool = { setEditorMode(it) },
                    backIcon = { Icon(painterResource(R.drawable.ic_back), null) },
                    shareIcon = { Icon(painterResource(R.drawable.ic_share), null) },
                    saveIcon = { Icon(painterResource(R.drawable.ic_save), null) },
                    toolIcon = { mode -> Icon(painterResource(when (mode) {
                        AnnotationMode.MOVE -> R.drawable.ic_move
                        AnnotationMode.DIMENSION -> R.drawable.ic_dimension
                        AnnotationMode.TEXT -> R.drawable.ic_text
                        AnnotationMode.ANGLE -> R.drawable.ic_angle
                        AnnotationMode.AREA -> R.drawable.ic_area
                    }), null) },
                )
            }
        }

        @Composable fun PlacementModeOptions() {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = colorResource(R.color.annotation_tool_active),
                onPrimary = colorResource(R.color.white),
            )) {
                PlacementOptions(editorMode != AnnotationMode.MOVE, continuousPlacement,
                    getString(R.string.annotation_placement_mode), getString(R.string.annotation_placement_single),
                    getString(R.string.annotation_placement_continuous), { continuousPlacement = it })
            }
        }

        @Composable fun LabelProperties() {
            MaterialTheme {
                AnnotationLabelProperties(textProperty, angleProperty,
                    getString(R.string.annotation_text_input), getString(R.string.annotation_angle_input),
                    getString(R.string.annotation_degree), dismissPropertyFocus,
                    onText = { value ->
                        if (textProperty?.text == value.text || annotationView.updateSelectedText(value.text)) {
                            textProperty = value
                            selectedTextMark = selectedTextMark?.copy(text = value.text)
                        }
                    }, onAngle = { value ->
                        if (angleProperty?.text == value.text || annotationView.updateSelectedAngleLabel(value.text)) {
                            angleProperty = value
                            selectedAngleMark = selectedAngleMark?.copy(label = value.text.trim())
                        }
                    })
            }
        }

        @Composable fun StyleProperties() {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = colorResource(R.color.annotation_tool_active), onPrimary = colorResource(R.color.white),
            )) {
                AnnotationStyleProperties(selectedTextMark?.textSize, selectedTextMark?.whiteBackground ?: true,
                    selectedMark?.thickness,
                    AnnotationStyleLabels(getString(R.string.annotation_style_size),
                        resources.getStringArray(R.array.annotation_style_sizes).toList(),
                        getString(R.string.annotation_style_background), getString(R.string.annotation_style_thickness),
                        resources.getStringArray(R.array.annotation_style_thicknesses).toList()),
                    onTextSize = ::selectTextSize,
                    onBackground = { checked ->
                        if (annotationView.updateSelectedTextBackground(checked)) {
                            selectedTextMark = selectedTextMark?.copy(whiteBackground = checked)
                        }
                    }, onThickness = ::selectLineThickness)
            }
        }

        @Composable fun DeleteAreaPoint() {
            MaterialTheme {
                AnnotationAreaPointAction(hasSelectedAreaPoint, getString(R.string.annotation_delete_area_point),
                    onDelete = {
                        if (!annotationView.deleteSelectedAreaPoint()) {
                            toast(getString(R.string.annotation_area_point_delete_failed))
                        }
                    }, icon = { Icon(painterResource(R.drawable.ic_delete_point), null,
                        tint = colorResource(R.color.annotation_area_point_delete)) })
            }
        }

        hintText = TextView(this).apply {
            text = getString(R.string.annotation_initial_hint)
            textSize = 13f
            setPadding(dp(10), dp(5), dp(10), dp(7))
        }

        annotationView = ImageAnnotationView(this).apply {
            onMessage = { notice ->
                hintText.text = notice.text
                if (notice.showToast) toast(notice.text)
            }
            onSelectionChanged = { selection -> showSelection(selection) }
            onAnnotationCreated = { mode -> handleAnnotationCreated(mode) }
            onCanvasTouched = { finishDistanceEditing() }
            onManipulationStarted = { dismissSelectionPanel() }
        }
        @Composable fun ImageActionsOverlay() {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = colorResource(R.color.annotation_tool_active),
                error = colorResource(R.color.annotation_action_delete),
                onPrimary = colorResource(R.color.white), onError = colorResource(R.color.white),
            )) {
                AnnotationActions(editorMode != AnnotationMode.MOVE,
                    editorMode == AnnotationMode.MOVE && hasAnnotationSelection,
                    getString(R.string.annotation_finish), getString(R.string.annotation_delete_selected),
                    onFinish = { setEditorMode(AnnotationMode.MOVE) },
                    onDelete = { if (!annotationView.deleteSelected()) toast(getString(R.string.annotation_selection_required)) },
                    finishIcon = { Icon(painterResource(R.drawable.ic_confirm), null) },
                    deleteIcon = { Icon(painterResource(R.drawable.ic_delete), null) })
            }
        }
        @Composable fun RecentValues() {
            MaterialTheme(colorScheme = lightColorScheme(primary = colorResource(R.color.annotation_tool_active),
                onPrimary = colorResource(R.color.white))) {
                RecentMeasurements(recentMeasurementLabels, selectedMark != null, ::applyRecentMeasurement)
            }
        }

        @Composable fun Colors() {
            MaterialTheme(colorScheme = lightColorScheme(secondary = colorResource(R.color.annotation_color_selected))) {
                ColorPalette(selectedMark?.color ?: selectedTextMark?.color ?: selectedAngleMark?.color ?: selectedAreaMark?.color,
                    getString(R.string.annotation_style_color), listOf(
                        ColorOption(resources.getColor(R.color.annotation_color_red, theme), getString(R.string.annotation_color_red)),
                        ColorOption(resources.getColor(R.color.annotation_tool_active, theme), getString(R.string.annotation_color_green)),
                        ColorOption(resources.getColor(R.color.annotation_color_blue, theme), getString(R.string.annotation_color_blue)),
                        ColorOption(resources.getColor(R.color.annotation_color_orange, theme), getString(R.string.annotation_color_orange)),
                        ColorOption(resources.getColor(R.color.annotation_color_black, theme), getString(R.string.annotation_color_black)),
                    ), ::selectMarkColor)
            }
        }

        val root = FrameLayout(this).apply {
            setPadding(0, dp(8), 0, dp(10))
            setBackgroundColor(Color.WHITE)
        }
        root.addView(ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AnnotationScreen(colorResource(R.color.white),
                    dimensionResource(R.dimen.annotation_actions_end_margin),
                    dimensionResource(R.dimen.annotation_properties_margin),
                    toolbar = { ImageToolbar() }, placementOptions = { PlacementModeOptions() },
                    canvas = { AndroidView(factory = { annotationView }, modifier = Modifier.fillMaxSize()) },
                    actions = { ImageActionsOverlay() },
                    properties = {
                        AnnotationPropertySheet(propertyPanelVisible, propertyPanelResetKey, animatePropertyDismiss,
                            getString(R.string.annotation_properties_dismiss), propertySheetStyle(), ::dismissSelectionPanel) {
                            QuantityProperties(false)
                            LabelProperties()
                            QuantityProperties(true)
                            RecentValues()
                            Colors()
                            StyleProperties()
                            DeleteAreaPoint()
                        }
                    })
            }
        }, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        applyWindowInsets(root)
        updateRecentMeasurements(availableMeasurements())
    }

    @Composable
    private fun propertySheetStyle() = AnnotationPropertySheetStyle(
        colorResource(R.color.annotation_properties_background), colorResource(R.color.annotation_properties_handle),
        dimensionResource(R.dimen.annotation_properties_horizontal_padding), dimensionResource(R.dimen.annotation_properties_vertical_padding),
        dimensionResource(R.dimen.annotation_properties_elevation), dimensionResource(R.dimen.annotation_properties_corner_radius),
        dimensionResource(R.dimen.annotation_properties_handle_area_height), dimensionResource(R.dimen.annotation_properties_handle_width),
        dimensionResource(R.dimen.annotation_properties_handle_height), dimensionResource(R.dimen.annotation_properties_handle_corner_radius),
        dimensionResource(R.dimen.annotation_properties_dismiss_threshold), dimensionResource(R.dimen.annotation_properties_exit_extra_distance),
    )

    private fun showSelection(selection: AnnotationSelection?) {
        hasAnnotationSelection = selection != null
        selectedMark = (selection as? AnnotationSelection.Dimension)?.mark
        selectedTextMark = (selection as? AnnotationSelection.Text)?.mark
        selectedAngleMark = (selection as? AnnotationSelection.Angle)?.mark
        selectedAreaMark = (selection as? AnnotationSelection.Area)?.mark
        propertyPanelResetKey++
        animatePropertyDismiss = false
        propertyPanelVisible = selection != null
        textProperty = selectedTextMark?.text?.let { TextFieldValue(it, TextRange(it.length)) }
        angleProperty = selectedAngleMark?.label?.let { TextFieldValue(it, TextRange(it.length)) }
        hasSelectedAreaPoint = (selection as? AnnotationSelection.Area)?.selectedPointIndex?.let { it >= 0 } == true
        updateRecentMeasurements(availableMeasurements())
        val distance = selectedMark?.label?.let { AnnotationQuantityLabel.parse(it, quantityUnits(false)) }
        distanceProperty = distance?.text?.let { TextFieldValue(it, TextRange(it.length)) }
        distanceUnit = distance?.unit ?: 0
        val area = selectedAreaMark?.label?.let { AnnotationQuantityLabel.parse(it, quantityUnits(true)) }
        areaProperty = area?.text?.let { TextFieldValue(it, TextRange(it.length)) }
        areaUnit = area?.unit ?: 0
    }

    private fun quantityUnits(area: Boolean) = resources.getStringArray(
        if (area) R.array.annotation_area_units else R.array.annotation_distance_units).toList()

    @Composable
    private fun QuantityProperties(area: Boolean) {
        MaterialTheme {
            AnnotationQuantityProperty(if (area) areaProperty else distanceProperty,
                if (area) areaUnit else distanceUnit, quantityUnits(area),
                getString(if (area) R.string.annotation_area_input else R.string.annotation_distance_input),
                getString(if (area) R.string.annotation_area_unit else R.string.annotation_distance_unit), dismissPropertyFocus,
                onValue = { value ->
                    val previous = if (area) areaProperty else distanceProperty
                    if (previous?.text == value.text || updateQuantity(area, value.text, if (area) areaUnit else distanceUnit)) {
                        if (area) areaProperty = value else distanceProperty = value
                    }
                }, onUnit = { unit ->
                    val value = if (area) areaProperty else distanceProperty
                    val previousUnit = if (area) areaUnit else distanceUnit
                    if (value != null && unit != previousUnit && updateQuantity(area, value.text, unit)) {
                        if (area) areaUnit = unit else distanceUnit = unit
                    }
                })
        }
    }

    private fun updateQuantity(area: Boolean, text: String, unit: Int): Boolean {
        val label = AnnotationQuantityLabel.format(text, unit, quantityUnits(area))
        val accepted = if (area) annotationView.updateSelectedAreaLabel(label) else annotationView.updateSelectedLabel(label)
        if (accepted) {
            if (area) selectedAreaMark = selectedAreaMark?.copy(label = label)
            else selectedMark = selectedMark?.copy(label = label)
        }
        return accepted
    }

    private fun setEditorMode(mode: AnnotationMode, preserveSelection: Boolean = false) {
        editorMode = mode
        annotationView.setMode(mode, preserveSelection)
        hintText.text = when (mode) {
            AnnotationMode.MOVE -> getString(R.string.annotation_move_mode_hint)
            AnnotationMode.DIMENSION -> getString(R.string.annotation_dimension_mode_hint)
            AnnotationMode.TEXT -> getString(R.string.annotation_text_mode_hint)
            AnnotationMode.ANGLE -> getString(R.string.annotation_angle_mode_hint)
            AnnotationMode.AREA -> getString(R.string.annotation_area_mode_hint)
        }
    }

    private fun handleAnnotationCreated(mode: AnnotationMode) {
        if (continuousPlacement || editorMode != mode) return
        setEditorMode(AnnotationMode.MOVE, preserveSelection = true)
        hintText.text = getString(R.string.annotation_single_placement_completed_hint)
    }

    private fun selectMarkColor(color: Int) {
        if (!annotationView.updateSelectedColor(color)) return
        selectedMark = selectedMark?.copy(color = color)
        selectedTextMark = selectedTextMark?.copy(color = color)
        selectedAngleMark = selectedAngleMark?.copy(color = color)
        selectedAreaMark = selectedAreaMark?.copy(color = color)
        hintText.text = getString(R.string.annotation_color_changed)
    }

    private fun selectTextSize(textSize: Int) {
        if (!annotationView.updateSelectedTextSize(textSize)) return
        selectedTextMark = selectedTextMark?.copy(textSize = textSize)
    }

    private fun selectLineThickness(thickness: Int) {
        if (!annotationView.updateSelectedLineThickness(thickness)) return
        selectedMark = selectedMark?.copy(thickness = thickness)
    }

    private fun dismissSelectionPanel() {
        if (!propertyPanelVisible) return
        finishDistanceEditing()
        animatePropertyDismiss = true
        propertyPanelVisible = false
    }

    private fun finishDistanceEditing() {
        dismissPropertyFocus++
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    private fun updateRecentMeasurements(values: List<Double>) {
        latestMeasurements = values.take(3)
        if (::annotationView.isInitialized) {
            annotationView.defaultDimensionLabel = latestMeasurements.firstOrNull()?.let {
                "${formatAnnotationMillimeters(it)} mm"
            }.orEmpty()
        }
        recentMeasurementLabels = latestMeasurements.map { "${formatAnnotationMillimeters(it)} mm" }
    }

    private fun handleMeasurementSnapshot(records: List<MeasurementRecord>) {
        val ids = records.map { it.measurement.id }
        val receivedNewValue = isNewestMeasurementAdded(observedMeasurementIds, ids)
        observedMeasurementIds = ids
        val values = records.map { it.measurement.valueMm }
        updateRecentMeasurements(values)
        if (receivedNewValue && selectedMark != null) {
            applyMeasurementToSelected(values.first(), automatic = true)
        }
    }

    private fun applyRecentMeasurement(index: Int) {
        val value = latestMeasurements.getOrNull(index) ?: return
        if (selectedMark == null) {
            toast(getString(R.string.annotation_dimension_selection_required))
            return
        }
        applyMeasurementToSelected(value, automatic = false)
    }

    private fun applyMeasurementToSelected(value: Double, automatic: Boolean) {
        val text = formatAnnotationMillimeters(value)
        distanceProperty = TextFieldValue(text, TextRange(text.length))
        distanceUnit = 0
        annotationView.updateSelectedLabel("$text mm")
        selectedMark = selectedMark?.copy(label = "$text mm")
        if (automatic) {
            hintText.text = getString(R.string.annotation_latest_measurement_applied, text)
            toast(getString(R.string.annotation_dimension_updated, text))
        } else {
            hintText.text = getString(R.string.annotation_measurement_applied, text)
        }
    }

    override fun onDestroy() {
        measurementScope.cancel()
        super.onDestroy()
    }

    private fun availableMeasurements(): List<Double> =
        measurementService.newestFirst().map { it.measurement.valueMm }.ifEmpty { initialMeasurementValues }

    private fun shareToWeChat() {
        val result = annotationView.exportBitmap() ?: run {
            toast(getString(R.string.annotation_photo_required))
            return
        }
        hintText.text = getString(R.string.annotation_share_preparing)
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
                        clipData = ClipData.newRawUri(getString(R.string.annotation_share_clip_label), uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        setPackage("com.tencent.mm")
                    }
                    try {
                        startActivity(shareIntent)
                        hintText.text = getString(R.string.annotation_share_select_wechat)
                    } catch (_: ActivityNotFoundException) {
                        hintText.text = getString(R.string.annotation_share_wechat_missing)
                        toast(getString(R.string.annotation_share_install_wechat))
                    }
                }.onFailure {
                    hintText.text = getString(R.string.annotation_share_generation_error, it.message)
                    toast(getString(R.string.annotation_share_generation_failed))
                }
            }
        }.start()
    }

    private fun saveToGallery() {
        val result = annotationView.exportBitmap() ?: run {
            toast(getString(R.string.annotation_photo_required))
            return
        }
        hintText.text = getString(R.string.annotation_gallery_saving)
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
                    ?: error(getString(R.string.annotation_gallery_create_failed))
                contentResolver.openOutputStream(imageUri!!)?.use { stream ->
                    check(result.compress(Bitmap.CompressFormat.PNG, 100, stream))
                } ?: error(getString(R.string.annotation_gallery_write_failed))
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(imageUri!!, values, null, null)
            }
            result.recycle()
            if (saved.isFailure) imageUri?.let { contentResolver.delete(it, null, null) }
            runOnUiThread {
                saved.onSuccess {
                    hintText.text = getString(R.string.annotation_gallery_saved_hint)
                    toast(getString(R.string.annotation_gallery_saved))
                }.onFailure {
                    hintText.text = getString(R.string.annotation_gallery_error, it.message)
                    toast(getString(R.string.annotation_gallery_failed))
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
        hintText.text = getString(R.string.annotation_choose_photo_hint)
        galleryLauncher.launch(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            },
        )
    }

    private fun loadImage(uri: Uri) {
        annotationView.cancelGesture()
        saveProject()
        imageJob?.cancel()
        val reservedId = if (currentImageUri == uri) pendingPhotoProjectId else null
        pendingPhotoProjectId = reservedId ?: java.util.UUID.randomUUID().toString()
        val newProjectId = requireNotNull(pendingPhotoProjectId)
        currentImageUri = uri
        loadingNewImage = true
        projectLoaded = false
        annotationView.isEnabled = false
        showSelection(null)
        hintText.text = getString(R.string.project_photo_loading)
        imageJob = measurementScope.launch {
            var bitmap: Bitmap? = null
            var transferred = false
            try {
                val jpeg = withContext(Dispatchers.IO) {
                    val decoded = AndroidPhotoCodec.read(contentResolver, uri)
                    bitmap = decoded
                    AndroidPhotoCodec.jpeg(decoded)
                }
                val image = requireNotNull(bitmap)
                val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                val project = projectServices.createAnnotation(getString(R.string.project_photo_default_name, date), jpeg, image.width, image.height, newProjectId).await()
                val photo = projectServices.loadAnnotation(project.manifest.projectId).await() ?: throw NoSuchElementException()
                val storedImage = withContext(Dispatchers.IO) {
                    val stored = AndroidPhotoCodec.decode(photo.bytes) ?: throw IllegalArgumentException()
                    bitmap?.recycle()
                    bitmap = stored
                    stored
                }
                val document = photo.project.annotations.getValue(photo.assetId)
                pendingPhotoProjectId = null
                currentProjectId = project.manifest.projectId
                currentPhotoAssetId = document.photoAssetId
                annotationView.setBitmap(storedImage)
                annotationView.restoreDocument(document)
                transferred = true
                projectLoaded = true
                loadingNewImage = false
                annotationView.isEnabled = true
                hintText.text = getString(R.string.project_photo_created)
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) {
                loadingNewImage = false
                pendingPhotoProjectId = null
                projectLoaded = currentProjectId != null && currentPhotoAssetId != null && annotationView.hasImage()
                annotationView.isEnabled = true
                currentImageUri = null
                projectServices.acknowledgeError()
                hintText.text = getString(R.string.project_photo_read_failed)
                toast(getString(R.string.project_photo_read_failed))
            } finally { if (!transferred) bitmap?.recycle() }
        }
    }

    private fun loadProject(projectId: String) {
        imageJob?.cancel()
        loadingNewImage = false
        projectLoaded = false
        annotationView.isEnabled = false
        hintText.text = getString(R.string.project_photo_opening)
        imageJob = measurementScope.launch {
            var bitmap: Bitmap? = null
            var transferred = false
            try {
                val photo = projectServices.loadAnnotation(projectId, currentPhotoAssetId).await() ?: throw NoSuchElementException()
                withContext(Dispatchers.IO) { bitmap = AndroidPhotoCodec.decode(photo.bytes) ?: throw IllegalArgumentException() }
                currentProjectId = photo.project.manifest.projectId
                currentPhotoAssetId = photo.assetId
                annotationView.setBitmap(requireNotNull(bitmap))
                annotationView.restoreDocument(photo.project.annotations.getValue(photo.assetId))
                transferred = true
                projectLoaded = true
                annotationView.isEnabled = true
                hintText.text = getString(R.string.project_photo_opened, photo.project.manifest.name)
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { projectServices.acknowledgeError(); toast(getString(R.string.project_open_failed)); finish() }
            finally { if (!transferred) bitmap?.recycle() }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun applyWindowInsets(root: android.view.View) {
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
