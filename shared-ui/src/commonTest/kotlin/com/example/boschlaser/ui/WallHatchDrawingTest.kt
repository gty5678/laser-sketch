package com.example.boschlaser.ui

import androidx.compose.ui.geometry.Rect
import kotlin.test.*

class WallHatchDrawingTest {
    private val canvas = Rect(0f, 0f, 1080f, 1920f)

    @Test fun zoomedWallsHaveWorkBoundedByCanvasSize() {
        val normal = wallHatchLines(Rect(-2000f, -2000f, 4000f, 4000f), canvas, 8f, 1f).toList()
        val enlarged = wallHatchLines(Rect(-1e8f, -1e8f, 1e8f, 1e8f), canvas, 8f, 1f).toList()
        assertEquals(normal, enlarged)
        assertTrue(enlarged.size <= 380)
    }

    @Test fun offscreenWallsProduceNoHatchLines() {
        assertTrue(wallHatchLines(Rect(-500f, -500f, -100f, -100f), canvas, 8f, 1f).none())
        assertTrue(wallHatchLines(Rect(1200f, 0f, 1500f, 500f), canvas, 8f, 1f).none())
    }

    @Test fun clippingPreservesPhaseAndCoversStrokeAtCanvasEdges() {
        val lines = wallHatchLines(Rect(-33f, -55f, 2000f, 3000f), canvas, 8f, 2f).toList()
        assertEquals(-2f, lines.first().end.y)
        assertEquals(1922f, lines.first().start.y)
        lines.forEach {
            val phase = it.start.x + it.start.y
            assertEquals(0f, phase % 8f, .001f)
            assertEquals(phase, it.end.x + it.end.y)
        }
        assertTrue(lines.first().start.x + lines.first().start.y <= -4f)
        assertTrue(lines.last().start.x + lines.last().start.y >= 3004f)
    }
}
