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
import androidx.compose.ui.graphics.asImageBitmap
import com.example.boschlaser.platform.AndroidPhotoCodec
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.dimensionResource
import com.example.boschlaser.ui.AnnotationScreen
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.colorResource
import com.example.boschlaser.ui.AnnotationToolbar
import com.example.boschlaser.ui.AnnotationToolItem
import com.example.boschlaser.ui.PlacementOptions
import com.example.boschlaser.ui.AnnotationActions
import com.example.boschlaser.ui.AnnotationAreaPointAction
import com.example.boschlaser.ui.AnnotationStyleProperties
import com.example.boschlaser.ui.AnnotationStyleLabels
import com.example.boschlaser.ui.ColorPalette
import com.example.boschlaser.ui.ColorOption
import com.example.boschlaser.ui.AnnotationLabelProperties
import com.example.boschlaser.ui.AnnotationQuantityProperty
import com.example.boschlaser.shared.measurement.MeasurementRecord
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

    private var correctionImage by mutableStateOf<Bitmap?>(null)
    private var correctionResult: CompletableDeferred<com.example.boschlaser.shared.annotation.PhotoCorrection?>? = null

    private lateinit var annotationView: ImageAnnotationView
    private var annotationHint by mutableStateOf("")
    internal val pageState by lazy { AnnotationPageState(quantityUnits(false), quantityUnits(true)) }
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
            annotationHint = getString(R.string.annotation_no_image_selected)
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
        pageState.continuousPlacement = savedInstanceState?.getBoolean(STATE_CONTINUOUS_PLACEMENT) ?: false
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
        outState.putBoolean(STATE_CONTINUOUS_PLACEMENT, pageState.continuousPlacement)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        if (::annotationView.isInitialized) annotationView.cancelGesture()
        pageState.cancelMeasurementWait()
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
                    selected = pageState.mode,
                    tools = listOf(
                        AnnotationToolItem(AnnotationMode.MOVE, getString(R.string.annotation_tool_move)),
                        AnnotationToolItem(AnnotationMode.DIMENSION, getString(R.string.annotation_tool_dimension)),
                        AnnotationToolItem(AnnotationMode.TEXT, getString(R.string.annotation_tool_text)),
                        AnnotationToolItem(AnnotationMode.ANGLE, getString(R.string.annotation_tool_angle)),
                        AnnotationToolItem(AnnotationMode.AREA, getString(R.string.annotation_tool_area)),
                    ),
                    backLabel = getString(R.string.annotation_back),
                    exportLabel = getString(R.string.annotation_export),
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
                PlacementOptions(pageState.mode != AnnotationMode.MOVE, pageState.continuousPlacement,
                    getString(R.string.annotation_placement_mode), getString(R.string.annotation_placement_single),
                    getString(R.string.annotation_placement_continuous), { pageState.continuousPlacement = it })
            }
        }

        @Composable fun LabelProperties() {
            MaterialTheme {
                AnnotationLabelProperties(pageState.properties.text, pageState.properties.angle,
                    getString(R.string.annotation_text_input), getString(R.string.annotation_angle_input),
                    getString(R.string.annotation_degree), pageState.properties.dismissFocus,
                    onText = { pageState.updateText(it, annotationView::updateSelectedText) },
                    onAngle = { pageState.updateAngle(it, annotationView::updateSelectedAngleLabel) })
            }
        }

        @Composable fun StyleProperties() {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = colorResource(R.color.annotation_tool_active), onPrimary = colorResource(R.color.white),
            )) {
                AnnotationStyleProperties(pageState.properties.textMark?.textSize, pageState.properties.textMark?.whiteBackground ?: true,
                    pageState.properties.dimension?.thickness,
                    AnnotationStyleLabels(getString(R.string.annotation_style_size),
                        resources.getStringArray(R.array.annotation_style_sizes).toList(),
                        getString(R.string.annotation_style_background), getString(R.string.annotation_style_thickness),
                        resources.getStringArray(R.array.annotation_style_thicknesses).toList()),
                    onTextSize = ::selectTextSize,
                    onBackground = { checked ->
                        pageState.updateTextBackground(checked, annotationView::updateSelectedTextBackground)
                    }, onThickness = ::selectLineThickness)
            }
        }

        @Composable fun DeleteAreaPoint() {
            MaterialTheme {
                AnnotationAreaPointAction(pageState.properties.hasAreaPoint, getString(R.string.annotation_delete_area_point),
                    onDelete = {
                        if (!annotationView.deleteSelectedAreaPoint()) {
                            toast(getString(R.string.annotation_area_point_delete_failed))
                        }
                    }, icon = { Icon(painterResource(R.drawable.ic_delete_point), null,
                        tint = colorResource(R.color.annotation_area_point_delete)) })
            }
        }

        annotationHint = getString(R.string.annotation_initial_hint)

        annotationView = ImageAnnotationView(this).apply {
            onMessage = { notice ->
                annotationHint = notice.text
                if (notice.showToast) toast(notice.text)
            }
            onSelectionChanged = { selection -> showSelection(selection) }
            onAnnotationCreated = { mode -> handleAnnotationCreated(mode) }
            onCanvasTouched = { finishDistanceEditing(); pageState.dismissPanel() }
            onManipulationStarted = { dismissSelectionPanel() }
        }
        @Composable fun ImageActionsOverlay() {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = colorResource(R.color.annotation_tool_active),
                error = colorResource(R.color.annotation_action_delete),
                onPrimary = colorResource(R.color.white), onError = colorResource(R.color.white),
            )) {
                AnnotationActions(pageState.mode != AnnotationMode.MOVE,
                    pageState.mode == AnnotationMode.MOVE && (pageState.properties.selection != null),
                    getString(R.string.annotation_finish), getString(R.string.annotation_delete_selected),
                    onFinish = { if (pageState.mode == AnnotationMode.AREA) annotationView.finishArea()
                        else setEditorMode(AnnotationMode.MOVE, preserveSelection = true) },
                    onDelete = { if (!annotationView.deleteSelected()) toast(getString(R.string.annotation_selection_required)) },
                    finishIcon = { Icon(painterResource(R.drawable.ic_confirm), null) },
                    deleteIcon = { Icon(painterResource(R.drawable.ic_delete), null) })
            }
        }
        @Composable fun RecentValues() {
            MaterialTheme(colorScheme = lightColorScheme(primary = colorResource(R.color.annotation_tool_active),
                onPrimary = colorResource(R.color.white))) {
                pageState.recentMeasurementLabels.take(3).forEachIndexed { index, label ->
                    androidx.compose.material3.TextButton(onClick = { applyRecentMeasurement(index) }) {
                        androidx.compose.material3.Text(label)
                    }
                }
            }
        }

        @Composable fun Colors() {
            MaterialTheme(colorScheme = lightColorScheme(secondary = colorResource(R.color.annotation_color_selected))) {
                ColorPalette(pageState.properties.color,
                    getString(R.string.annotation_style_color), listOf(
                        ColorOption(resources.getColor(R.color.annotation_color_red, theme), getString(R.string.annotation_color_red)),
                        ColorOption(resources.getColor(R.color.annotation_tool_active, theme), getString(R.string.annotation_color_green)),
                        ColorOption(resources.getColor(R.color.annotation_color_blue, theme), getString(R.string.annotation_color_blue)),
                        ColorOption(resources.getColor(R.color.annotation_color_orange, theme), getString(R.string.annotation_color_orange)),
                        ColorOption(resources.getColor(R.color.annotation_color_black, theme), getString(R.string.annotation_color_black)),
                    ), com.example.boschlaser.ui.CustomColorLabels(
                        getString(R.string.annotation_custom_color), getString(R.string.annotation_color_hex),
                        getString(R.string.annotation_color_wheel), getString(R.string.annotation_color_brightness),
                        getString(R.string.annotation_color_apply), getString(R.string.annotation_color_cancel)), ::selectMarkColor)
            }
        }

        @Composable fun SelectionTools() {
            if (pageState.properties.text != null || pageState.properties.angle != null || pageState.properties.area != null) {
                val isText = pageState.properties.text != null
                val isArea = pageState.properties.area != null
                val title = getString(when {
                    isText -> R.string.annotation_tool_text
                    isArea -> R.string.annotation_tool_area
                    else -> R.string.annotation_tool_angle
                })
                val value = when {
                    isText -> pageState.properties.text?.text.orEmpty()
                    isArea -> pageState.properties.area?.text.orEmpty() + " " + quantityUnits(true)[pageState.properties.areaUnit]
                    else -> pageState.properties.angle?.text.orEmpty() + getString(R.string.annotation_degree)
                }
                com.example.boschlaser.ui.AnnotationDimensionMenu(title, value, getString(R.string.annotation_finish),
                    pageState.properties.resetKey, tag = "annotation-label-menu") {
                    LabelProperties()
                    QuantityProperties(true)
                    if (isText) StyleProperties()
                    if (isArea) {
                        androidx.compose.material3.TextButton(onClick = { annotationView.insertSelectedAreaPoint() }) {
                            androidx.compose.material3.Text(getString(R.string.annotation_insert_area_point))
                        }
                        DeleteAreaPoint()
                    }
                }
            }
            if (pageState.properties.dimension != null) {
                com.example.boschlaser.ui.AnnotationDimensionMenu(getString(R.string.annotation_dimension_properties),
                    pageState.properties.distance?.text.orEmpty() + " " + quantityUnits(false)[pageState.properties.distanceUnit],
                    getString(R.string.annotation_finish), pageState.properties.resetKey) {
                    QuantityProperties(false)
                    androidx.compose.material3.TextButton(onClick = { pageState.toggleMeasurementWait() }) {
                        androidx.compose.material3.Text(getString(if (pageState.waitingForMeasurement)
                            R.string.annotation_waiting_measurement else R.string.annotation_wait_measurement))
                    }
                    RecentValues()
                }
            }
            com.example.boschlaser.ui.AnnotationDimensionMenu(getString(R.string.annotation_style_color), "",
                getString(R.string.annotation_finish), pageState.properties.resetKey,
                tag = "annotation-colors-menu") { Colors() }
            if (pageState.properties.dimension != null) Box(Modifier.width(260.dp)) { StyleProperties() }
        }

        val root = FrameLayout(this).apply {
            setPadding(0, 0, 0, dp(10))
            setBackgroundColor(Color.WHITE)
        }
        root.addView(ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val correcting = correctionImage
                if (correcting != null) {
                    val previewSource = remember(correcting) {
                        val scale = minOf(1.0, 700.0 / maxOf(correcting.width, correcting.height))
                        if (scale < 1) Bitmap.createScaledBitmap(correcting, maxOf(2, (correcting.width * scale).toInt()), maxOf(2, (correcting.height * scale).toInt()), true) else correcting
                    }
                    androidx.compose.ui.window.Dialog(onDismissRequest = {}, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
                        MaterialTheme { androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
                            com.example.boschlaser.ui.PhotoCorrectionScreen(correcting.asImageBitmap(),
                                com.example.boschlaser.ui.PhotoCorrectionLabels(
                                    getString(R.string.photo_correction_title), getString(R.string.photo_correction_hint),
                                    getString(R.string.photo_correction_lens), getString(R.string.photo_correction_reset),
                                    getString(R.string.photo_correction_original), getString(R.string.photo_correction_apply),
                                    getString(R.string.photo_correction_error), getString(R.string.photo_correction_horizontal),
                                    getString(R.string.photo_correction_vertical), getString(R.string.photo_correction_rotation)),
                                render = { settings, maximum -> withContext(Dispatchers.Default) {
                                    val result = correctBitmap(previewSource, settings, maximum)
                                    result.asImageBitmap()
                                } }, onDone = { correctionResult?.complete(it) })
                        } }
                    }
                }
                AnnotationScreen(colorResource(R.color.white),
                    dimensionResource(R.dimen.annotation_actions_end_margin),
                    dimensionResource(R.dimen.annotation_properties_margin),
                    toolbar = { ImageToolbar() }, placementOptions = {
                        com.example.boschlaser.ui.AnnotationSecondaryToolbar(scrollable = false) {
                            if (pageState.mode != AnnotationMode.MOVE) PlacementModeOptions()
                        }
                    },
                    status = { androidx.compose.material3.Text(if (pageState.waitingForMeasurement) getString(R.string.annotation_waiting_measurement) else annotationHint,
                        modifier = Modifier.height(22.dp).padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    canvas = { AndroidView(factory = { annotationView }, modifier = Modifier.fillMaxSize()) },
                    actions = { ImageActionsOverlay() },
                    properties = {
                        if (pageState.mode == AnnotationMode.MOVE && pageState.properties.visible && pageState.properties.selection != null) {
                            com.example.boschlaser.ui.AnnotationEditingPanel { SelectionTools() }
                        }
                    })
            }
        }, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        applyWindowInsets(root)
        updateRecentMeasurements(availableMeasurements())
    }

    private fun showSelection(selection: AnnotationSelection?) {
        pageState.showSelection(selection)
        updateRecentMeasurements(availableMeasurements())
    }

    private fun quantityUnits(area: Boolean) = resources.getStringArray(
        if (area) R.array.annotation_area_units else R.array.annotation_distance_units).toList()

    @Composable
    private fun QuantityProperties(area: Boolean) {
        MaterialTheme {
            val properties = pageState.properties
            val value = if (area) properties.area else properties.distance
            val unit = if (area) properties.areaUnit else properties.distanceUnit
            val commit: (String) -> Boolean = if (area) annotationView::updateSelectedAreaLabel else annotationView::updateSelectedLabel
            AnnotationQuantityProperty(value, unit, quantityUnits(area),
                getString(if (area) R.string.annotation_area_input else R.string.annotation_distance_input),
                getString(if (area) R.string.annotation_area_unit else R.string.annotation_distance_unit), properties.dismissFocus,
                onValue = { pageState.updateQuantity(area, it, unit, commit) },
                onUnit = { selected -> if (!pageState.convertUnit(area, selected, commit)) toast(getString(R.string.annotation_unit_invalid)) })
        }
    }

    private fun setEditorMode(mode: AnnotationMode, preserveSelection: Boolean = false) {
        pageState.cancelMeasurementWait()
        pageState.mode = mode
        annotationView.setMode(mode, preserveSelection)
        annotationHint = when (mode) {
            AnnotationMode.MOVE -> getString(R.string.annotation_move_mode_hint)
            AnnotationMode.DIMENSION -> getString(R.string.annotation_dimension_mode_hint)
            AnnotationMode.TEXT -> getString(R.string.annotation_text_mode_hint)
            AnnotationMode.ANGLE -> getString(R.string.annotation_angle_mode_hint)
            AnnotationMode.AREA -> getString(R.string.annotation_area_mode_hint)
        }
    }

    private fun handleAnnotationCreated(mode: AnnotationMode) {
        if (!pageState.completePlacement(mode)) return
        setEditorMode(AnnotationMode.MOVE, preserveSelection = true)
        annotationHint = getString(R.string.annotation_single_placement_completed_hint)
    }

    private fun selectMarkColor(color: Int) {
        if (pageState.updateColor(color, annotationView::updateSelectedColor)) {
            annotationHint = getString(R.string.annotation_color_changed)
        }
    }

    private fun selectTextSize(textSize: Int) = pageState.updateTextSize(textSize, annotationView::updateSelectedTextSize)

    private fun selectLineThickness(thickness: Int) = pageState.updateThickness(thickness, annotationView::updateSelectedLineThickness)

    private fun dismissSelectionPanel() {
        if (!pageState.properties.visible) return
        finishDistanceEditing()
        pageState.dismissPanel()
    }

    private fun finishDistanceEditing() {
        pageState.dismissFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    private fun updateRecentMeasurements(values: List<Double>) {
        pageState.updateRecentMeasurements(values)
        if (::annotationView.isInitialized) annotationView.defaultDimensionLabel = pageState.defaultDimensionLabel
    }

    private fun handleMeasurementSnapshot(records: List<MeasurementRecord>) {
        val value = pageState.receiveMeasurements(records)
        annotationView.defaultDimensionLabel = pageState.defaultDimensionLabel
        if (value != null && pageState.consumeMeasurementWait()) applyMeasurementToSelected(value, automatic = true)
    }

    private fun applyRecentMeasurement(index: Int) {
        val value = pageState.latestMeasurements.getOrNull(index) ?: return
        if (pageState.properties.dimension == null) {
            toast(getString(R.string.annotation_dimension_selection_required))
            return
        }
        applyMeasurementToSelected(value, automatic = false)
    }

    private fun applyMeasurementToSelected(value: Double, automatic: Boolean) {
        val text = formatAnnotationMillimeters(value)
        if (!pageState.applyMeasurement(value, annotationView::updateSelectedLabel)) return
        if (automatic) {
            annotationHint = getString(R.string.annotation_latest_measurement_applied, text)
            toast(getString(R.string.annotation_dimension_updated, text))
        } else {
            annotationHint = getString(R.string.annotation_measurement_applied, text)
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
        annotationHint = getString(R.string.annotation_share_preparing)
        Thread {
            val sharedUri = runCatching {
                val directory = File(cacheDir, "shared").apply { mkdirs() }
                val file = File(directory, "LaserSketch-share.png")
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
                        annotationHint = getString(R.string.annotation_share_select_wechat)
                    } catch (_: ActivityNotFoundException) {
                        annotationHint = getString(R.string.annotation_share_wechat_missing)
                        toast(getString(R.string.annotation_share_install_wechat))
                    }
                }.onFailure {
                    annotationHint = getString(R.string.annotation_share_generation_error, it.message)
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
        annotationHint = getString(R.string.annotation_gallery_saving)
        Thread {
            var imageUri: Uri? = null
            val saved = runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "LaserSketch-${fileTime()}.png")
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
                    annotationHint = getString(R.string.annotation_gallery_saved_hint)
                    toast(getString(R.string.annotation_gallery_saved))
                }.onFailure {
                    annotationHint = getString(R.string.annotation_gallery_error, it.message)
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
        annotationHint = getString(R.string.annotation_choose_photo_hint)
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
        annotationHint = getString(R.string.project_photo_loading)
        imageJob = measurementScope.launch {
            var bitmap: Bitmap? = null
            var transferred = false
            try {
                bitmap = withContext(Dispatchers.IO) { AndroidPhotoCodec.read(contentResolver, uri) }
                val result = CompletableDeferred<com.example.boschlaser.shared.annotation.PhotoCorrection?>()
                correctionResult = result
                correctionImage = bitmap
                val settings = try { result.await() } finally { correctionImage = null; correctionResult = null }
                if (settings != null) {
                    val original = requireNotNull(bitmap)
                    bitmap = withContext(Dispatchers.Default) { correctBitmap(original, settings, 3000) }
                    original.recycle()
                }
                val jpeg = withContext(Dispatchers.IO) { AndroidPhotoCodec.jpeg(requireNotNull(bitmap)) }
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
                setEditorMode(AnnotationMode.DIMENSION)
                transferred = true
                projectLoaded = true
                loadingNewImage = false
                annotationView.isEnabled = true
                annotationHint = getString(R.string.project_photo_created)
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) {
                loadingNewImage = false
                pendingPhotoProjectId = null
                projectLoaded = currentProjectId != null && currentPhotoAssetId != null && annotationView.hasImage()
                annotationView.isEnabled = true
                currentImageUri = null
                projectServices.acknowledgeError()
                annotationHint = getString(R.string.project_photo_read_failed)
                toast(getString(R.string.project_photo_read_failed))
            } finally { if (!transferred) bitmap?.recycle() }
        }
    }

    private fun loadProject(projectId: String) {
        imageJob?.cancel()
        loadingNewImage = false
        projectLoaded = false
        annotationView.isEnabled = false
        annotationHint = getString(R.string.project_photo_opening)
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
                setEditorMode(AnnotationMode.MOVE)
                transferred = true
                projectLoaded = true
                annotationView.isEnabled = true
                annotationHint = getString(R.string.project_photo_opened, photo.project.manifest.name)
            } catch (failure: CancellationException) { throw failure }
            catch (_: Exception) { projectServices.acknowledgeError(); toast(getString(R.string.project_open_failed)); finish() }
            finally { if (!transferred) bitmap?.recycle() }
        }
    }

    private suspend fun correctBitmap(image: Bitmap, settings: com.example.boschlaser.shared.annotation.PhotoCorrection, maximum: Int): Bitmap {
        val correctionContext = currentCoroutineContext()
        val scale = minOf(1.0, maximum.toDouble() / maxOf(image.width, image.height))
        val source = if (scale < 1) Bitmap.createScaledBitmap(image, maxOf(2, (image.width * scale).toInt()), maxOf(2, (image.height * scale).toInt()), true) else image
        try {
            val pixels = IntArray(source.width * source.height)
            source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
            val result = com.example.boschlaser.shared.annotation.correctPhoto(pixels, source.width, source.height, settings, maximum, checkCancelled = { correctionContext.ensureActive() })
            return Bitmap.createBitmap(result.pixels, result.width, result.height, Bitmap.Config.ARGB_8888)
        } finally { if (source !== image) source.recycle() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun applyWindowInsets(root: android.view.View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // AppCompat already accounts for the ActionBar height.
            view.setPadding(0, bars.top, 0, max(dp(10), bars.bottom))
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun fileTime(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
}
