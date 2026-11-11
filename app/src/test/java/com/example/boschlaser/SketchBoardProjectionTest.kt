package com.example.boschlaser

import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.*
import org.junit.Assert.*
import org.junit.Test

class SketchBoardProjectionTest {
    @Test fun legacyProjectionRetainsBoardsAndExactCoordinates() {
        val board = Board("fixture", listOf(Vec2(.123456789, 0.0),Vec2(2000.0,0.0),Vec2(2000.0,2000.0)),BoardFill.WOOD)
        val document = SketchDocument(boards = mapOf(board.id to board))
        assertEquals(document,document.toSketchState().toDocument())
        val projection = SketchCoreProjection()
        assertEquals(document,projection.restore(document.toSketchState()))
        assertEquals(listOf(board),projection.state(document).boards)
        assertEquals(emptyList<Board>(),projection.state(SketchDocument()).boards)
    }
}
