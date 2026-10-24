package com.example.boschlaser.shared.editor

import com.example.boschlaser.shared.geometry.Vec2

data class Viewport(val scale: Double, val offset: Vec2) {
    init { require(scale.isFinite() && scale > 0.0) }
    fun worldToScreen(point: Vec2) = point * scale + offset
    fun screenToWorld(point: Vec2) = (point - offset) * (1.0 / scale)
}
