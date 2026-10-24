package com.example.boschlaser

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.boschlaser.shared.editor.*
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.shared.model.*
import com.example.boschlaser.ui.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SketchCanvasInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @org.junit.After fun finishScreen() { finishAndCloseScenario(compose.activityRule.scenario) }

    @Test fun canvasObservesHostUpdatesAndClipsTheMagnifierToACircle() {
        val control = ControlDrawingStyle(Color.Green, Color.White)
        val style = SketchSceneStyle(GridDrawingStyle(Color.Yellow, 1f, Color.Yellow, 1f),
            WallDrawingStyle(Color.Gray, Color.Black, 2f), WallDrawingStyle(Color.Gray, Color.Green, 4f),
            OpeningDrawingStyle(Color.Black, 3f, Color.Black, 2f), OpeningDrawingStyle(Color.Green, 4f, Color.Black, 2f),
            ColumnDrawingStyle(Color.Gray, Color.Green, 4f, Color.Green, Color.White, 8f),
            WallControlDrawingStyle(Color.Green, 2f, null, control), WallControlDrawingStyle(Color.Green, 4f, null, control),
            TextStyle(color = Color.Black, fontSize = 13.sp), Color.Green, 4f, 16.0)
        val initial = SketchCanvasSnapshot(SketchDocument(), EditorState(), PlacementSettings(), Viewport(.1, Vec2(20.0, 50.0)),
            style, SketchSceneOptions(100.0, rotationHandleOffsetPx = 32.0), Color.Yellow,
            MagnifierDrawingStyle(Color.White, Color(0x37000000), Color.Green))
        val snapshot = mutableStateOf(initial)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                SketchCanvas({ snapshot.value }, { "${it.toInt()} mm" }, Modifier.size(400.dp, 300.dp).testTag("canvas"))
            }
        }
        fun pixel(x: Int, y: Int) = compose.onNodeWithTag("canvas").captureToImage().toPixelMap()[x, y]
        assertEquals(Color.Yellow, pixel(120, 150))
        val column = CircularColumn("fixture", Vec2(1000.0, 1000.0), 400.0)
        val document = SketchDocument(columns = mapOf(column.id to column))
        compose.runOnIdle { snapshot.value = initial.copy(document = document) }
        assertEquals(Color.Gray, pixel(120, 150))
        compose.runOnIdle { snapshot.value = snapshot.value.copy(magnifier = SketchMagnifier(column.center, 2.6, 0.0)) }
        assertEquals(Color.Gray, pixel(88, 84))
        // Sample empty lens background between grid lines, away from the circular column.
        assertEquals(Color.White, pixel(84, 20))
        assertEquals(Color.Yellow, pixel(20, 20))
        compose.runOnIdle { snapshot.value = snapshot.value.copy(magnifier = null) }
        assertEquals(Color.Yellow, pixel(88, 20))
        assertEquals(column, document.columns.getValue(column.id))
    }
}
