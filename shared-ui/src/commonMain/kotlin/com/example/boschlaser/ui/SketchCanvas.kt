package com.example.boschlaser.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.rememberTextMeasurer
import com.example.boschlaser.shared.editor.EditorState
import com.example.boschlaser.shared.editor.PlacementSettings
import com.example.boschlaser.shared.editor.Viewport
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.SketchDocument

/** Immutable rendering inputs, never a second editable document or gesture owner. */
data class SketchCanvasSnapshot(
    val document: SketchDocument,
    val state: EditorState,
    val placement: PlacementSettings,
    val viewport: Viewport,
    val style: SketchSceneStyle,
    val options: SketchSceneOptions,
    val background: Color,
    val magnifierStyle: MagnifierDrawingStyle,
    val magnifier: SketchMagnifier? = null,
)

/** The host attaches pointer adapters through Modifier and provides localized dimension text. */
@Composable
fun SketchCanvas(snapshot: () -> SketchCanvasSnapshot, formatWallLength: (Double) -> String, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer(cacheSize = 128)
    val geometryCache = remember { SketchWallGeometryCache() }
    Canvas(modifier) {
        val current = snapshot()
        val sizePx = Vec2(size.width.toDouble(), size.height.toDouble())
        drawRect(current.background)
        fun scene(viewport: Viewport) = drawSketchScene(current.document, current.state, current.placement,
            viewport, sizePx, current.style, current.options, measurer, formatWallLength, geometryCache)
        scene(current.viewport)
        current.magnifier?.let { magnifier ->
            drawSketchMagnifier(magnifierDrawingLayout(current.viewport, magnifier, density.toDouble()), current.magnifierStyle) {
                scene(it)
            }
        }
    }
}
