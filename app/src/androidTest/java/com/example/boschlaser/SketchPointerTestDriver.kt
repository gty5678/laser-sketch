package com.example.boschlaser

import android.view.MotionEvent

/** Exercise the same pointer adapter as the canvas, using the view's current viewport. */
internal fun SketchView.sendWorldPointer(action: Int, point: SketchPoint) {
    val screen = worldToScreen(point.toVec2())
    val event = MotionEvent.obtain(0L, 0L, action, screen.x.toFloat(), screen.y.toFloat(), 0)
    try { onTouchEvent(event) } finally { event.recycle() }
}

internal fun SketchView.previewWorldDrag(start: SketchPoint, end: SketchPoint) {
    sendWorldPointer(MotionEvent.ACTION_DOWN, start)
    sendWorldPointer(MotionEvent.ACTION_MOVE, end)
}
