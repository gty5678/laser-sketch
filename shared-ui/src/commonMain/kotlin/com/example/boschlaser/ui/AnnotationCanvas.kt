package com.example.boschlaser.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.boschlaser.shared.annotation.AnnotationDocument
import com.example.boschlaser.shared.geometry.Vec2
import kotlin.math.max

/** Immutable draw inputs; the editor and source photo remain owned by their existing host. */
data class AnnotationCanvasSnapshot(
    val document: AnnotationDocument,
    val viewport: PhotoDrawingViewport?,
    val state: AnnotationSceneState,
    val style: AnnotationSceneStyle,
    val labels: AnnotationSceneLabels,
    val background: Color,
    val emptyHint: String,
    val emptyHintFontSizePx: Double,
    val emptyHintColorArgb: Int,
    val magnifierStyle: PhotoMagnifierStyle,
    val magnifierFocus: Vec2? = null,
)

/** Photos and native fonts enter through adapters; no platform image type enters shared UI. */
@Composable
fun AnnotationCanvas(snapshot: (Vec2) -> AnnotationCanvasSnapshot, painter: AnnotationTextPainter,
    drawPhoto: DrawScope.(PhotoDrawingViewport) -> Unit, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val sizePx = Vec2(size.width.toDouble(), size.height.toDouble())
        drawAnnotationCanvas(snapshot(sizePx), sizePx, painter, drawPhoto)
    }
}

fun DrawScope.drawAnnotationCanvas(current: AnnotationCanvasSnapshot, sizePx: Vec2,
    painter: AnnotationTextPainter, drawPhoto: DrawScope.(PhotoDrawingViewport) -> Unit) {
    drawRect(current.background)
    val viewport = current.viewport
    if (viewport == null) {
        val fontSize = current.emptyHintFontSizePx
        val layout = annotationLabelLayout(sizePx * .5, painter.measure(current.emptyHint, fontSize), fontSize,
            0.0, whiteBackground = false)
        drawAnnotationLabel(layout, current.emptyHint, current.emptyHintColorArgb, current.style.labelBackground, painter)
        return
    }
    val scale = max(1.0, viewport.width / 500)
    drawPhoto(viewport)
    drawAnnotationScene(current.document, viewport, scale, current.state, current.style, painter, current.labels)
    current.magnifierFocus?.let { focus ->
        photoMagnifierLayout(sizePx, density.toDouble(), focus)?.let { lens ->
            drawPhotoMagnifier(lens, current.magnifierStyle) {
                drawPhoto(viewport)
                drawAnnotationScene(current.document, viewport, scale,
                    AnnotationSceneState(dimensionPreview = current.state.dimensionPreview), current.style, painter,
                    current.labels, showControls = false, dimensionPreviewBeforeText = true)
            }
        }
    }
}

/** Fit the whole photo, centered horizontally and aligned to the top as in the Android editor. */
fun fitPhotoViewport(canvasSizePx: Vec2, photoSizePx: Vec2): PhotoDrawingViewport {
    require(canvasSizePx.x >= 0 && canvasSizePx.y >= 0 && photoSizePx.x > 0 && photoSizePx.y > 0)
    val scale = minOf(canvasSizePx.x / photoSizePx.x, canvasSizePx.y / photoSizePx.y)
    val width = photoSizePx.x * scale
    return PhotoDrawingViewport((canvasSizePx.x - width) / 2, 0.0, width, photoSizePx.y * scale)
}
