@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.example.boschlaser.ui

import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.*
import com.example.boschlaser.shared.annotation.*
import com.example.boschlaser.shared.annotation.Annotation as Mark
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.measurement.isNewestMeasurementAdded
import com.example.boschlaser.shared.model.Project
import platform.Foundation.*
import platform.UIKit.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun IosAnnotation(project: Project, assetId: String, platform: IosPlatform, services: IosAppServices, isNewPhoto: Boolean,
    save: (AnnotationDocument, Boolean) -> Unit) {
    val editor = remember { AnnotationEditor(project.annotations.getValue(assetId)).apply {
        setMode(if (isNewPhoto) AnnotationMode.DIMENSION else AnnotationMode.MOVE)
    } }
    var loaded by remember { mutableStateOf(false) }
    val image by produceState<ImageBitmap?>(null, assetId) {
        value = withContext(Dispatchers.Default) { runCatching { decodePhoto(services.store.asset(project, assetId)) }.getOrNull() }
        loaded = true
    }
    val bitmap = image
    if (bitmap == null) {
        Column(Modifier.safeDrawingPadding()) {
            if (loaded) Text(label("photo_error")) else CircularProgressIndicator()
            TextButton(onClick = { save(editor.document, true) }) { Text(label("back")) }
        }
        return
    }
    val density = LocalDensity.current.density.toDouble()
    val focus = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme
    val painter = rememberIosTextPainter()
    val defaults = services.preferences
    var continuous by remember { mutableStateOf(defaults.boolForKey("annotationContinuous")) }
    var revision by remember { mutableIntStateOf(0) }
    var viewport by remember { mutableStateOf<PhotoDrawingViewport?>(null) }
    var magnifier by remember { mutableStateOf<Vec2?>(null) }
    var properties by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf(label("annotation_hint")) }
    var waiting by remember { mutableStateOf<String?>(null) }
    var focusSignal by remember { mutableIntStateOf(0) }
    val records by services.measurements.records.collectAsState()
    var previousIds by remember { mutableStateOf<List<String>?>(null) }
    val style = remember(colors) { AnnotationSceneStyle(AnnotationControlStyle(colors.primary, colors.surface, colors.tertiary), colors.surface.copy(alpha = 225 / 255f), colors.primary) }
    val labels = remember { AnnotationSceneLabels(label("text_placeholder"), { label("angle_value").replace("%s", it) }) }
    fun update() { revision++ }
    fun cancel() { editor.cancelEdit(); editor.cancelDimension(); magnifier = null; waiting = null; focus.clearFocus(); focusSignal++; update() }
    fun mode(value: AnnotationMode) { properties = false; cancel(); editor.setMode(value); update() }
    fun created() {
        if (!continuous) editor.setMode(AnnotationMode.MOVE, preserveSelection = true)
        properties = true; notice = label("annotation_created"); update()
    }
    fun updateMark(mark: Mark) { editor.execute(UpdateAnnotationCommand(mark)); update() }
    fun fillMeasurement(value: Double) {
        val selected = editor.state.selection?.annotationId?.let { editor.document.annotations[it] } as? DimensionAnnotation ?: return
        updateMark(selected.copy(label = millimeters(value))); waiting = null; notice = label("measurement_applied")
    }
    LaunchedEffect(records) {
        val ids = records.asReversed().map { it.measurement.id }
        if (isNewestMeasurementAdded(previousIds, ids) && waiting != null && editor.state.mode == AnnotationMode.MOVE &&
            editor.state.selection?.annotationId == waiting) records.lastOrNull()?.let { fillMeasurement(it.measurement.valueMm) }
        previousIds = ids
    }
    LaunchedEffect(editor.state.selection, editor.state.mode) { waiting = null }
    val latestSave by rememberUpdatedState(save)
    DisposableEffect(editor) {
        val token = NSNotificationCenter.defaultCenter.addObserverForName(UIApplicationWillResignActiveNotification, null, NSOperationQueue.mainQueue) {
            cancel(); latestSave(editor.document, false)
        }
        onDispose { NSNotificationCenter.defaultCenter.removeObserver(token); editor.cancelEdit(); editor.cancelDimension() }
    }
    revision
    // Properties copy confirmed entities so an interrupted drag cannot leak preview coordinates.
    val selected = editor.state.selection?.annotationId?.let { editor.document.annotations[it] }
    fun export(): ByteArray {
        val bitmap = ImageBitmap(bitmap.width, bitmap.height)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(bitmap.width.toFloat(), bitmap.height.toFloat())) {
            drawImage(bitmap)
            drawAnnotationScene(editor.document, PhotoDrawingViewport(0.0, 0.0, bitmap.width.toDouble(), bitmap.height.toDouble()),
                maxOf(1.0, bitmap.width / 500.0), AnnotationSceneState(), style, painter, labels, showControls = false, showPreviews = false)
        }
        return png(bitmap)
    }
    AnnotationScreen(colors.surfaceVariant, 12.dp, 12.dp, modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
        toolbar = {
            AnnotationToolbar(editor.state.mode, AnnotationMode.entries.map { AnnotationToolItem(it, label("annotation_${it.name.lowercase()}")) },
                label("save_back"), label("share_image"), label("save_album"),
                onBack = { cancel(); save(editor.document, true) }, onShare = { services.run { platform.share(export(), "png") } },
                onSave = { services.run { platform.savePhoto(export()) { notice = label(if (it) "photo_saved" else "photo_error") } } }, onTool = ::mode,
                backIcon = { DrawingIcon("back") }, shareIcon = { DrawingIcon("share") },
                saveIcon = { DrawingIcon("save") }, toolIcon = { DrawingIcon(it.name.lowercase()) },
                exportLabel = label("share"))
        }, placementOptions = {
            AnnotationSecondaryToolbar(scrollable = false) {
                if (editor.state.mode != AnnotationMode.MOVE) {
                    PlacementOptions(true, continuous, label("placement"), label("single"), label("continuous"),
                        { continuous = it; defaults.setBool(it, "annotationContinuous") })
                }
            }
        }, status = {
            Text(notice, modifier = Modifier.height(22.dp), style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        },
        canvas = {
            AnnotationCanvas(snapshot = { size ->
                revision
                val photoViewport = fitPhotoViewport(size, Vec2(bitmap.width.toDouble(), bitmap.height.toDouble()))
                AnnotationCanvasSnapshot(editor.renderDocument, photoViewport,
                    AnnotationSceneState(setOfNotNull(editor.state.selection?.annotationId), editor.state.selection?.pointIndex ?: -1,
                        editor.state.creation.dimension?.let { DimensionAnnotation("preview", it.start, it.end, "", colors.primary.toArgb()) },
                        editor.state.creation.anglePoints, editor.state.creation.areaPoints),
                    style, labels, colors.surfaceVariant, label("photo_error"), 16.0 * density, colors.onSurface.toArgb(),
                    PhotoMagnifierStyle(colors.surface, colors.outline, colors.primary, 2f, 1f, 8f), magnifier)
            }, painter = painter, drawPhoto = { target ->
                drawImage(bitmap, dstOffset = IntOffset(target.left.toInt(), target.top.toInt()), dstSize = IntSize(target.width.toInt(), target.height.toInt()))
            }, modifier = Modifier.fillMaxSize().onSizeChanged {
                viewport = fitPhotoViewport(Vec2(it.width.toDouble(), it.height.toDouble()), Vec2(bitmap.width.toDouble(), bitmap.height.toDouble()))
            }.pointerInput(editor) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    focus.clearFocus(); focusSignal++; properties = false
                    val photoViewport = viewport?.takeIf { it.width > 0 && it.height > 0 } ?: return@awaitEachGesture
                    val photoSize = Vec2(photoViewport.width, photoViewport.height)
                    fun screen(change: PointerInputChange) = Vec2(change.position.x.toDouble(), change.position.y.toDouble())
                    fun photo(change: PointerInputChange) = photoViewport.photo(screen(change))
                    val hit = AnnotationHitTest(editor.renderDocument, photoSize,
                        AnnotationHitTolerances(14 * density, 24 * density, 20 * density, 20 * density, 14 * density))
                    val startMode = editor.state.mode
                    val target = if (startMode == AnnotationMode.MOVE) hit.pick(photo(down), editor.state.selection) else null
                    if (startMode == AnnotationMode.MOVE) {
                        editor.select(target)
                        if (target != null) editor.beginDrag(target, photo(down))
                    } else if (startMode == AnnotationMode.DIMENSION) editor.beginDimension(photo(down))
                    update()
                    var released = false
                    var cancelled = false
                    try {
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } > 1) { cancelled = true; editor.cancelEdit(); editor.cancelDimension(); magnifier = null; update() }
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change != null && !cancelled) {
                                if (change.changedToUpIgnoreConsumed()) {
                                    val point = photo(change)
                                    when (startMode) {
                                        AnnotationMode.MOVE -> {
                                            val dragging = editor.state.drag?.started == true
                                            editor.commitEdit()
                                            properties = !dragging && target != null
                                            if (!dragging && target != null) hit.areaEdgeAt(target.annotationId, point)?.let { edge ->
                                                editor.tapAreaEdge(target.annotationId, edge, (NSProcessInfo.processInfo.systemUptime * 1000).toLong(), 300)
                                            }
                                        }
                                        AnnotationMode.DIMENSION -> if (editor.finishDimension(point, photoSize, 12 * density, uuid(),
                                            records.lastOrNull()?.let { millimeters(it.measurement.valueMm) }.orEmpty(), colors.primary.toArgb())) created()
                                        AnnotationMode.TEXT -> if (editor.placeText(point, uuid(), "", colors.primary.toArgb())) created()
                                        AnnotationMode.ANGLE -> when (val result = editor.placeAnglePoint(point, uuid(), colors.primary.toArgb())) {
                                            is AnnotationPlacementResult.Created -> created()
                                            is AnnotationPlacementResult.Pending -> notice = label("angle_points").replace("%s", result.pointCount.toString())
                                            else -> notice = label("invalid_value")
                                        }
                                        AnnotationMode.AREA -> { editor.appendAreaPoint(point); notice = label("area_points").replace("%s", editor.state.creation.areaPoints.size.toString()) }
                                    }
                                    magnifier = null; released = true; update()
                                } else if (change.pressed) {
                                    if (startMode == AnnotationMode.DIMENSION) { editor.updateDimension(photo(change)); magnifier = screen(change) }
                                    if (startMode == AnnotationMode.MOVE && target != null && editor.dragTo(photo(change), photoSize, viewConfiguration.touchSlop.toDouble())) magnifier = screen(change)
                                    update()
                                }
                            }
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    } finally {
                        if (!released) { editor.cancelEdit(); editor.cancelDimension() }
                        magnifier = null; update()
                    }
                }
            })
        }, actions = {
            AnnotationActions(editor.state.mode != AnnotationMode.MOVE, editor.state.mode == AnnotationMode.MOVE && selected != null,
                label("finish"), label("delete"), {
                    if (editor.state.mode == AnnotationMode.AREA) {
                        if (editor.finishArea(uuid(), colors.primary.toArgb())) created() else notice = label("area_minimum")
                    } else mode(AnnotationMode.MOVE)
                }, { selected?.let { editor.execute(DeleteAnnotationCommand(it.id)); update() } },
                { DrawingIcon("confirm") }, { DrawingIcon("delete") })
        }, properties = {
            if (properties && editor.state.mode == AnnotationMode.MOVE && selected != null) {
                AnnotationEditingPanel {
                    selected?.let { mark ->
                        IosAnnotationProperties(mark, editor.state.selection?.pointIndex, focusSignal, ::updateMark,
                            onDeletePoint = { index ->
                                if (!editor.execute(DeleteAreaPointCommand(mark.id, index))) notice = label("area_minimum")
                                else {
                                    val area = editor.document.annotations[mark.id] as AreaAnnotation
                                    editor.select(AnnotationSelectionTarget(mark.id, minOf(index, area.points.lastIndex)))
                                }; update()
                            }, onInsertPoint = { index -> editor.execute(InsertAreaMidpointCommand(mark.id, index)); update() },
                            measurementContent = {
                                TextButton(onClick = { waiting = if (waiting == mark.id) null else mark.id }) {
                                    Text(label(if (waiting == mark.id) "cancel_measurement" else "receive_measurement"))
                                }
                                records.asReversed().take(3).forEach { record ->
                                    TextButton(onClick = { fillMeasurement(record.measurement.valueMm) }) {
                                        Text(millimeters(record.measurement.valueMm))
                                    }
                                }
                            })
                    }
                }
            }
        })
}
