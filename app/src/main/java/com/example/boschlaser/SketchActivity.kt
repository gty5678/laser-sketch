package com.example.boschlaser

import com.example.boschlaser.shared.editor.WallEditOperation
import com.example.boschlaser.shared.editor.WallEditSession
import com.example.boschlaser.shared.editor.WallEditUpdate
import com.example.boschlaser.shared.command.WallEditStatus

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

import android.os.Bundle
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.boschlaser.ui.WallControlLinePicker
import com.example.boschlaser.ui.DrawingSettingsDialog
import com.example.boschlaser.ui.DrawingSettingsLabels
import com.example.boschlaser.shared.model.WallAlignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.boschlaser.ui.GridSnapControl
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
    private var openingPlacementType by mutableStateOf<SketchOpeningType?>(null)
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
    private var currentMode by mutableStateOf(SketchMode.SELECT)
    private var wallEditSession by mutableStateOf<WallEditSession?>(null)
    private val joinWallsActive get() = wallEditSession?.operation == WallEditOperation.TRIM
    private val extendWallActive get() = wallEditSession?.operation == WallEditOperation.EXTEND
    private var systemBottomInset by mutableIntStateOf(0)
    private var systemTopInset by mutableIntStateOf(0)

    private val measurementScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val projectServices by lazy { ProjectServices.get(this) }
    private var projectLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        projectId = savedInstanceState?.getString(EXTRA_PROJECT_ID) ?: intent.getStringExtra(EXTRA_PROJECT_ID)
        buildInterface()
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
            onSelectionChanged = { if (!joinWallsActive && !extendWallActive) showSelection(it) }
            onStateChanged = {
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
                            getString(R.string.sketch_settings_cancel), getString(R.string.sketch_settings_automatic_dimensions)),
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
                SketchEditTools(joinWallsActive, extendWallActive,
                    getString(if (joinWallsActive) R.string.sketch_join_cancel else R.string.sketch_join),
                    getString(if (extendWallActive) R.string.sketch_extend_cancel else R.string.sketch_extend),
                    onJoin = { if (joinWallsActive) stopWallJoinMode() else startWallJoinMode() },
                    onExtend = { if (extendWallActive) stopWallExtendMode() else startWallExtendMode() },
                    joinIcon = { Icon(painterResource(R.drawable.ic_join_walls), contentDescription = null) },
                    extendIcon = { Icon(painterResource(R.drawable.ic_extend_wall), contentDescription = null) },
                    additionalTools = {
                        SketchToolButtons(ToolType.valueOf(currentMode.name), listOf(
                            SketchToolItem(ToolType.MEASURE, getString(R.string.sketch_tool_measure)),
                            SketchToolItem(ToolType.SPLIT, getString(R.string.sketch_tool_split))),
                            onTool = { setMode(SketchMode.valueOf(it.name)) },
                            toolIcon = { tool -> Icon(painterResource(
                                if (tool == ToolType.MEASURE) R.drawable.ic_measure else R.drawable.ic_split),
                                contentDescription = null) })
                    })
            }
        }

        @Composable fun GridSnap() {
            MaterialTheme {
                GridSnapControl(gridSnapEnabled, getString(R.string.sketch_grid_snap)) { enabled ->
                    gridSnapEnabled = enabled
                    sketchView.setGridSnapEnabled(enabled)
                }
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
        currentMode = mode
        sketchView.setMode(mode)
        when (mode) {
            SketchMode.MEASURE -> toast(getString(R.string.sketch_measure_hint))
            SketchMode.SPLIT -> toast(getString(R.string.sketch_split_hint))
            SketchMode.DIMENSION -> toast(getString(R.string.sketch_dimension_hint))
            else -> Unit
        }
        when (mode) {
            SketchMode.DOOR -> showOpeningPlacementProperties(SketchOpeningType.DOOR)
            SketchMode.WINDOW -> showOpeningPlacementProperties(SketchOpeningType.WINDOW)
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
        openingPlacementType = null
        propertyPanelResetKey++
        animatePropertyDismiss = false
        hasSelection = selection != null
        selectionActionTarget = when {
            currentMode != SketchMode.SELECT -> SelectionActionTarget.NONE
            selection is SketchSelection.Wall -> SelectionActionTarget.WALL
            selection is SketchSelection.Opening && selection.opening.type == SketchOpeningType.DOOR -> SelectionActionTarget.DOOR
            else -> SelectionActionTarget.NONE
        }
        if (selection is SketchSelection.Dimension) {
            hideProperties()
        } else if (selection == null) {
            when (currentMode) {
                SketchMode.DOOR -> showOpeningPlacementProperties(SketchOpeningType.DOOR)
                SketchMode.WINDOW -> showOpeningPlacementProperties(SketchOpeningType.WINDOW)
                else -> hideProperties()
            }
        } else propertyPanelVisible = true
    }

    private fun hideProperties() {
        animatePropertyDismiss = false
        propertyPanelVisible = false
    }

    private fun showOpeningPlacementProperties(type: SketchOpeningType) {
        propertySelection = null
        openingPlacementType = type
        propertyPanelResetKey++
        animatePropertyDismiss = false
        propertyPanelVisible = true
    }

    @Composable
    private fun Properties() {
        val selection = propertySelection
        val placement = openingPlacementType
        val title = when (selection) {
            is SketchSelection.Dimension -> getString(R.string.sketch_dimension_properties)
            is SketchSelection.Wall -> getString(R.string.sketch_wall_properties)
            is SketchSelection.Column -> getString(if (selection.column.type == SketchColumnType.CIRCLE)
                R.string.sketch_circle_properties else R.string.sketch_rectangle_properties)
            is SketchSelection.Opening -> getString(if (selection.opening.type == SketchOpeningType.DOOR)
                R.string.sketch_door_properties else R.string.sketch_window_properties)
            null -> placement?.let { getString(if (it == SketchOpeningType.DOOR)
                R.string.sketch_place_door_hint else R.string.sketch_place_window_hint) }.orEmpty()
        }
        SketchPropertySheet(propertyPanelVisible, propertyPanelResetKey, animatePropertyDismiss, title,
            propertySheetStyle(), ViewConfiguration.get(this).scaledTouchSlop.toFloat(),
            onDismiss = { animatePropertyDismiss = true; propertyPanelVisible = false },
            onPresenceChanged = { propertyPanelPresent = it }) {
            key(propertyPanelResetKey) {
                when (selection) {
                    is SketchSelection.Dimension -> Unit
                    is SketchSelection.Wall -> WallProperties(selection.wall.id)
                    is SketchSelection.Column -> ColumnProperties(selection.column.id)
                    is SketchSelection.Opening -> OpeningProperties(selection.opening.id)
                    null -> placement?.let { OpeningPlacementProperties(it) }
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
                }, onDismiss = { editingFaceDimension = null })
        }
    }

    @Composable
    private fun WallProperties(wallId: String) {
        val wall = sketchView.renderDocument().walls[wallId] ?: return
        val alignment = wall.alignment
        MaterialTheme(colorScheme = lightColorScheme(primary = colorResource(R.color.sketch_tool_active))) {
            WallControlLinePicker(alignment, getString(R.string.wall_control_line_label), listOf(
                getString(R.string.wall_control_line_center), getString(R.string.wall_control_line_inner),
                getString(R.string.wall_control_line_outer))) { selected ->
                val controlLine = when (selected) {
                    WallAlignment.CENTER -> SketchWallControlLine.CENTER
                    WallAlignment.LEFT -> SketchWallControlLine.INNER
                    WallAlignment.RIGHT -> SketchWallControlLine.OUTER
                }
                sketchView.updateSelectedWallControlLine(controlLine)
            }
        }
        NumberInput(formatSketchMillimeters(wall.thickness), getString(R.string.sketch_wall_thickness),
            getString(R.string.sketch_wall_thickness_apply)) { text ->
            val value = text.toDoubleOrNull()
            if (value == null || !sketchView.updateSelectedWallThickness(value)) {
                toast(getString(R.string.sketch_wall_thickness_invalid))
            }
        }
    }

    @Composable
    private fun ColumnProperties(columnId: String) {
        val column = sketchView.renderDocument().columns[columnId] ?: return
        val rectangle = column as? com.example.boschlaser.shared.model.RectColumn
        val columnWidth = rectangle?.width ?: (column as com.example.boschlaser.shared.model.CircularColumn).diameter
        val columnDepth = rectangle?.depth ?: columnWidth
        var width by remember { mutableStateOf(TextFieldValue(formatSketchMillimeters(columnWidth))) }
        var depth by remember { mutableStateOf(TextFieldValue(formatSketchMillimeters(columnDepth))) }
        val showDepth = rectangle != null && currentMode == SketchMode.SELECT
        MaterialTheme(colorScheme = lightColorScheme(primary = colorResource(R.color.sketch_tool_active))) {
            ColumnSizeProperty(width, if (showDepth) depth else null,
                getString(R.string.sketch_column_width), getString(R.string.sketch_column_depth),
                getString(R.string.sketch_property_apply), onWidth = { width = it }, onDepth = { depth = it },
                onApply = {
                    val w = width.text.toDoubleOrNull()
                    val d = when {
                        rectangle == null -> w
                        showDepth -> depth.text.toDoubleOrNull()
                        else -> (sketchView.snapshotDocument().columns[column.id] as? com.example.boschlaser.shared.model.RectColumn)?.depth
                    }
                    if (w == null || d == null || !sketchView.updateSelectedColumnSize(w, d)) {
                        toast(getString(R.string.sketch_column_size_invalid))
                    }
                })
        }
        if (rectangle != null) {
            NumberInput(formatSketchMillimeters(rectangle.rotationDegrees),
                getString(R.string.column_rotation_angle), getString(R.string.column_rotation_apply)) { text ->
                val angle = text.toDoubleOrNull()
                if (angle == null || !sketchView.updateSelectedColumnRotation(angle)) toast(getString(R.string.column_rotation_invalid))
            }
        }
    }

    @Composable
    private fun OpeningProperties(openingId: String) {
        val opening = sketchView.renderDocument().openings[openingId] ?: return
        NumberInput(formatSketchMillimeters(opening.width), getString(R.string.sketch_opening_width)) { text ->
            val value = text.toDoubleOrNull()
            if (value == null || !sketchView.updateSelectedOpening(value)) toast(getString(R.string.sketch_opening_width_invalid))
        }
    }

    @Composable
    private fun OpeningPlacementProperties(type: SketchOpeningType) {
        NumberInput(sketchView.placementOpeningWidth(type).roundToInt().toString(), getString(R.string.sketch_opening_width)) { text ->
            val value = text.toFloatOrNull()
            if (value == null || !sketchView.updatePlacementOpeningWidth(type, value)) {
                toast(getString(R.string.sketch_opening_placement_width_invalid))
            } else toast(getString(R.string.sketch_opening_placement_width_updated, value.roundToInt()))
        }
    }

    @Composable
    private fun NumberInput(initialValue: String, label: String, applyLabel: String = getString(R.string.sketch_property_apply),
        accessory: @Composable () -> Unit = {}, onApply: (String) -> Unit) {
        var draft by remember { mutableStateOf(TextFieldValue(initialValue, TextRange(0, initialValue.length))) }
        MaterialTheme(colorScheme = lightColorScheme(primary = colorResource(R.color.sketch_tool_active))) {
            NumberProperty(draft, label, applyLabel, onValue = { draft = it }, onApply = { onApply(draft.text) }, accessory = accessory)
        }
    }

    private fun showDrawingSettings() {
        settingsGrid = sketchView.gridDisplayStepMm().toString()
        settingsMagnifier = sketchView.magnifierZoom().toString()
        settingsAutomaticDimensions = sketchView.showAutomaticDimensions()
        settingsVisible = true
    }

    private fun applyDrawingSettings(grid: String, magnifier: String, automaticDimensions: Boolean) {
        settingsVisible = false
        val gridValue = grid.toFloatOrNull()
        val magnifierValue = magnifier.toFloatOrNull()
        if (gridValue == null || gridValue !in SketchView.MIN_GRID_DISPLAY_STEP_MM..SketchView.MAX_GRID_DISPLAY_STEP_MM) {
            toast(getString(R.string.sketch_settings_grid_invalid, SketchView.MIN_GRID_DISPLAY_STEP_MM.toInt(),
                SketchView.MAX_GRID_DISPLAY_STEP_MM.toInt()))
        } else if (magnifierValue == null || magnifierValue !in SketchView.MIN_MAGNIFIER_ZOOM..SketchView.MAX_MAGNIFIER_ZOOM) {
            toast(getString(R.string.sketch_settings_magnifier_invalid, SketchView.MIN_MAGNIFIER_ZOOM.toInt(),
                SketchView.MAX_MAGNIFIER_ZOOM.toInt()))
        } else {
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
