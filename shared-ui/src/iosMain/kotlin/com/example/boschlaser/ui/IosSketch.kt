@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.example.boschlaser.ui

import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.boschlaser.shared.command.*
import com.example.boschlaser.shared.editor.*
import com.example.boschlaser.shared.geometry.*
import com.example.boschlaser.shared.model.*
import com.example.boschlaser.shared.tool.*
import platform.Foundation.*
import platform.UIKit.*

@Composable
internal fun IosSketch(project: Project, platform: IosPlatform, services: IosAppServices,
    save: (SketchDocument, Boolean) -> Unit) {
    val editor = remember { SketchEditor(project.sketch) }
    var revision by remember { mutableIntStateOf(0) }
    val displayDensity = LocalDensity.current
    val density = displayDensity.density.toDouble()
    val focus = LocalFocusManager.current
    val defaults = services.preferences
    var viewport by remember { mutableStateOf(Viewport(.2 * density, Vec2(30.0, 180.0) * density)) }
    var grid by remember { mutableDoubleStateOf(defaults.doubleForKey("gridStep").takeIf { it in 10.0..2000.0 } ?: 100.0) }
    var zoom by remember { mutableDoubleStateOf(defaults.doubleForKey("magnifierZoom").takeIf { it in 1.0..8.0 } ?: 2.6) }
    var automatic by remember { mutableStateOf(defaults.objectForKey("autoDimensions") == null || defaults.boolForKey("autoDimensions")) }
    var snap by remember { mutableStateOf(defaults.objectForKey("gridSnap") == null || defaults.boolForKey("gridSnap")) }
    var together by remember { mutableStateOf(defaults.boolForKey("moveTogether")) }
    var settings by remember { mutableStateOf(false) }
    var properties by remember { mutableStateOf(false) }
    var propertyPresent by remember { mutableStateOf(false) }
    var creation by remember { mutableStateOf<String?>(null) }
    var dimension by remember { mutableStateOf<WallDimension?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val style = iosSketchStyle()
    val measurer = rememberTextMeasurer(cacheSize = 128)
    val colors = MaterialTheme.colorScheme
    fun update() { revision++ }
    fun accept(command: SketchCommand): Boolean {
        val planned = command.apply(editor.document)
        if (planned == null) { notice = label("invalid_value"); return false }
        if (planned != editor.document) editor.execute(command)
        notice = null
        update(); return true
    }
    fun apply(result: ToolUpdate) {
        viewport = viewport.copy(offset = viewport.offset + result.panBy)
        result.editFaceDimension?.let { dimension = it }
        result.failure?.let { notice = label(if (it == com.example.boschlaser.shared.tool.ToolFailure.INVALID_BOARD) "board_invalid" else "invalid_value") }
        update()
    }
    val latestSave by rememberUpdatedState(save)
    DisposableEffect(editor) {
        val token = NSNotificationCenter.defaultCenter.addObserverForName(UIApplicationWillResignActiveNotification, null, NSOperationQueue.mainQueue) {
            editor.cancelPointer(); dimension = null; focus.clearFocus(); update(); latestSave(editor.document, false)
        }
        onDispose { NSNotificationCenter.defaultCenter.removeObserver(token); editor.cancelPointer() }
    }
    revision
    editor.detachMultiWayEndpoints = !together
    LaunchedEffect(editor.state.selection) { properties = editor.state.selection != null }
    fun delete() {
        val command = when (val selection = editor.state.selection) {
            is Selection.Board -> DeleteBoardCommand(selection.id)
            is Selection.Wall -> DeleteWallCommand(selection.id)
            is Selection.Column -> DeleteColumnCommand(selection.id)
            is Selection.Opening -> DeleteOpeningCommand(selection.id)
            is Selection.Dimension -> DeleteWallDimensionCommand(selection.id)
            null -> null
        }
        command?.let { accept(it) }
    }
    fun tool(type: ToolType) { focus.clearFocus(); editor.setTool(type); notice = null; update() }
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        SketchScreen(showProperties = (properties || propertyPresent) && editor.state.tool == ToolType.SELECT &&
                editor.state.selection != null && editor.state.selection !is Selection.Dimension,
            topInset = 0.dp, bottomInset = 0.dp,
            style = SketchScreenStyle(colors.surface, 2.dp, if (editor.state.tool == ToolType.DIMENSION) 0.dp else 48.dp,
                148.dp, 12.dp, 48.dp, 12.dp, 56.dp, 12.dp, 12.dp, 8.dp),
            canvas = {
                SketchCanvas(snapshot = {
                    revision // Observe editor invalidation in the draw phase, including strongly skipped compositions.
                    SketchCanvasSnapshot(editor.renderDocument, editor.state, editor.placement, viewport, style,
                        SketchSceneOptions(grid, rotationHandleOffsetPx = 30.0 * density, showAutomaticDimensions = automatic), colors.surface,
                        MagnifierDrawingStyle(colors.surface, colors.onSurface.copy(alpha = .2f), colors.outline),
                        editor.state.magnifierTarget?.let { SketchMagnifier(it, zoom, 196.0 * density) })
                }, formatWallLength = ::millimeters, modifier = Modifier.fillMaxSize().pointerInput(editor, snap) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        focus.clearFocus()
                        var navigating = false
                        fun point(change: PointerInputChange) = Vec2(change.position.x.toDouble(), change.position.y.toDouble())
                        fun contains(bounds: Rect2?, screen: Vec2) = bounds != null && screen.x in bounds.min.x..bounds.max.x && screen.y in bounds.min.y..bounds.max.y
                        fun labelHit(mark: WallDimension, screen: Vec2): Boolean = contains(sketchDimensionLabelBounds(editor.document, mark, viewport,
                            measurer, style.label, displayDensity, WallDimensionGeometry.forMovingSelection(mark, editor.state.selection) != null, format = ::millimeters), screen)
                        fun context(screen: Vec2): PointerContext {
                            val selectedWall = (editor.state.selection as? Selection.Wall)?.id
                            val selectedColumn = (editor.state.selection as? Selection.Column)?.id
                                ?.let { editor.renderDocument.columns[it] } as? RectColumn
                            val rotation = selectedColumn?.takeIf { editor.state.tool == ToolType.SELECT &&
                                columnDrawingLayout(it, viewport, 30.0 * density).rotationHandle?.let { handle ->
                                    Geometry.distance(handle, screen) <= 18 * density
                                } == true }?.id
                            return PointerContext(screen, 16 * density / viewport.scale, 14 * density / viewport.scale,
                                14 * density / viewport.scale, 16 * density / viewport.scale, 12 * density / viewport.scale,
                                12 * density / viewport.scale, gridStep = if (snap) gridStepForScale(grid, viewport.scale.toFloat()) else null,
                                rotationHandleColumnId = rotation, dragThreshold = viewConfiguration.touchSlop.toDouble(), viewportScale = viewport.scale,
                                dimensionLabelId = editor.document.dimensions.values.lastOrNull { labelHit(it, screen) }?.id,
                                faceDimensionLabelId = if (automatic && selectedWall != null) WallDimensionGeometry.forSelectedWall(editor.document, selectedWall)
                                    .firstOrNull { labelHit(it, screen) }?.id else null,
                                dimensionPreviewLabelHit = (editor.state.preview as? SketchPreview.Dimension)?.dimension?.let { labelHit(it, screen) } == true)
                        }
                        val wallEdit = editor.state.wallEditSession != null
                        var committed = false
                        try {
                            if (!wallEdit) apply(editor.pointerDown(viewport.screenToWorld(point(down)), context(point(down))))
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } >= 2) {
                                    if (!navigating) { editor.interruptPointerForViewportChange(); navigating = true }
                                    val centroid = event.calculateCentroid(useCurrent = false)
                                    val anchor = Vec2(centroid.x.toDouble(), centroid.y.toDouble())
                                    val pan = event.calculatePan()
                                    val scale = (viewport.scale * event.calculateZoom()).coerceIn(.01 * density, 2.0 * density)
                                    viewport = Viewport(scale, anchor + Vec2(pan.x.toDouble(), pan.y.toDouble()) - viewport.screenToWorld(anchor) * scale)
                                    update()
                                } else if (!navigating) {
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change != null) {
                                        val screen = point(change); val world = viewport.screenToWorld(screen)
                                        if (change.changedToUpIgnoreConsumed()) {
                                            if (wallEdit) {
                                                val result = editor.pickWallEdit(world, 14 * density / viewport.scale)
                                                if (result?.status != null && result.status != WallEditStatus.SUCCESS) notice = label("invalid_value")
                                                update()
                                            } else apply(editor.pointerUp(world, context(screen)))
                                            committed = true
                                        } else if (change.pressed) {
                                            if (wallEdit) { editor.previewWallEdit(world, 14 * density / viewport.scale); update() }
                                            else apply(editor.pointerMove(world, context(screen)))
                                        }
                                    }
                                }
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                        } finally {
                            if (!committed && !navigating) { editor.cancelPointer(); update() }
                        }
                    }
                })
            }, toolbar = {
                SketchToolbar(editor.state.tool, listOf(ToolType.SELECT, ToolType.PAN, ToolType.WALL,
                    ToolType.RECT_COLUMN, ToolType.CIRCLE_COLUMN, ToolType.DOOR, ToolType.WINDOW,
                    ToolType.BOARD, ToolType.DIMENSION)
                    .map { SketchToolItem(it, label(it.name.lowercase())) }, label("save_back"), label("settings"),
                    onBack = { editor.cancelPointer(); save(editor.document, true) }, onSettings = { settings = true }, onTool = ::tool,
                    backIcon = { DrawingIcon("back") }, settingsIcon = { DrawingIcon("settings") },
                    shareLabel = label("share"), pngLabel = label("export_png"), cadLabel = label("export_dxf"),
                    onSharePng = { services.run { platform.share(sketchPng(editor.document, style, measurer), "png") } },
                    onShareCad = { services.run { platform.share(com.example.boschlaser.shared.export.sketchDxf(editor.document).encodeToByteArray(), "dxf") } },
                    shareIcon = { DrawingIcon("share") },
                    toolIcon = { DrawingIcon(if (it == ToolType.RECT_COLUMN || it == ToolType.CIRCLE_COLUMN) "column" else it.name.lowercase()) })
            }, editToolbar = {
                when (editor.state.tool) {
                    ToolType.BOARD -> BoardTools(editor.placement.boardShape, editor.placement.boardFill,
                        listOf(label("board_rectangle"), label("board_polygon"), label("board_circle")), listOf(label("board_white"), label("board_grid"), label("board_wood")),
                        { editor.cancelPointer(); editor.placement = editor.placement.copy(boardShape = it); update() },
                        { editor.placement = editor.placement.copy(boardFill = it); update() })
                    ToolType.WALL -> DrawingWallTools(editor.placement.alignment, label("control_line"), lineLabels, label("wall_thickness"),
                        { editor.placement = editor.placement.copy(alignment = it); update() }, { creation = "wall" }, editor.placement.wallFill,
                        listOf(label("wall_fill_blank"), label("wall_fill_hatch"), label("wall_fill_black")),
                        { editor.placement = editor.placement.copy(wallFill = it); update() },
                        editor.continuousWallDrawing, label("wall_drawing_single"), label("wall_drawing_continuous"),
                        { editor.continuousWallDrawing = it; update() })
                    ToolType.RECT_COLUMN, ToolType.CIRCLE_COLUMN -> ColumnPropertyTools(editor.placement.columnFill,
                        listOf(label("column_fill_white"), label("column_fill_black")),
                        listOfNotNull("${label("column_size")} ${decimal(editor.placement.columnWidth)} × ${decimal(editor.placement.columnDepth)}",
                            if (editor.state.tool == ToolType.RECT_COLUMN) "${label("rotation")} ${decimal(editor.placement.columnRotation)}°" else null),
                        { editor.placement = editor.placement.copy(columnFill = it); update() },
                        { creation = if (it == 0) "column_size" else "column_angle" })
                    ToolType.DOOR -> Row {
                        TextButton(onClick = { creation = "door" }) { Text(label("width")) }
                        ScrollableTools(Modifier) { doorLabels.forEach { (value, text) -> FilterChip(editor.placement.doorStyle == value,
                            { editor.placement = editor.placement.copy(doorStyle = value); update() }, label = { Text(text) }) } }
                    }
                    ToolType.WINDOW -> CreationPropertyTools(listOf(label("width"))) { creation = "window" }
                    ToolType.DIMENSION, ToolType.PAN -> Unit
                    else -> SketchEditTools(editor.state.wallEditSession?.operation == WallEditOperation.TRIM,
                        editor.state.wallEditSession?.operation == WallEditOperation.EXTEND, label("trim"), label("extend"),
                        { editor.startWallEdit(WallEditOperation.TRIM); update() }, { editor.startWallEdit(WallEditOperation.EXTEND); update() },
                        { DrawingIcon("join_walls") }, { DrawingIcon("extend_wall") }, additionalTools = {
                            TextButton(onClick = { tool(ToolType.SPLIT) }) { Text(label("split")) }
                            TextButton(onClick = { tool(ToolType.MEASURE) }) { Text(label("measure")) }
                        })
                }
            }, actions = {
                Surface(shape = MaterialTheme.shapes.large, tonalElevation = 2.dp) {
                    Row {
                        IconButton(enabled = editor.canUndo || editor.state.wallAnchor != null || editor.state.preview != null || editor.state.dimensionFirst != null,
                            onClick = { editor.undoPlacement(); update() }, modifier = Modifier.semantics { contentDescription = label("undo") }) { DrawingIcon("undo") }
                        IconButton(enabled = editor.canRedo, onClick = { editor.redo(); update() },
                            modifier = Modifier.semantics { contentDescription = label("redo") }) { DrawingIcon("undo", Modifier.graphicsLayer { scaleX = -1f }) }
                        if (editor.state.selection != null) IconButton(onClick = ::delete,
                            modifier = Modifier.semantics { contentDescription = label("delete") }) { DrawingIcon("delete") }
                        if (editor.state.tool != ToolType.SELECT || editor.state.wallEditSession != null) IconButton(onClick = {
                            val result = editor.finishBoardPlacement()
                            if (result.failure != null) apply(result) else tool(ToolType.SELECT)
                        },
                            modifier = Modifier.semantics { contentDescription = label("finish") }) { DrawingIcon("confirm") }
                        IconButton(onClick = { services.run { platform.savePhoto(sketchPng(editor.document, style, measurer)) { notice = label(if (it) "photo_saved" else "photo_error") } } },
                            modifier = Modifier.semantics { contentDescription = label("save_album") }) { DrawingIcon("save") }
                    }
                }
            }, selectionActions = {
                val selected = editor.state.selection
                val wall = (selected as? Selection.Wall)?.let { editor.document.walls[it.id] }
                val opening = (selected as? Selection.Opening)?.let { editor.document.openings[it.id] }
                val board = (selected as? Selection.Board)?.let { editor.document.boards[it.id] }
                val vertex = editor.state.selectedBoardVertex
                BoardVertexAction(editor.state.tool == ToolType.SELECT && board?.shape == BoardShape.POLYGON && vertex != null,
                    vertex?.let { board?.removedVertex(it) != null } == true, label("board_delete_vertex"),
                    { if (editor.deleteSelectedBoardVertex()) update() }, { DrawingIcon("delete") })
                SelectionActions(if (wall != null) SelectionActionTarget.WALL else if (opening?.type == OpeningType.DOOR) SelectionActionTarget.DOOR else SelectionActionTarget.NONE,
                    label("straighten"), label("flip_hinge"), label("flip_side"),
                    { wall?.let { accept(StraightenWallCommand(it.id, editor.wallFreeEndpointIsStart(it.id))) } },
                    { opening?.let { accept(ChangeOpeningPropertiesCommand(it.id, it.width, flipHinge = true)) } },
                    { opening?.let { accept(ChangeOpeningPropertiesCommand(it.id, it.width, flip = true)) } },
                    { DrawingIcon("straighten") }, { DrawingIcon("flip_vertical") }, { DrawingIcon("flip_horizontal") })
            }, gridSnap = {
                SketchBottomControls(snap, label("grid_snap"), together, label("move_together"),
                    { snap = it; defaults.setBool(it, "gridSnap") }, { together = it; defaults.setBool(it, "moveTogether"); update() })
            }, properties = {
                SketchPropertySheet(properties, editor.state.selection.hashCode(), true, if (editor.state.selection is Selection.Wall) label("wall_properties") else label("properties"),
                    SketchPropertySheetStyle(colors.surface, colors.onSurface, 16.sp, 12.dp, 12.dp, 12.dp, 8.dp, 56.dp),
                    LocalViewConfiguration.current.touchSlop, { properties = false; focus.clearFocus() }, { propertyPresent = it },
                    headerAccessory = {
                        (editor.state.selection as? Selection.Wall)?.let { editor.document.walls[it.id] }?.let { wall ->
                            WallControlLinePicker(wall.alignment, label("control_line"), lineLabels, showLabel = false) {
                                accept(ChangeWallAlignmentCommand(wall.id, it))
                            }
                        }
                        (editor.state.selection as? Selection.Opening)?.let { editor.document.openings[it.id] }
                            ?.takeIf { it.type == OpeningType.DOOR }?.let { opening ->
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { IosOpeningProperties(opening, ::accept) }
                            }
                    },
                    content = { IosSketchProperties(editor, ::accept, ::update) })
            }, guidance = {
                Text(notice ?: label(if (editor.state.wallEditSession != null) "wall_edit_hint" else if (editor.state.tool == ToolType.PAN) "pan_hint" else if (editor.state.tool == ToolType.BOARD) "board_hint" else if (editor.state.tool == ToolType.DIMENSION) "dimension_hint" else "sketch_hint"),
                    style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Text(label("sketch_counts").replace("%w", editor.document.walls.size.toString())
                    .replace("%o", editor.document.openings.size.toString()).replace("%c", editor.document.columns.size.toString()),
                    style = MaterialTheme.typography.bodySmall)
                if (editor.document.dimensions.isNotEmpty()) Text(label("dimension_count").replace("%s", editor.document.dimensions.size.toString()),
                    style = MaterialTheme.typography.bodySmall)
            }, dialogs = {
                if (settings) DrawingSettingsDialog(decimal(grid), decimal(zoom), automatic,
                    DrawingSettingsLabels(label("settings"), label("grid_step"), label("grid_range"), label("magnifier"), label("zoom_range"),
                        label("apply"), label("cancel"), label("automatic_dimensions"), label("invalid_value"), label("invalid_value")),
                    { g, z, a ->
                        grid = g.toDouble(); zoom = z.toDouble(); automatic = a
                        defaults.setDouble(grid, "gridStep"); defaults.setDouble(zoom, "magnifierZoom"); defaults.setBool(a, "autoDimensions"); settings = false
                    }, { settings = false })
                creation?.let { kind ->
                    val initial = when (kind) {
                        "wall" -> listOf("wall_thickness" to editor.placement.wallThickness)
                        "door" -> listOf("width" to editor.placement.doorWidth)
                        "window" -> listOf("width" to editor.placement.windowWidth)
                        "column_angle" -> listOf("rotation" to editor.placement.columnRotation)
                        else -> if (editor.state.tool == ToolType.CIRCLE_COLUMN) listOf("diameter" to editor.placement.columnWidth)
                            else listOf("width" to editor.placement.columnWidth, "depth" to editor.placement.columnDepth)
                    }
                    IosNumberDialog(label("creation_properties"), initial, { values ->
                        try {
                            editor.placement = when (kind) {
                                "wall" -> editor.placement.copy(wallThickness = values[0])
                                "door" -> editor.placement.copy(doorWidth = values[0])
                                "window" -> editor.placement.copy(windowWidth = values[0])
                                "column_angle" -> editor.placement.copy(columnRotation = values[0])
                                else -> editor.placement.copy(columnWidth = values[0], columnDepth = values.getOrElse(1) { values[0] }, columnRotation = values.getOrElse(2) { editor.placement.columnRotation })
                            }; update(); true
                        } catch (_: IllegalArgumentException) { false }
                    }, { creation = null })
                }
                dimension?.let { mark ->
                    val value = WallDimensionGeometry.layout(editor.document, mark)?.valueMm ?: 0.0
                    WallFaceDistanceDialog(decimal(value), label("dimension"), label("dimension_hint"), label("length_mm_input"), label("apply"), label("cancel"), label("invalid_value"),
                        { input -> input.toDoubleOrNull()?.let { amount ->
                            val result = accept(if (mark.id in editor.document.dimensions) DriveWallDimensionCommand(mark.id, amount) else DriveWallFaceDistanceCommand(mark, amount))
                            if (result) dimension = null
                            result
                        } ?: false }, { dimension = null })
                }
            })
    }
}
