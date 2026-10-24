package com.example.boschlaser

import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.boschlaser.platform.AndroidAnnotationTextPainter
import com.example.boschlaser.shared.annotation.*
import com.example.boschlaser.shared.geometry.Vec2
import com.example.boschlaser.ui.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnotationCanvasInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @org.junit.After fun finishScreen() { finishAndCloseScenario(compose.activityRule.scenario) }

    @Test fun sharedCanvasObservesEditsAndRendersThePhotoInsideTheLens() {
        val initial = AnnotationCanvasSnapshot(AnnotationDocument(), PhotoDrawingViewport(0.0, 0.0, 300.0, 200.0),
            AnnotationSceneState(), AnnotationSceneStyle(AnnotationControlStyle(Color.Cyan, Color.White, Color.Yellow), Color.White, Color.Red),
            AnnotationSceneLabels("placeholder") { "$it°" }, Color.White, "empty", 18.0, -16777216,
            PhotoMagnifierStyle(Color.DarkGray, Color.Black, Color.Cyan, 3f, 2f, 9f))
        val state = mutableStateOf(initial)
        val painter = AndroidAnnotationTextPainter()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                AnnotationCanvas({ state.value }, painter, drawPhoto = { viewport ->
                    drawRect(Color.Gray, Offset(viewport.left.toFloat(), viewport.top.toFloat()),
                        Size(viewport.width.toFloat(), viewport.height.toFloat()))
                }, Modifier.size(300.dp, 250.dp).testTag("shared-annotation"))
            }
        }
        fun pixel(x: Int, y: Int) = compose.onNodeWithTag("shared-annotation").captureToImage().toPixelMap()[x, y]
        assertEquals(Color.Gray, pixel(150, 100))
        assertEquals(Color.White, pixel(150, 230))
        val mark = DimensionAnnotation("dimension", PhotoPoint(.2, .5), PhotoPoint(.8, .5), "", -65536)
        val document = AnnotationDocument(mapOf(mark.id to mark))
        compose.runOnIdle { state.value = initial.copy(document = document) }
        assertEquals(Color.Red, pixel(150, 100))
        compose.runOnIdle { state.value = state.value.copy(magnifierFocus = Vec2(150.0, 100.0)) }
        assertEquals(Color.Cyan, pixel(224, 76))
        assertEquals(Color.Red, pixel(244, 76))
        assertEquals(Color.Gray, pixel(200, 40))
        assertEquals(Color.Gray, pixel(162, 14))
        compose.runOnIdle { state.value = state.value.copy(magnifierFocus = null) }
        assertEquals(Color.Gray, pixel(244, 76))
        assertEquals(mark, document.annotations[mark.id])
    }

    @Test fun androidHostRoutesComposeTouchesAndRedrawsTheConfirmedSharedDocument() {
        val source = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }
        lateinit var host: ImageAnnotationView
        var creations = 0
        val visible = mutableStateOf(true)
        try {
            compose.setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    if (visible.value) AndroidView(factory = { context ->
                        ImageAnnotationView(context).apply {
                            host = this
                            setBitmap(source)
                            onAnnotationCreated = { creations++ }
                        }
                    }, modifier = Modifier.size(300.dp, 250.dp))
                }
            }
            compose.onNodeWithTag("annotation-canvas").performTouchInput {
                down(Offset(60f, 100f))
                moveTo(Offset(240f, 100f))
                up()
            }
            compose.runOnIdle {
                val mark = host.snapshotDocument().annotations.values.single() as DimensionAnnotation
                assertEquals(1, creations)
                assertEquals(.2, mark.start.x, 1e-6)
                assertEquals(.5, mark.start.y, 1e-6)
                assertEquals(.8, mark.end.x, 1e-6)
                assertEquals(.5, mark.end.y, 1e-6)
            }
            val pixels = compose.onNodeWithTag("annotation-canvas").captureToImage().toPixelMap()
            assertEquals(Color(0xffd6202a), pixels[150, 100])
            compose.runOnIdle { assertTrue(host.undo()) }
            assertEquals(Color.White, compose.onNodeWithTag("annotation-canvas").captureToImage().toPixelMap()[150, 100])
        } finally {
            // Remove the composition before releasing the native bitmap used by its draw callback.
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            source.recycle()
        }
    }
}
