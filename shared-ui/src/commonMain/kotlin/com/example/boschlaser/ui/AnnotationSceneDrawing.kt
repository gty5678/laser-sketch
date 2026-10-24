package com.example.boschlaser.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.boschlaser.shared.annotation.AngleAnnotation
import com.example.boschlaser.shared.annotation.AnnotationDocument
import com.example.boschlaser.shared.annotation.AreaAnnotation
import com.example.boschlaser.shared.annotation.DimensionAnnotation
import com.example.boschlaser.shared.annotation.PhotoPoint
import com.example.boschlaser.shared.annotation.TextAnnotation
import com.example.boschlaser.shared.annotation.Annotation as CoreAnnotation
import kotlin.math.max

/** Rendering state is separate from both the confirmed document and tool ownership. */
data class AnnotationSceneState(
    val selectedIds: Set<String> = emptySet(),
    val selectedAreaPoint: Int = -1,
    val dimensionPreview: DimensionAnnotation? = null,
    val pendingAngle: List<PhotoPoint> = emptyList(),
    val pendingArea: List<PhotoPoint> = emptyList(),
)
data class AnnotationSceneStyle(val controls: AnnotationControlStyle, val labelBackground: Color, val previewColor: Color)
data class AnnotationSceneLabels(val textPlaceholder: String, val formatAngle: (String) -> String)

/** Preserve the original area → dimension → text → angle order for every output surface. */
fun DrawScope.drawAnnotationScene(
    document: AnnotationDocument,
    viewport: PhotoDrawingViewport,
    scale: Double,
    state: AnnotationSceneState,
    style: AnnotationSceneStyle,
    painter: AnnotationTextPainter,
    labels: AnnotationSceneLabels,
    showControls: Boolean = true,
    showPreviews: Boolean = true,
    dimensionPreviewBeforeText: Boolean = false,
) {
    require(scale.isFinite() && scale > 0)
    fun label(layout: AnnotationLabelLayout, text: String, color: Int) =
        drawAnnotationLabel(layout, text, color, style.labelBackground, painter)
    fun selected(mark: CoreAnnotation) = showControls && mark.id in state.selectedIds
    fun dimension(mark: DimensionAnnotation, controls: Boolean) {
        val shape = dimensionDrawingLayout(mark, viewport, scale)
        drawAnnotationDimension(shape, Color(mark.colorArgb))
        if (mark.label.isNotBlank()) label(dimensionLabelLayout(shape, painter.measure(mark.label, max(24.0, 18 * scale)), scale),
            mark.label, mark.colorArgb)
        if (controls) drawAnnotationControls(listOf(shape.start, shape.end), scale, style.controls)
    }
    document.annotations.values.filterIsInstance<AreaAnnotation>().forEach { mark ->
        val shape = areaDrawingLayout(mark, viewport)
        drawAnnotationArea(shape, mark.colorArgb, scale)
        if (mark.label.isNotBlank()) {
            val size = max(24.0, 18 * scale)
            val padding = max(8.0, 6 * scale)
            label(annotationLabelLayout(shape.labelBaselineCenter, painter.measure(mark.label, size), size, padding, padding / 2), mark.label, mark.colorArgb)
        }
        if (selected(mark)) drawAnnotationControls(shape.points, scale, style.controls, state.selectedAreaPoint)
    }
    document.annotations.values.filterIsInstance<DimensionAnnotation>().forEach { dimension(it, selected(it)) }
    if (showPreviews && dimensionPreviewBeforeText) state.dimensionPreview?.let { dimension(it, false) }
    document.annotations.values.filterIsInstance<TextAnnotation>().forEach { mark ->
        val text = mark.text.ifBlank { labels.textPlaceholder }
        val size = annotationTextSize(mark.sizePreset, scale)
        label(annotationLabelLayout(viewport.screen(mark.position), painter.measure(text, size), size, max(8.0, 6 * scale),
            whiteBackground = mark.whiteBackground), text, mark.colorArgb)
    }
    document.annotations.values.filterIsInstance<AngleAnnotation>().forEach { mark ->
        val shape = angleDrawingLayout(mark, viewport, scale)
        drawAnnotationAngle(shape, Color(mark.colorArgb))
        if (mark.label.isNotBlank()) {
            val text = labels.formatAngle(mark.label)
            val size = max(22.0, 18 * scale)
            label(annotationLabelLayout(shape.labelBaselineCenter, painter.measure(text, size), size, max(7.0, 5 * scale)), text, mark.colorArgb)
        }
        if (selected(mark)) drawAnnotationControls(listOf(shape.vertex, shape.first, shape.second), scale, style.controls)
    }
    if (!showPreviews) return
    if (state.pendingAngle.isNotEmpty()) {
        val points = state.pendingAngle.map(viewport::screen)
        if (points.size >= 2) drawAnnotationPolyline(points.take(2), style.previewColor, scale)
        drawAnnotationControls(points, scale, style.controls)
    }
    if (state.pendingArea.isNotEmpty()) {
        val points = state.pendingArea.map(viewport::screen)
        drawAnnotationPolyline(points, style.previewColor, scale)
        drawAnnotationControls(points, scale, style.controls)
    }
    if (!dimensionPreviewBeforeText) state.dimensionPreview?.let { dimension(it, false) }
}
