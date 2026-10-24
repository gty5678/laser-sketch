package com.example.boschlaser.shared.tool

import com.example.boschlaser.shared.command.SplitWallCommand
import com.example.boschlaser.shared.editor.Selection
import com.example.boschlaser.shared.editor.SketchEditor
import com.example.boschlaser.shared.geometry.Geometry
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.geometry.WallGeometry

/** Hit the visible wall body, then split on its semantic locator line in one atomic command. */
class SplitTool : SketchTool {
    override fun pointerDown(editor: SketchEditor, point: Vec2, context: PointerContext) = ToolUpdate()
    override fun pointerMove(editor: SketchEditor, point: Vec2, context: PointerContext) = ToolUpdate()
    override fun pointerUp(editor: SketchEditor, point: Vec2, context: PointerContext): ToolUpdate {
        val wall = editor.document.walls.values.toList().asReversed()
            .firstOrNull { editor.hitTestEngine.hitsWall(it, point, context.wallTolerance) } ?: return ToolUpdate()
        val projected = Geometry.closestPoint(point, WallGeometry.referenceLine(wall))
        val newId = editor.nextId()
        if (!editor.execute(SplitWallCommand(wall.id, projected, newId)))
            return ToolUpdate(failure = ToolFailure.INVALID_SPLIT)
        editor.select(Selection.Wall(newId))
        return ToolUpdate(documentChanged = true)
    }
    override fun cancel(editor: SketchEditor) = Unit
}
