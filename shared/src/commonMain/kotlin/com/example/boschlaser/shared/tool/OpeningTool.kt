package com.example.boschlaser.shared.tool

import com.example.boschlaser.shared.command.AddOpeningCommand
import com.example.boschlaser.shared.editor.*
import com.example.boschlaser.shared.geometry.*
import com.example.boschlaser.shared.model.*

class OpeningTool(private val window: Boolean) : SketchTool {
    override fun pointerDown(editor: SketchEditor, point: Vec2, context: PointerContext) = ToolUpdate()
    override fun pointerMove(editor: SketchEditor, point: Vec2, context: PointerContext) = ToolUpdate()
    override fun pointerUp(editor: SketchEditor, point: Vec2, context: PointerContext): ToolUpdate {
        val wall = editor.document.walls.values.minByOrNull { Geometry.distanceToSegment(point, WallGeometry.referenceLine(it)) }
            ?: return ToolUpdate()
        val line = WallGeometry.referenceLine(wall)
        val length = Geometry.length(line)
        val width = if (window) editor.placement.windowWidth else editor.placement.doorWidth
        if (width >= length || Geometry.distanceToSegment(point, line) > 500.0) return ToolUpdate()
        val offset = (Geometry.parameter(point, line) * length).coerceIn(width / 2.0, length - width / 2.0)
        val opening = Opening(editor.nextId(), wall.id, offset, width, if (window) OpeningType.WINDOW else OpeningType.DOOR)
        val changed = editor.execute(AddOpeningCommand(opening))
        if (changed) editor.select(Selection.Opening(opening.id))
        return ToolUpdate(changed)
    }
    override fun cancel(editor: SketchEditor) = Unit
}
