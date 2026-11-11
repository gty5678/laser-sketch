package com.example.boschlaser

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.boschlaser.platform.androidSketchDrawingStyle
import com.example.boschlaser.shared.editor.*
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.*
import com.example.boschlaser.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchBoardControlsInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @org.junit.After fun finishScreen() { finishAndCloseScenario(compose.activityRule.scenario) }

    @Test fun graphicalSwitchesAreIndependentSingleSelections() {
        compose.setContent {
            var shape by remember { mutableStateOf(BoardShape.RECTANGLE) }
            var fill by remember { mutableStateOf(BoardFill.WHITE) }
            BoardTools(shape,fill,listOf("Rectangle","Polygon","Circle"),listOf("White","Grid","Wood"),{ shape = it },{ fill = it })
        }
        compose.onNodeWithContentDescription("Circle").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Rectangle").assertIsNotSelected()
        compose.onNodeWithContentDescription("Polygon").assertIsNotSelected()
        compose.onNodeWithContentDescription("White").assertIsSelected()
        compose.onNodeWithContentDescription("Wood").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("White").assertIsNotSelected()
        compose.onNodeWithContentDescription("Grid").assertIsNotSelected()
        compose.onNodeWithContentDescription("Circle").assertIsSelected()
        compose.onNodeWithText("Circle").assertDoesNotExist()
    }
    @Test fun vertexDeleteActionOnlyExistsForSelectedVertexAndCanBeDisabled() {
        val visible = mutableStateOf(false)
        val enabled = mutableStateOf(true)
        var deleted = false
        compose.setContent {
            BoardVertexAction(visible.value,enabled.value,"Delete vertex",{ deleted = true }, {})
        }
        compose.onNodeWithContentDescription("Delete vertex").assertDoesNotExist()
        compose.runOnIdle { visible.value = true }
        compose.onNodeWithContentDescription("Delete vertex").assertIsDisplayed().performClick()
        assertTrue(deleted)
        compose.runOnIdle { enabled.value = false }
        compose.onNodeWithContentDescription("Delete vertex").assertIsNotEnabled()
        compose.runOnIdle { visible.value = false }
        compose.onNodeWithContentDescription("Delete vertex").assertDoesNotExist()
    }
    @Test fun woodHasMonochromeLinesAndCircleClipsItsBoundingBoxCorners() {
        val board = Board("circle",Board.circle(Vec2(1000.0,1000.0),800.0),BoardFill.WOOD,BoardShape.CIRCLE)
        val snapshot = SketchCanvasSnapshot(SketchDocument(boards = mapOf(board.id to board)), EditorState(), PlacementSettings(),
            Viewport(.1,Vec2(0.0,0.0)),androidSketchDrawingStyle(compose.activity,Density(1f),8f),
            SketchSceneOptions(100.0,showGrid = false,showSelection = false,rotationHandleOffsetPx = 20.0),
            Color.Magenta,MagnifierDrawingStyle(Color.White,Color.Gray,Color.Black))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                SketchCanvas({ snapshot }, { it.toString() },Modifier.size(200.dp).testTag("board-canvas"))
            }
        }
        val pixels = compose.onNodeWithTag("board-canvas").captureToImage().toPixelMap()
        assertEquals(Color.Magenta,pixels[25,25])
        var textured = 0
        var white = 0
        for (x in 60 until 140) for (y in 60 until 140) {
            val color = pixels[x,y]
            assertEquals(color.red,color.green,1e-5f)
            assertEquals(color.green,color.blue,1e-5f)
            if (color.red < .9f) textured++ else white++
        }
        assertTrue(textured > 50)
        assertTrue(white > 500)
    }
}
