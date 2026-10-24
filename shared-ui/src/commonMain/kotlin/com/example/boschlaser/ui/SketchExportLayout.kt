package com.example.boschlaser.ui

import com.example.boschlaser.shared.editor.Viewport
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.geometry.sketchContentBounds
import com.example.boschlaser.shared.model.SketchDocument
import kotlin.math.max
import kotlin.math.min

/** A platform-independent export camera; no selection, grid or screen camera is serialized. */
fun sketchExportViewport(document: SketchDocument, sizePx: Vec2): Viewport {
    require(sizePx.x > 0.0 && sizePx.y > 0.0)
    val bounds = sketchContentBounds(document)
    val scale = min((sizePx.x - 180.0) / max(1.0, bounds.max.x - bounds.min.x),
        (sizePx.y - 180.0) / max(1.0, bounds.max.y - bounds.min.y)).coerceIn(.03, .5)
    val center = bounds.min * .5 + bounds.max * .5
    return Viewport(scale, sizePx * .5 - center * scale)
}
