package com.example.boschlaser

import com.example.boschlaser.shared.editor.WallEditOperation
import com.example.boschlaser.shared.editor.WallEditSession
import com.example.boschlaser.shared.editor.WallEditUpdate
import com.example.boschlaser.shared.command.WallEditStatus

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.os.Bundle
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.boschlaser.ui.CreationPropertyTools
import com.example.boschlaser.ui.DrawingWallTools
import androidx.compose.material3.AlertDialog
import com.example.boschlaser.ui.WallControlLinePicker
import com.example.boschlaser.ui.DrawingSettingsDialog
import com.example.boschlaser.ui.DrawingSettingsLabels
import com.example.boschlaser.shared.model.WallAlignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.dimensionResource
import com.example.boschlaser.ui.SketchScreen
import com.example.boschlaser.ui.SketchScreenStyle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalDensity
import com.example.boschlaser.ui.SketchPropertySheet
import com.example.boschlaser.ui.SketchPropertySheetStyle
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.boschlaser.ui.NumberProperty
import com.example.boschlaser.ui.ColumnSizeProperty
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.colorResource
import com.example.boschlaser.ui.SketchToolbar
import com.example.boschlaser.ui.SketchToolItem
import com.example.boschlaser.ui.SketchToolButtons
import com.example.boschlaser.ui.SketchEditTools
import com.example.boschlaser.ui.SketchBottomControls
import com.example.boschlaser.ui.EditorActions
import com.example.boschlaser.ui.SelectionActions
import com.example.boschlaser.ui.SelectionActionTarget
import com.example.boschlaser.shared.editor.ToolType
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.roundToInt

class SketchActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_PROJECT_ID = "sketch_project_id"
        const val EXTRA_MEASUREMENTS_MM = "sketch_measurements_mm"
        private const val PREFS_NAME = "sketch_settings"
        private const val PREF_GRID_DISPLAY_STEP_MM = "grid_display_step_mm"
        private const val PREF_MAGNIFIER_ZOOM = "magnifier_zoom"
        private const val PREF_AUTOMATIC_DIMENSIONS = "show_automatic_dimensions"
    }

    private lateinit var sketchView: SketchView
    private var propertyPanelPresent by mutableStateOf(false)
    private var propertyPanelResetKey by mutableIntStateOf(0)
    private var animatePropertyDismiss by mutableStateOf(false)
    private var propertySelection by mutableStateOf<SketchSelection?>(null)
    private var propertyPanelVisible by mutableStateOf(false)
    private var hasSelection by mutableStateOf(false)
    private var selectionActionTarget by mutableStateOf(SelectionActionTarget.NONE)
    private var gridSnapEnabled by mutableStateOf(true)
    private var projectId: String? = null
    private var settingsVisible by mutableStateOf(false)
    private var editingFaceDimension by mutableStateOf<com.example.boschlaser.shared.model.WallDimension?>(null)
    private var settingsGrid = ""
    private var settingsMagnifier = ""
    private var settingsAutomaticDimensions = true
    private var detachMultiWayEndpoints by mutableStateOf(true)
    private var currentMode by mutableStateOf(SketchMode.SELECT)
    private var creationProperty by mutableStateOf<Int?>(null)
    private var creationSettingsRevision by mutableIntStateOf(0)
    private var drawingColumnFill by mutableStateOf(com.example.boschlaser.shared.model.ColumnFill.BLACK)
    private var drawingFill by mutableStateOf(com.example.boschlaser.shared.model.WallFill.BLANK)
    private var drawingThickness by mutableStateOf(240f)
    private var drawingThicknessVisible by mutableStateOf(false)
    private var drawingControlLine by mutableStateOf(SketchWallControlLine.CENTER)
    private var wallEditSession by mutableStateOf<WallEditSession?>(null)
    private val joinWallsActive get() = wallEditSession?.operation == WallEditOperation.TRIM
    private val extendWallActive get() = wallEditSession?.operation == WallEditOperation.EXTEND
    private var systemBottomInset by mutableIntStateOf(0)
    private var systemTopInset by mutableIntStateOf(0)

    private val measurementScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val projectServices by lazy { ProjectServices.get(this) }
    private var projectLoaded = false
    private var previousOpeningDocument: com.example.boschlaser.shared.model.SketchDocument? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        projectId = savedInstanceState?.getString(EXTRA_PROJECT_ID) ?: intent.getStringExtra(EXTRA_PROJECT_ID)
        detachMultiWayEndpoints = savedInstanceState?.getBoolean("detach_multi_way_endpoints", true) ?: true
        buildInterface()
        sketchView.setDetachMultiWayEndpoints(detachMultiWayEndpoints)
        measurementScope.launch {
            projectServices.migrationNotices.collect { pending ->
                if (pending) { toast(getString(R.string.project_migration_notice)); projectServices.acknowledgeMigrationNotice() }
            }
        }
        val id = projectId
        if (id == null) {
            toast(getString(R.string.sketch_project_missing))
            finish()
        } else {
            sketchView.isEnabled = false
            measurementScope.launch {
                try {
                    val project = projectServices.loadSketch(id).await()
                    if (project == null) { toast(getString(R.string.project_open_failed)); finish() }
                    else {
                        projectId = project.manifest.projectId
                        sketchView.restoreDocument(project.sketch, adoptWallThickness = true)
                        previousOpeningDocument = project.sketch
                        projectLoaded = true
                        sketchView.isEnabled = true
                    }
                } catch (failure: CancellationException) { throw failure }
                catch (_: Exception) { projectServices.acknowledgeError(); toast(getString(R.string.project_open_failed)); finish() }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        projectId?.let { outState.putString(EXTRA_PROJECT_ID, it) }
        outState.putBoolean("detach_multi_way_endpoints", detachMultiWayEndpoints)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        sketchView.cancelGesture()
        saveProject()
        super.onStop()
    }

    override fun onDestroy() {
        measurementScope.cancel()
        super.onDestroy()
    }

    private fun buildInterface() {
        sketchView = SketchView(this).apply {
            onSelectionChanged = {
                drawingControlLine = wallControlLine()
                drawingThickness = drawingWallThickness()
                if (!joinWallsActive && !extendWallActive) showSelection(it)
            }
            onStateChanged = {
                val document = snapshotDocument()
                val previous = previousOpeningDocument
                previousOpeningDocument = document
                if (projectLoaded && previous != null &&
                    com.example.boschlaser.shared.geometry.hasEditedOpeningOverlap(previous, document)) {
                    this@SketchActivity.toast(getString(R.string.sketch_openings_overlap_warning))
                }
                if (!this@SketchActivity.saveProject()) {
                    this@SketchActivity.toast(getString(R.string.sketch_auto_save_failed))
                }
            }
            onWallEditSessionChanged = { wallEditSession = it }
            onWallEditUpdated = { handleWallEditUpdate(it) }
            onDimensionPlaced = { this@SketchActivity.setMode(SketchMode.SELECT) }
            onFaceDimensionRequested = { editingFaceDimension = it }
        }
        @Composable fun Settings() {
            if (settingsVisible) {
                MaterialTheme(colorScheme = lightColorScheme(primary = colorResource(R.color.sketch_tool_active))) {
                    DrawingSettingsDialog(settingsGrid, settingsMagnifier, settingsAutomaticDimensions,
                        DrawingSettingsLabels(getString(R.string.sketch_settings_title), getString(R.string.sketch_settings_grid),
                            getString(R.string.sketch_settings_grid_hint),
                            getString(R.string.sketch_settings_magnifier, SketchView.MIN_MAGNIFIER_ZOOM.toInt(), SketchView.MAX_MAGNIFIER_ZOOM.toInt()),
                            getString(R.string.sketch_settings_magnifier_hint), getString(R.string.sketch_property_apply),
                            getString(R.string.sketch_settings_cancel), getString(R.string.sketch_settings_automatic_dimensions),
                            getString(R.string.sketch_settings_grid_invalid, 10, 2000),
                            getString(R.string.sketch_settings_magnifier_invalid, 1, 8)),
                        onApply = ::applyDrawingSettings, onDismiss = { settingsVisible = false })
                }
            }
        }

        @Composable fun Toolbar() {
            MaterialTheme(colorScheme = lightColorScheme(primary = colorResource(R.color.sketch_tool_active))) {
                SketchToolbar(ToolType.valueOf(currentMode.name), listOf(
                    SketchToolItem(ToolType.SELECT, getString(R.string.sketch_tool_select)),
                    SketchToolItem(ToolType.WALL, getString(R.string.sketch_tool_wall)),
                    SketchToolItem(ToolType.RECT_COLUMN, getString(R.string.sketch_tool_column)),
                    SketchToolItem(ToolType.DOOR, getString(R.string.sketch_tool_door)),
                    SketchToolItem(ToolType.WINDOW, getString(R.string.sketch_tool_window)),
                    SketchToolItem(ToolType.DIMENSION, getString(R.string.sketch_tool_dimension))),
                    getString(R.string.sketch_back), getString(R.string.sketch_settings),
                    onBack = { finish() }, onSettings = ::showDrawingSettings,
                    onTool = { setMode(SketchMode.valueOf(it.name)) },
                    backIcon = { Icon(painterResource(R.drawable.ic_back), contentDescription = null) },
                    settingsIcon = { Icon(painterResource(R.drawable.ic_settings), contentDescription = null) },
                    toolIcon = { tool -> Icon(painterResource(when (tool) {
                        ToolType.SELECT -> R.drawable.ic_select
                        ToolType.WALL -> R.drawable.ic_wall
                        ToolType.RECT_COLUMN, ToolType.CIRCLE_COLUMN -> R.drawable.ic_column
                        ToolType.DOOR -> R.drawable.ic_door
                        ToolType.WINDOW -> R.drawable.ic_window
                        ToolType.MEASURE -> R.drawable.ic_measure
                        ToolType.SPLIT -> R.drawable.ic_split
                        ToolType.DIMENSION -> R.drawable.ic_dimension
                    }), contentDescription = null) })
            }
        }

        @Composable fun EditToolbar() {
            MaterialTheme(colorScheme = lightColorScheme(primary = colorResource(R.color.sketch_tool_active))) {
                if (currentMode == SketchMode.WALL) {
                    DrawingWallTools(when (drawingControlLine) {
                        SketchWallControlLine.CENTER -> WallAlignment.CENTER
                        SketchWallControlLine.INNER -> WallAlignment.LEFT
                        SketchWallControlLine.OUTER -> WallAlignment.RIGHT
                    }, getString(R.string.wall_control_line_label), listOf(
                        getString(R.string.wall_control_line_center), getString(R.string.wall_control_line_inner),
                        getString(R.string.wall_control_line_outer)),
                        getString(R.string.sketch_drawing_wall_thickness, formatSketchMillimeters(drawingThickness.toDouble())),
                        onAlignment = { alignment ->
                            sketchView.setDrawingWallControlLine(when (alignment) {
                                WallAlignment.CENTER -> SketchWallControlLine.CENTER
                                WallAlignment.LEFT -> SketchWallControlLine.INNER
                                WallAlignment.RIGHT -> SketchWallControlLine.OUTER
                            })
                            drawingControlLine = sketchView.wallControlLine()
                        }, onThickness = { drawingThicknessVisible = true },
                        fill = drawingFill, fillLabels = listOf(getString(R.string.wall_fill_blank),
                            getString(R.string.wall_fill_hatch), getString(R.string.wall_fill_black)),
                        onFill = { drawingFill = it; sketchView.drawingWallFill = it })
                    if (drawingThicknessVisible) {
                        AlertDialog(onDismissRequest = { drawingThicknessVisible = false },
                            confirmButton = {}, text = {
                                NumberInput(formatSketchMillimeters(drawingThickness.toDouble()),
                                    getString(R.string.sketch_wall_thickness), getString(R.string.sketch_wall_thickness_apply),
                                    errorLabel = getString(R.string.sketch_wall_thickness_invalid), wallThicknessPresets = true) { text ->
                                    val applied = text.toDoubleOrNull()?.let { sketchView.setDrawingWallThickness(it) } == true
                                    if (applied) {
                                        drawingThickness = sketchView.drawingWallThickness()
                                        drawingThicknessVisible = false
                                    }
                                    applied
                                }
                            })
                    }
                } else if (currentMode in listOf(SketchMode.RECT_COLUMN, SketchMode.CIRCLE_COLUMN, SketchMode.DOOR, SketchMode.WINDOW)) {
                    CreationTools()
                } else if (currentMode in listOf(SketchMode.SELECT, SketchMode.SPLIT, SketchMode.MEASURE)) {
                    SketchEditTools(joinWallsActive, extendWallActive,
                        getString(if (joinWallsActive) R.string.sketch_join_cancel else R.string.sketch_join),
                        getString(if (extendWallActive) R.string.sketch_extend_cancel else R.string.sketch_extend),
                        onJoin = { if (joinWallsActive) stopWallJoinMode() else startWallJoinMode() },
                        onExtend = { if (extendWallActive) stopWallExtendMode() else startWallExtendMode() },
                        joinIcon = { Icon(painterResource(R.drawable.ic_join_walls), contentDescription = null) },
                        extendIcon = { Icon(painterResource(R.drawable.ic_extend_wall), contentDescription = null) },
                        additionalTools = {
                            SketchToolButtons(ToolType.valueOf(currentMode.name), listOf(
                                SketchToolItem(ToolType.SPLIT, getString(R.string.sketch_tool_split)),
                                SketchToolItem(ToolType.MEASURE, getString(R.string.sketch_tool_measure))),
                                onTool = { setMode(SketchMode.valueOf(it.name)) },
                                toolIcon = { tool -> Icon(painterResource(
                                    if (tool == ToolType.MEASURE) R.drawable.ic_measure else R.drawable.ic_split),
                                    contentDescription = null) })
                        })
                }
            }
        }

        @Composable fun GridSnap() {
            MaterialTheme {
                SketchBottomControls(gridSnapEnabled, getString(R.string.sketch_grid_snap),
                    !detachMultiWayEndpoints,
                    getString(R.string.sketch_endpoint_move_together),
                    onGridChange = { enabled ->
                        gridSnapEnabled = enabled
                        sketchView.setGridSnapEnabled(enabled)
                    }, onNodeChange = { moveTogether ->
                        detachMultiWayEndpoints = !moveTogether
                        sketchView.setDetachMultiWayEndpoints(detachMultiWayEndpoints)
                    })
            }
        }

        @Composable fun PlacementActions() {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = colorResource(R.color.sketch_tool_active),
                secondary = colorResource(R.color.editor_action_neutral),
                error = colorResource(R.color.editor_action_delete),
                onPrimary = colorResource(R.color.white),
                onSecondary = colorResource(R.color.white),
                onError = colorResource(R.color.white),
            )) {
                EditorActions(
                    showDelete = hasSelection && currentMode == SketchMode.SELECT,
                    showConfirm = currentMode != SketchMode.SELECT,
                    undoLabel = getString(R.string.sketch_action_undo),
                    deleteLabel = getString(R.string.sketch_action_delete),
                    confirmLabel = getString(R.string.sketch_action_confirm),
                    onUndo = { if (!sketchView.undoPlacement()) toast(getString(R.string.sketch_undo_empty)) },
                    onDelete = { if (!sketchView.deleteSelected()) toast(getString(R.string.sketch_selection_required)) },
                    onConfirm = { setMode(SketchMode.SELECT) },
                    undoIcon = { Icon(painterResource(R.drawable.ic_undo), null) },
                    deleteIcon = { Icon(painterResource(R.drawable.ic_delete), null) },
                    confirmIcon = { Icon(painterResource(R.drawable.ic_confirm), null) },
                )
            }
        }

        @Composable fun SelectionTools() {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = colorResource(R.color.sketch_tool_active),
                onPrimary = colorResource(R.color.white),
            )) {
                SelectionActions(
                    target = selectionActionTarget,
                    straightenLabel = getString(R.string.sketch_action_straighten),
                    hingeLabel = getString(R.string.door_flip_left_right),
                    sideLabel = getString(R.string.door_flip_up_down),
                    onStraighten = {
                        if (!sketchView.straightenSelectedWall(false)) toast(getString(R.string.sketch_wall_required))
                    },
                    onFlipHinge = {
                        sketchView.selectedOpening()?.takeIf { it.type == SketchOpeningType.DOOR }
                            ?.let { sketchView.updateSelectedOpening(it.width, flipHinge = true) }
                    },
                    onFlipSide = {
                        sketchView.selectedOpening()?.takeIf { it.type == SketchOpeningType.DOOR }
                            ?.let { sketchView.updateSelectedOpening(it.width, flip = true) }
                    },
                    straightenIcon = { Icon(painterResource(R.drawable.ic_straighten), null) },
                    hingeIcon = { Icon(painterResource(R.drawable.ic_flip_horizontal), null) },
                    sideIcon = { Icon(painterResource(R.drawable.ic_flip_vertical), null) },
                )
            }
        }

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).let { preferences ->
            preferences.getFloat(PREF_GRID_DISPLAY_STEP_MM, SketchView.DEFAULT_GRID_DISPLAY_STEP_MM)
                .let { sketchView.setGridDisplayStepMm(it) }
            preferences.getFloat(PREF_MAGNIFIER_ZOOM, SketchView.DEFAULT_MAGNIFIER_ZOOM)
                .let { sketchView.setMagnifierZoom(it) }
            sketchView.setShowAutomaticDimensions(preferences.getBoolean(PREF_AUTOMATIC_DIMENSIONS, true))
        }
        val root = FrameLayout(this)
        root.addView(ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val density = resources.displayMetrics.density
                SketchScreen(propertyPanelVisible || propertyPanelPresent, (systemTopInset / density).dp, (systemBottomInset / density).dp,
                    screenStyle(),
                    canvas = { AndroidView(factory = { sketchView }, modifier = Modifier.fillMaxSize()) },
                    toolbar = { Toolbar() }, editToolbar = { EditToolbar() },
                    actions = { PlacementActions() }, selectionActions = { SelectionTools() },
                    gridSnap = { GridSnap() },
                    guidance = {
                        Text(getString(when {
                            wallEditSession?.first != null -> R.string.sketch_wall_edit_preview_hint
                            wallEditSession != null -> R.string.sketch_extend_pick_first
                            currentMode == SketchMode.DIMENSION -> R.string.sketch_dimension_hint
                            currentMode == SketchMode.WALL -> R.string.sketch_wall_chain_hint
                            else -> R.string.sketch_selection_gestures_hint
                        }))
                    },
                    properties = { Properties() },
                    dialogs = { Settings(); FaceDistanceDialog() })
            }
        }, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            systemTopInset = bars.top
            systemBottomInset = bars.bottom
            sketchView.setMagnifierTopOffset(systemTopInset + resources.getDimension(R.dimen.sketch_magnifier_top_offset))
            insets
        }
        ViewCompat.requestApplyInsets(root)
        setMode(SketchMode.SELECT)
    }

    @Composable
    private fun screenStyle() = SketchScreenStyle(
        colorResource(R.color.sketch_toolbar_background), dimensionResource(R.dimen.sketch_toolbar_elevation),
        dimensionResource(R.dimen.sketch_edit_toolbar_height), dimensionResource(R.dimen.sketch_actions_top),
        dimensionResource(R.dimen.sketch_actions_end), dimensionResource(R.dimen.sketch_actions_height),
        dimensionResource(R.dimen.sketch_selection_actions_end), dimensionResource(R.dimen.sketch_selection_actions_width),
        dimensionResource(R.dimen.sketch_bottom_margin), dimensionResource(R.dimen.sketch_properties_horizontal_margin),
        dimensionResource(R.dimen.sketch_properties_gap),
    )

    private fun setMode(mode: SketchMode) {
        if (joinWallsActive) stopWallJoinMode()
        if (extendWallActive) stopWallExtendMode()
        creationProperty = null
        drawingThicknessVisible = false
        currentMode = mode
        sketchView.setMode(mode)
        drawingControlLine = sketchView.wallControlLine()
        drawingThickness = sketchView.drawingWallThickness()
        when (mode) {
            SketchMode.MEASURE -> toast(getString(R.string.sketch_measure_hint))
            SketchMode.SPLIT -> toast(getString(R.string.sketch_split_hint))
            SketchMode.DIMENSION -> toast(getString(R.string.sketch_dimension_hint))
            else -> Unit
        }
        when (mode) {
            SketchMode.SELECT -> Unit
            else -> hideProperties()
        }
    }

    private fun startWallJoinMode() {
        setMode(SketchMode.SELECT)
        sketchView.startWallEdit(WallEditOperation.TRIM)
        showSelection(null)
    }

    private fun stopWallJoinMode() = sketchView.stopWallEdit()

    private fun startWallExtendMode() {
        setMode(SketchMode.SELECT)
        sketchView.startWallEdit(WallEditOperation.EXTEND)
        showSelection(null)
        toast(getString(R.string.sketch_extend_pick_first))
    }

    private fun stopWallExtendMode() = sketchView.stopWallEdit()

    private fun handleWallEditUpdate(update: WallEditUpdate) {
        val message = when (update.status) {
            null -> if (update.operation == WallEditOperation.EXTEND) R.string.sketch_extend_pick_boundary else null
            WallEditStatus.SUCCESS -> if (update.operation == WallEditOperation.TRIM)
                R.string.sketch_trim_success else R.string.sketch_extend_success
            WallEditStatus.SAME_WALL -> R.string.sketch_edit_different_walls
            WallEditStatus.WALL_NOT_FOUND -> if (update.operation == WallEditOperation.EXTEND)
                R.string.sketch_edit_wall_changed else null
            WallEditStatus.PARALLEL -> if (update.operation == WallEditOperation.TRIM)
                R.string.sketch_trim_parallel else R.string.sketch_extend_parallel
            WallEditStatus.ALREADY_REACHES -> R.string.sketch_extend_already_reaches
            WallEditStatus.TARGET_MISSED -> R.string.sketch_extend_target_missed
            WallEditStatus.OPENING_CONFLICT, WallEditStatus.INVALID_LENGTH -> if (update.operation == WallEditOperation.TRIM)
                R.string.sketch_trim_opening_conflict else R.string.sketch_extend_opening_conflict
        }
        if (message != null) toast(getString(message))
    }

    private fun showSelection(selection: SketchSelection?) {
        propertySelection = selection
        propertyPanelResetKey++
        animatePropertyDismiss = false
        hasSelection = selection != null
        selectionActionTarget = when {
            currentMode != SketchMode.SELECT -> SelectionActionTarget.NONE
            selection is SketchSelection.Wall -> SelectionActionTarget.WALL
            selection is SketchSelection.Opening && selection.opening.type == SketchOpeningType.DOOR -> SelectionActionTarget.DOOR
            else -> SelectionActionTarget.NONE
        }
        if (selection is SketchSelection.Dimension || currentMode != SketchMode.SELECT) {
            hideProperties()
        } else if (selection == null) {
            hideProperties()
        } else propertyPanelVisible = true
    }

    private fun hideProperties() {
        animatePropertyDismiss = false
        propertyPanelVisible = false
    }

    @Composable
    private fun Properties() {
        val selection = propertySelection
        val title = when (selection) {
            is SketchSelection.Dimension -> getString(R.string.sketch_dimension_properties)
            is SketchSelection.Wall -> getString(R.string.sketch_wall_properties)
            is SketchSelection.Column -> getString(if (selection.column.type == SketchColumnType.CIRCLE)
                R.string.sketch_circle_properties else R.string.sketch_rectangle_properties)
            is SketchSelection.Opening -> getString(if (selection.opening.type == SketchOpeningType.DOOR)
                R.string.sketch_door_properties else R.string.sketch_window_properties)
            null -> ""
        }
        SketchPropertySheet(propertyPanelVisible, propertyPanelResetKey, animatePropertyDismiss, title,
            propertySheetStyle(), ViewConfiguration.get(this).scaledTouchSlop.toFloat(),
            onDismiss = { animatePropertyDismiss = true; propertyPanelVisible = false },
            onPresenceChanged = { propertyPanelPresent = it },
            headerAccessory = {
                (selection as? SketchSelection.Wall)?.let { WallReferenceLine(it.wall.id) }
                (selection as? SketchSelection.Opening)?.takeIf { it.opening.type == SketchOpeningType.DOOR }?.let {
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        OpeningProperties(it.opening.id)
                    }
                }
            }) {
            key(propertyPanelResetKey) {
                when (selection) {
                    is SketchSelection.Dimension -> Unit
                    is SketchSelection.Wall -> WallProperties(selection.wall.id)
                    is SketchSelection.Column -> ColumnProperties(selection.column.id)
                    is SketchSelection.Opening -> if (selection.opening.type != SketchOpeningType.DOOR) {
                        OpeningProperties(selection.opening.id)
                    }
                    null -> Unit
                }
            }
        }
    }

    @Composable
    private fun propertySheetStyle() = SketchPropertySheetStyle(
        colorResource(R.color.sketch_properties_background), colorResource(R.color.sketch_properties_title),
        with(LocalDensity.current) { resources.getDimension(R.dimen.sketch_properties_title_size).toSp() },
        dimensionResource(R.dimen.sketch_properties_horizontal_padding), dimensionResource(R.dimen.sketch_properties_top_padding),
        dimensionResource(R.dimen.sketch_properties_bottom_padding), dimensionResource(R.dimen.sketch_properties_elevation),
        dimensionResource(R.dimen.sketch_properties_dismiss_minimum),
    )

    @Composable
    private fun FaceDistanceDialog() {
        val dimension = editingFaceDimension ?: return
        val records by MeasurementServices.get(this).service.records.collectAsStateWithLifecycle()
        val value = com.example.boschlaser.shared.geometry.WallDimensionGeometry.layout(sketchView.snapshotDocument(), dimension)?.valueMm
            ?: return
        MaterialTheme(colorScheme = lightColorScheme(primary = colorResource(R.color.sketch_tool_active))) {
            com.example.boschlaser.ui.WallFaceDistanceDialog(formatSketchMillimeters(value),
                getString(R.string.sketch_dimension_properties), getString(if (dimension.first is com.example.boschlaser.shared.model.WallFaceReference &&
                    dimension.second is com.example.boschlaser.shared.model.WallFaceReference && dimension.first.entityId == dimension.second.entityId)
                    R.string.sketch_wall_length_dimension_hint else R.string.sketch_wall_face_drive_hint),
                getString(R.string.sketch_dimension_value), getString(R.string.sketch_dimension_apply),
                getString(R.string.sketch_settings_cancel), getString(R.string.sketch_dimension_drive_failed),
                onApply = { text ->
                    val requested = text.toDoubleOrNull()
                    val applied = requested != null && sketchView.applyWallFaceDistance(dimension, requested)
                    if (applied) editingFaceDimension = null
                    applied
                }, onDismiss = { editingFaceDimension = null },
                recentMeasurements = records.asReversed().take(3).map { formatSketchMillimeters(it.measurement.valueMm) },
                recentMeasurementsLabel = getString(R.string.sketch_recent_measurements))
        }
    }

    @Composable
    private fun WallReferenceLine(wallId: String) {
        val wall = sketchView.renderDocument().walls[wallId] ?: return
        val alignment = wall.alignment
        if (currentMode != SketchMode.WALL) MaterialTheme(colorScheme = lightColorScheme(primary = colorResource(R.color.sketch_tool_active))) {
            WallControlLinePicker(alignment, getString(R.string.wall_control_line_label), listOf(
                getString(R.string.wall_control_line_center), getString(R.string.wall_control_line_inner),
                getString(R.string.wall_control_line_outer)), showLabel = false) { selected ->
                val controlLine = when (selected) {
                    WallAlignment.CENTER -> SketchWallControlLine.CENTER
                    WallAlignment.LEFT -> SketchWallControlLine.INNER
                    WallAlignment.RIGHT -> SketchWallControlLine.OUTER
                }
                sketchView.updateSelectedWallControlLine(controlLine)
            }
        }
    }

    @Composable
    private fun WallProperties(wallId: String) {
        val wall = sketchView.renderDocument().walls[wallId] ?: return
        var thicknessVisible by remember(wallId) { mutableStateOf(false) }
        val thicknessLabel = getString(R.string.sketch_drawing_wall_thickness, formatSketchMillimeters(wall.thickness))
        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.material3.TextButton(onClick = { thicknessVisible = true }) {
                Text(thicknessLabel)
            }
            com.example.boschlaser.ui.WallFillPicker(wall.fill, listOf(getString(R.string.wall_fill_blank),
                getString(R.string.wall_fill_hatch), getString(R.string.wall_fill_black))) {
                sketchView.updateSelectedWallFill(it)
            }
        }
        if (thicknessVisible) {
            AlertDialog(onDismissRequest = { thicknessVisible = false }, confirmButton = {},
                dismissButton = { androidx.compose.material3.TextButton(onClick = { thicknessVisible = false }) {
                    Text(getString(R.string.sketch_settings_cancel))
                } }, text = {
                NumberInput(formatSketchMillimeters(wall.thickness), getString(R.string.sketch_wall_thickness),
                    getString(R.string.sketch_wall_thickness_apply), errorLabel = getString(R.string.sketch_wall_thickness_invalid),
                    wallThicknessPresets = true) { text ->
                    val applied = text.toDoubleOrNull()?.let { sketchView.updateSelectedWallThickness(it) } == true
                    if (applied) thicknessVisible = false
                    applied
                }
            })
        }
    }

    @Composable
    private fun CreationTools() {
        // Reading this revision refreshes summaries after applying creation settings.
        val revision = creationSettingsRevision
        val column = currentMode == SketchMode.RECT_COLUMN || currentMode == SketchMode.CIRCLE_COLUMN
        val circular = currentMode == SketchMode.CIRCLE_COLUMN
        val door = currentMode == SketchMode.DOOR
        val type = if (door) SketchOpeningType.DOOR else SketchOpeningType.WINDOW
        val labels = if (column) listOfNotNull(
            getString(R.string.sketch_creation_column_size,
                formatSketchMillimeters(sketchView.placementColumnWidth),
                formatSketchMillimeters(sketchView.placementColumnDepth)),
            if (!circular) getString(R.string.sketch_creation_column_angle,
                sketchView.placementColumnRotation.toString().removeSuffix(".0")) else null)
        else listOfNotNull(getString(R.string.sketch_creation_opening_width,
            formatSketchMillimeters(sketchView.placementOpeningWidth(type).toDouble())),
            if (door) getString(when (sketchView.placementDoorStyle) {
                com.example.boschlaser.shared.model.DoorStyle.SINGLE -> R.string.sketch_door_single
                com.example.boschlaser.shared.model.DoorStyle.DOUBLE -> R.string.sketch_door_double
                com.example.boschlaser.shared.model.DoorStyle.SLIDING -> R.string.sketch_door_sliding
                com.example.boschlaser.shared.model.DoorStyle.OPENING -> R.string.sketch_door_opening
            }) else null)
        key(revision) {
            if (column) com.example.boschlaser.ui.ColumnPropertyTools(drawingColumnFill,
                listOf(getString(R.string.column_fill_white), getString(R.string.column_fill_black)), labels,
                onFill = { drawingColumnFill = it; sketchView.drawingColumnFill = it },
                onProperty = { creationProperty = it })
            else CreationPropertyTools(labels) { creationProperty = it }
        }
        val property = creationProperty ?: return
        fun applied() { creationSettingsRevision++; creationProperty = null }
        AlertDialog(onDismissRequest = { creationProperty = null }, confirmButton = {},
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { creationProperty = null }) {
                    Text(getString(R.string.sketch_settings_cancel))
                }
            }, text = {
            when {
                column && property == 0 -> {
                    var width by remember { mutableStateOf(TextFieldValue(formatSketchMillimeters(sketchView.placementColumnWidth))) }
                    var depth by remember { mutableStateOf(TextFieldValue(formatSketchMillimeters(sketchView.placementColumnDepth))) }
                    var error by remember { mutableStateOf<String?>(null) }
                    ColumnSizeProperty(width, if (circular) null else depth,
                        getString(R.string.sketch_column_width), getString(R.string.sketch_column_depth),
                        getString(R.string.sketch_property_apply),
                        onWidth = { width = it; error = null }, onDepth = { depth = it; error = null },
                        onApply = {
                            val w = width.text.toDoubleOrNull()
                            val d = if (circular) w else depth.text.toDoubleOrNull()
                            if (w != null && d != null && sketchView.updatePlacementColumnSize(w, d)) applied()
                            else error = getString(R.string.sketch_column_size_invalid)
                        }, error = error)
                }
                column -> NumberInput(sketchView.placementColumnRotation.toString().removeSuffix(".0"),
                    getString(R.string.column_rotation_angle), errorLabel = getString(R.string.column_rotation_invalid), columnAnglePresets = true) {
                    val success = it.toDoubleOrNull()?.let(sketchView::updatePlacementColumnRotation) == true
                    if (success) applied()
                    success
                }
                property == 0 -> NumberInput(formatSketchMillimeters(sketchView.placementOpeningWidth(type).toDouble()),
                    getString(R.string.sketch_opening_width), errorLabel = getString(R.string.sketch_opening_placement_width_invalid), openingWidthPresets = true) {
                    val success = it.toFloatOrNull()?.let { width -> sketchView.updatePlacementOpeningWidth(type, width) } == true
                    if (success) applied()
                    success
                }
                else -> DoorStylePicker(sketchView.placementDoorStyle) {
                    sketchView.updatePlacementDoorStyle(it)
                    applied()
                }
            }
        })
    }

    @Composable
    private fun ColumnProperties(columnId: String) {
        val column = sketchView.renderDocument().columns[columnId] ?: return
        val rectangle = column as? com.example.boschlaser.shared.model.RectColumn
        val columnWidth = rectangle?.width ?: (column as com.example.boschlaser.shared.model.CircularColumn).diameter
        val columnDepth = rectangle?.depth ?: columnWidth
        var property by remember(columnId) { mutableStateOf<Int?>(null) }
        val labels = listOfNotNull(getString(R.string.sketch_creation_column_size,
            formatSketchMillimeters(columnWidth), formatSketchMillimeters(columnDepth)),
            rectangle?.let { getString(R.string.sketch_creation_column_angle, formatSketchMillimeters(it.rotationDegrees)) })
        com.example.boschlaser.ui.ColumnPropertyTools(column.fill,
            listOf(getString(R.string.column_fill_white), getString(R.string.column_fill_black)), labels,
            onFill = { sketchView.updateSelectedColumnFill(it) }, onProperty = { property = it })
        val editing = property ?: return
        AlertDialog(onDismissRequest = { property = null }, confirmButton = {},
            dismissButton = { androidx.compose.material3.TextButton(onClick = { property = null }) {
                Text(getString(R.string.sketch_settings_cancel))
            } }, text = {
                if (editing == 0) {
                    var width by remember { mutableStateOf(TextFieldValue(formatSketchMillimeters(columnWidth))) }
                    var depth by remember { mutableStateOf(TextFieldValue(formatSketchMillimeters(columnDepth))) }
                    var sizeError by remember { mutableStateOf<String?>(null) }
                    ColumnSizeProperty(width, if (rectangle != null) depth else null,
                        getString(R.string.sketch_column_width), getString(R.string.sketch_column_depth),
                        getString(R.string.sketch_property_apply), onWidth = { width = it; sizeError = null }, onDepth = { depth = it; sizeError = null },
                        onApply = {
                            val w = width.text.toDoubleOrNull()
                            val d = if (rectangle != null) depth.text.toDoubleOrNull() else w
                            if (w != null && d != null && sketchView.updateSelectedColumnSize(w, d)) property = null
                            else sizeError = getString(R.string.sketch_column_size_invalid)
                        }, error = sizeError)
                } else if (rectangle != null) {
                    NumberInput(formatSketchMillimeters(rectangle.rotationDegrees),
                        getString(R.string.column_rotation_angle), getString(R.string.column_rotation_apply),
                        errorLabel = getString(R.string.column_rotation_invalid), columnAnglePresets = true) { text ->
                        val success = text.toDoubleOrNull()?.let(sketchView::updateSelectedColumnRotation) == true
                        if (success) property = null
                        success
                    }
                }
            })
    }

    @Composable
    private fun OpeningProperties(openingId: String) {
        val opening = sketchView.renderDocument().openings[openingId] ?: return
        if (opening.type == com.example.boschlaser.shared.model.OpeningType.DOOR) {
            var styleVisible by remember(openingId) { mutableStateOf(false) }
            val styleSummary = getString(when (opening.doorStyle) {
                com.example.boschlaser.shared.model.DoorStyle.SINGLE -> R.string.sketch_door_single
                com.example.boschlaser.shared.model.DoorStyle.DOUBLE -> R.string.sketch_door_double
                com.example.boschlaser.shared.model.DoorStyle.SLIDING -> R.string.sketch_door_sliding
                com.example.boschlaser.shared.model.DoorStyle.OPENING -> R.string.sketch_door_opening
            })
            androidx.compose.material3.TextButton(onClick = { styleVisible = true },
                modifier = androidx.compose.ui.Modifier.semantics { contentDescription = styleSummary }) {
                Text(styleSummary)
            }
            if (styleVisible) AlertDialog(onDismissRequest = { styleVisible = false }, confirmButton = {},
                dismissButton = { androidx.compose.material3.TextButton(onClick = { styleVisible = false }) {
                    Text(getString(R.string.sketch_settings_cancel))
                } }, text = {
                    DoorStylePicker(opening.doorStyle) {
                        if (sketchView.updateSelectedDoorStyle(it)) styleVisible = false
                    }
                })
        }
        var widthVisible by remember(openingId) { mutableStateOf(false) }
        val widthSummary = getString(R.string.sketch_creation_opening_width, formatSketchMillimeters(opening.width))
        androidx.compose.material3.TextButton(onClick = { widthVisible = true },
            modifier = androidx.compose.ui.Modifier.semantics { contentDescription = widthSummary }) {
            Text(widthSummary)
        }
        if (widthVisible) AlertDialog(onDismissRequest = { widthVisible = false }, confirmButton = {},
            dismissButton = { androidx.compose.material3.TextButton(onClick = { widthVisible = false }) {
                Text(getString(R.string.sketch_settings_cancel))
            } }, text = {
                NumberInput(formatSketchMillimeters(opening.width), getString(R.string.sketch_opening_width),
                    errorLabel = getString(R.string.sketch_opening_width_invalid), openingWidthPresets = true) { text ->
                    val success = text.toDoubleOrNull()?.let { sketchView.updateSelectedOpening(it) } == true
                    if (success) widthVisible = false
                    success
                }
            })
    }

    @Composable
    private fun DoorStylePicker(selected: com.example.boschlaser.shared.model.DoorStyle,
        onSelect: (com.example.boschlaser.shared.model.DoorStyle) -> Unit) {
        com.example.boschlaser.ui.DoorStylePicker(selected, linkedMapOf(
            com.example.boschlaser.shared.model.DoorStyle.SINGLE to getString(R.string.sketch_door_single),
            com.example.boschlaser.shared.model.DoorStyle.DOUBLE to getString(R.string.sketch_door_double),
            com.example.boschlaser.shared.model.DoorStyle.SLIDING to getString(R.string.sketch_door_sliding),
            com.example.boschlaser.shared.model.DoorStyle.OPENING to getString(R.string.sketch_door_opening),
        ), onSelect)
    }

    @Composable
    private fun NumberInput(initialValue: String, label: String, applyLabel: String = getString(R.string.sketch_property_apply),
        errorLabel: String, accessory: @Composable () -> Unit = {}, wallThicknessPresets: Boolean = false, openingWidthPresets: Boolean = false, columnAnglePresets: Boolean = false, onApply: (String) -> Boolean) {
        var draft by remember { mutableStateOf(TextFieldValue(initialValue, TextRange(0, initialValue.length))) }
        var error by remember { mutableStateOf<String?>(null) }
        MaterialTheme(colorScheme = lightColorScheme(primary = colorResource(R.color.sketch_tool_active))) {
            androidx.compose.foundation.layout.Column {
                NumberProperty(draft, label, applyLabel, onValue = { draft = it; error = null },
                    onApply = { error = if (onApply(draft.text)) null else errorLabel }, accessory = accessory, error = error, openingWidthPresets = openingWidthPresets, columnAnglePresets = columnAnglePresets)
                if (wallThicknessPresets) com.example.boschlaser.ui.WallThicknessPresets(draft.text) { selected ->
                    draft = TextFieldValue(selected, TextRange(0, selected.length))
                    error = if (onApply(selected)) null else errorLabel
                }
            }
        }
    }

    private fun showDrawingSettings() {
        settingsGrid = sketchView.gridDisplayStepMm().toString()
        settingsMagnifier = sketchView.magnifierZoom().toString()
        settingsAutomaticDimensions = sketchView.showAutomaticDimensions()
        settingsVisible = true
    }

    private fun applyDrawingSettings(grid: String, magnifier: String, automaticDimensions: Boolean) {
        val gridValue = grid.toFloatOrNull()
        val magnifierValue = magnifier.toFloatOrNull()
        if (gridValue == null || gridValue !in SketchView.MIN_GRID_DISPLAY_STEP_MM..SketchView.MAX_GRID_DISPLAY_STEP_MM) {
            toast(getString(R.string.sketch_settings_grid_invalid, SketchView.MIN_GRID_DISPLAY_STEP_MM.toInt(),
                SketchView.MAX_GRID_DISPLAY_STEP_MM.toInt()))
        } else if (magnifierValue == null || magnifierValue !in SketchView.MIN_MAGNIFIER_ZOOM..SketchView.MAX_MAGNIFIER_ZOOM) {
            toast(getString(R.string.sketch_settings_magnifier_invalid, SketchView.MIN_MAGNIFIER_ZOOM.toInt(),
                SketchView.MAX_MAGNIFIER_ZOOM.toInt()))
        } else {
            settingsVisible = false
            sketchView.setGridDisplayStepMm(gridValue)
            sketchView.setMagnifierZoom(magnifierValue)
            sketchView.setShowAutomaticDimensions(automaticDimensions)
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putFloat(PREF_GRID_DISPLAY_STEP_MM, gridValue)
                .putFloat(PREF_MAGNIFIER_ZOOM, magnifierValue)
                .putBoolean(PREF_AUTOMATIC_DIMENSIONS, automaticDimensions)
                .apply()
        }
    }

    private fun saveProject(): Boolean {
        if (!projectLoaded) return true
        val id = projectId ?: return false
        return runCatching {
            val bitmap = sketchView.exportPreviewBitmap()
            val preview = try {
                java.io.ByteArrayOutputStream().also { output ->
                    check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
                }.toByteArray()
            } finally { bitmap.recycle() }
            val pending = projectServices.saveSketch(id, sketchView.snapshotDocument(), preview)
            measurementScope.launch {
                try { pending.await() }
                catch (failure: CancellationException) { throw failure }
                catch (_: Exception) { projectServices.acknowledgeError(); toast(getString(R.string.sketch_auto_save_failed)) }
            }
            true
        }.getOrDefault(false)
    }


    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
