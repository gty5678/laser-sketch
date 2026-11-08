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
        val canvasGeneration = mutableStateOf(0)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                androidx.compose.runtime.key(canvasGeneration.value) {
                    SketchCanvas({ snapshot.value }, { "${it.toInt()} mm" }, Modifier.size(400.dp, 300.dp).testTag("canvas"))
                }
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

        // A panned, cached network must match a newly built canvas, including hatch phase,
        // mixed-material corners, opening cutouts and the second magnifier cache entry.
        val first = Wall("first", Vec2(0.0, 0.0), Vec2(600.0, 0.0), 100.0, fill = WallFill.HATCH)
        val second = Wall("second", first.end, Vec2(600.0, 500.0), 100.0, fill = WallFill.BLACK)
        val door = Opening("door", first.id, 300.0, 100.0, OpeningType.DOOR)
        compose.runOnIdle {
            snapshot.value = initial.copy(document = SketchDocument(
                walls = mapOf(first.id to first, second.id to second), openings = mapOf(door.id to door)),
                viewport = Viewport(1.5, Vec2(0.0, 150.0)),
                magnifier = SketchMagnifier(first.end, 2.6, 0.0))
        }
        compose.onNodeWithTag("canvas").captureToImage()
        compose.runOnIdle { snapshot.value = snapshot.value.copy(viewport = Viewport(1.5, Vec2(-650.0, 160.0))) }
        val cached = compose.onNodeWithTag("canvas").captureToImage().toPixelMap()
        compose.runOnIdle { canvasGeneration.value++ }
        val rebuilt = compose.onNodeWithTag("canvas").captureToImage().toPixelMap()
        for (y in 0 until cached.height) for (x in 0 until cached.width) {
            assertEquals("Cached pan pixel ($x, $y)", rebuilt[x, y], cached[x, y])
        }

    }
}
