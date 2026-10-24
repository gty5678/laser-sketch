package com.example.boschlaser

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.boschlaser.platform.AndroidPhotoCodec
import com.example.boschlaser.shared.measurement.MeasurementRecord
import com.example.boschlaser.shared.measurement.Measurement as CoreMeasurement
import com.example.boschlaser.shared.annotation.*
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsNotSelected
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnotationActivityProjectInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private fun <T> Deferred<T>.awaitResult(): T = runBlocking { withTimeout(5000) { await() } }
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun view(activity: AnnotationActivity) = AnnotationActivity::class.java.getDeclaredField("annotationView")
        .apply { isAccessible = true }.get(activity) as ImageAnnotationView
    private fun awaitDocument(scenario: ActivityScenario<AnnotationActivity>, id: String) {
        var loaded = false
        val deadline = SystemClock.uptimeMillis() + 5000
        while (!loaded && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { loaded = view(it).isEnabled && id in view(it).snapshotDocument().annotations }
            if (!loaded) SystemClock.sleep(20)
        }
        assertTrue("Photo document must finish asynchronous loading", loaded)
    }
    private fun touch(view: ImageAnnotationView, action: Int, point: SketchPoint) {
        val event = MotionEvent.obtain(0, 0, action, point.x, point.y, 0)
        try { view.onTouchEvent(event) } finally { event.recycle() }
    }
    @Test fun sharedMeasurementSnapshotKeepsDoubleForDefaultAndSelectedDimensionLabels() {
        val scenario = ActivityScenario.launch<AnnotationActivity>(Intent(context, AnnotationActivity::class.java))
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        try {
            compose.waitForIdle()
            scenario.onActivity { activity ->
                val annotationView = view(activity)
                annotationView.setBitmap(bitmap)
                val mark = DimensionAnnotation("measurement-target", PhotoPoint(.2, .5), PhotoPoint(.8, .5), "Original", -65536)
                annotationView.restoreDocument(AnnotationDocument(mapOf(mark.id to mark)))
                annotationView.setMode(AnnotationMode.MOVE)
                val point = annotationView.photoToScreen(PhotoPoint(.5, .5))
                touch(annotationView, MotionEvent.ACTION_DOWN, point)
                touch(annotationView, MotionEvent.ACTION_UP, point)
                val receive = AnnotationActivity::class.java.getDeclaredMethod("handleMeasurementSnapshot", List::class.java)
                    .apply { isAccessible = true }
                val first = MeasurementRecord(CoreMeasurement("first", 1000.0, 1))
                val second = MeasurementRecord(CoreMeasurement("second", 16777217.1, 2))
                receive.invoke(activity, listOf(first))
                receive.invoke(activity, listOf(second, first))
                assertEquals("16777217.1 mm", annotationView.defaultDimensionLabel)
                assertEquals("16777217.1 mm", (annotationView.snapshotDocument().annotations.getValue(mark.id) as DimensionAnnotation).label)
                val recent = AnnotationActivity::class.java.getDeclaredField("latestMeasurements").apply { isAccessible = true }
                assertEquals(listOf(16777217.1, 1000.0), recent.get(activity))
                annotationView.updateSelectedLabel("Manual reset")
                receive.invoke(activity, listOf(second, first))
                assertEquals("Manual reset", (annotationView.snapshotDocument().annotations.getValue(mark.id) as DimensionAnnotation).label)
                AnnotationActivity::class.java.getDeclaredMethod("applyRecentMeasurement", Int::class.javaPrimitiveType)
                    .apply { isAccessible = true }.invoke(activity, 0)
                assertEquals("16777217.1 mm", (annotationView.snapshotDocument().annotations.getValue(mark.id) as DimensionAnnotation).label)
                annotationView.updateSelectedLabel("Another manual label")
                val third = MeasurementRecord(CoreMeasurement("third", second.measurement.valueMm, 3))
                receive.invoke(activity, listOf(third, second, first))
                assertEquals("16777217.1 mm", (annotationView.snapshotDocument().annotations.getValue(mark.id) as DimensionAnnotation).label)
            }
        } finally {
            finishAndCloseScenario(scenario)
            bitmap.recycle()
        }
    }

    @Test fun swipingSharedPropertySheetKeepsSelectionDocumentAndUndoHistory() {
        val scenario = ActivityScenario.launch<AnnotationActivity>(Intent(context, AnnotationActivity::class.java))
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val mark = DimensionAnnotation("sheet-target", PhotoPoint(.201234567891, .25), PhotoPoint(.801234567891, .25), "1234 mm", -65536)
        val document = AnnotationDocument(mapOf(mark.id to mark))
        try {
            compose.waitForIdle()
            scenario.onActivity { activity ->
                val annotationView = view(activity)
                annotationView.setBitmap(bitmap)
                annotationView.restoreDocument(document)
                annotationView.setMode(AnnotationMode.MOVE)
                val point = annotationView.photoToScreen(PhotoPoint(.5, .25))
                touch(annotationView, MotionEvent.ACTION_DOWN, point)
                touch(annotationView, MotionEvent.ACTION_UP, point)
            }
            compose.onNodeWithTag("annotation-property-sheet").assertExists()
            val handle = compose.onNodeWithTag("annotation-property-handle")
            val density = handle.fetchSemanticsNode().boundsInRoot.height / 24f
            handle.performTouchInput { down(center); moveBy(Offset(0f, 80f * density)); up() }
            compose.onNodeWithTag("annotation-property-sheet").assertDoesNotExist()
            scenario.onActivity { activity ->
                val annotationView = view(activity)
                assertEquals(mark.id, annotationView.editorState().selection?.annotationId)
                assertEquals(document, annotationView.snapshotDocument())
                assertFalse(annotationView.undo())
                val point = annotationView.photoToScreen(PhotoPoint(.5, .25))
                touch(annotationView, MotionEvent.ACTION_DOWN, point)
                touch(annotationView, MotionEvent.ACTION_UP, point)
                assertEquals(document, annotationView.snapshotDocument())
            }
            compose.onNodeWithTag("annotation-property-sheet").assertExists()
        } finally {
            finishAndCloseScenario(scenario)
            bitmap.recycle()
        }
    }

    @Test fun confirmedLabelSurvivesDestructionWithOriginalPhotoBytesAndDoublePoints() {
        val services = ProjectServices.get(context)
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val jpeg = AndroidPhotoCodec.jpeg(bitmap)
        bitmap.recycle()
        val project = services.createAnnotation("Photo fixture", jpeg, 400, 400).awaitResult()
        val assetId = project.annotations.keys.single()
        val mark = DimensionAnnotation("dimension", PhotoPoint(.251234567891, .501234567891), PhotoPoint(.751234567891, .501234567891), "Original", -65536)
        val document = AnnotationDocument(mapOf(mark.id to mark), assetId)
        services.saveAnnotation(project.manifest.projectId, assetId, document, null).awaitResult()
        val scenario = ActivityScenario.launch<AnnotationActivity>(Intent(context, AnnotationActivity::class.java)
            .putExtra(AnnotationActivity.EXTRA_PROJECT_ID, project.manifest.projectId))
        var scenarioClosed = false
        fun closeScenario() {
            if (!scenarioClosed) {
                finishAndCloseScenario(scenario)
                scenarioClosed = true
            }
        }
        try {
            awaitDocument(scenario, mark.id)
            compose.onNodeWithContentDescription(context.getString(R.string.annotation_tool_move)).performClick().assertIsOn()
            scenario.onActivity { activity ->
                val view = view(activity)
                val point = view.photoToScreen(PhotoPoint(.5, mark.start.y))
                touch(view, MotionEvent.ACTION_DOWN, point)
                touch(view, MotionEvent.ACTION_UP, point)
                assertTrue(view.updateSelectedLabel("Edited"))
            }
            closeScenario()
            services.flush().awaitResult()
            val restored = services.loadAnnotation(project.manifest.projectId).awaitResult()!!
            assertArrayEquals(jpeg, restored.bytes)
            assertEquals(document.copy(annotations = mapOf(mark.id to mark.copy(label = "Edited"))), restored.project.annotations.getValue(assetId))
        } finally { closeScenario(); services.flush().awaitResult(); services.delete(project.manifest.projectId).awaitResult() }
    }
    @Test fun retryingReservedCreationIdKeepsExistingPhotoAndConfirmedAnnotations() {
        val services = ProjectServices.get(context)
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val jpeg = AndroidPhotoCodec.jpeg(bitmap)
        bitmap.recycle()
        val id = java.util.UUID.randomUUID().toString()
        val project = services.createAnnotation("First fixture", jpeg, 4, 4, id).awaitResult()
        val assetId = project.annotations.keys.single()
        val document = AnnotationDocument(mapOf("text" to TextAnnotation("text", PhotoPoint(.3, .4), "Confirmed", -1)), assetId)
        try {
            services.saveAnnotation(id, assetId, document, null).awaitResult()
            val retried = services.createAnnotation("Retry fixture", byteArrayOf(0), 1, 1, id).awaitResult()
            assertEquals("First fixture", retried.manifest.name)
            assertEquals(document, retried.annotations.getValue(assetId))
            assertArrayEquals(jpeg, services.loadAnnotation(id).awaitResult()!!.bytes)
            assertEquals(1, services.projects.value.count { it.info.id == id })
        } finally { services.delete(id).awaitResult() }
    }
    @Test fun migratedPhotoPauseCancelsDragAndDoesNotRewriteLegacySource() {
        val services = ProjectServices.get(context)
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val id = AnnotationProjectStore.create(context, bitmap)
        bitmap.recycle()
        val mark = DimensionMark(.25f, .5f, .75f, .5f, "Original")
        AnnotationProjectStore.save(context, id, AnnotationState(dimensions = listOf(mark)))
        val scenario = ActivityScenario.launch<AnnotationActivity>(Intent(context, AnnotationActivity::class.java).putExtra(AnnotationActivity.EXTRA_PROJECT_ID, id))
        var scenarioClosed = false
        fun closeScenario() {
            if (!scenarioClosed) {
                finishAndCloseScenario(scenario)
                scenarioClosed = true
            }
        }
        try {
            awaitDocument(scenario, "dimension-0")
            val before = services.loadAnnotation("annotation-$id").awaitResult()!!
            compose.onNodeWithContentDescription(context.getString(R.string.annotation_tool_move)).performClick().assertIsOn()
            scenario.onActivity { activity ->
                val view = view(activity)
                val point = view.photoToScreen(PhotoPoint(.5, .5))
                touch(view, MotionEvent.ACTION_DOWN, point)
                touch(view, MotionEvent.ACTION_MOVE, SketchPoint(point.x + 30, point.y + 25))
                assertNotEquals(mark, view.snapshotState().dimensions.single())
            }
            closeScenario()
            services.flush().awaitResult()
            val after = services.loadAnnotation("annotation-$id").awaitResult()!!
            assertEquals(before.project.annotations, after.project.annotations)
            assertArrayEquals(before.bytes, after.bytes)
            val original = AnnotationProjectStore.load(context, id)!!
            assertEquals(mark, original.state.dimensions.single())
            original.bitmap.recycle()
        } finally {
            closeScenario()
            services.flush().awaitResult()
            services.delete("annotation-$id").awaitResult()
            AnnotationProjectStore.delete(context, id)
        }
    }
    @Test fun sharedPlacementChoicesControlCreationAndSurviveRecreation() {
        val services = ProjectServices.get(context)
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val jpeg = AndroidPhotoCodec.jpeg(bitmap)
        bitmap.recycle()
        val project = services.createAnnotation("Placement fixture", jpeg, 400, 400).awaitResult()
        val asset = project.annotations.keys.single()
        val anchor = TextAnnotation("anchor", PhotoPoint(.1, .1), "Fixture", -1)
        services.saveAnnotation(project.manifest.projectId, asset, AnnotationDocument(mapOf(anchor.id to anchor), asset), null).awaitResult()
        val scenario = ActivityScenario.launch<AnnotationActivity>(Intent(context, AnnotationActivity::class.java)
            .putExtra(AnnotationActivity.EXTRA_PROJECT_ID, project.manifest.projectId))
        fun dimension(y: Double) {
            scenario.onActivity { activity ->
                val view = view(activity)
                val start = view.photoToScreen(PhotoPoint(.2, y))
                val end = view.photoToScreen(PhotoPoint(.7, y))
                touch(view, MotionEvent.ACTION_DOWN, start)
                touch(view, MotionEvent.ACTION_MOVE, end)
                touch(view, MotionEvent.ACTION_UP, end)
            }
        }
        try {
            awaitDocument(scenario, anchor.id)
            compose.onNodeWithContentDescription(context.getString(R.string.annotation_tool_dimension)).performClick().assertIsOn()
            compose.onNodeWithContentDescription(context.getString(R.string.annotation_placement_single)).assertIsSelected()
            dimension(.25)
            compose.onNodeWithContentDescription(context.getString(R.string.annotation_tool_move)).assertIsOn()
            compose.onNodeWithContentDescription(context.getString(R.string.annotation_placement_single)).assertDoesNotExist()
            compose.onNodeWithContentDescription(context.getString(R.string.annotation_tool_dimension)).performClick()
            compose.onNodeWithContentDescription(context.getString(R.string.annotation_placement_continuous)).performClick().assertIsSelected()
            dimension(.5)
            dimension(.7)
            compose.onNodeWithContentDescription(context.getString(R.string.annotation_tool_dimension)).assertIsOn()
            scenario.onActivity { assertEquals(3, view(it).snapshotDocument().annotations.values.count { it is DimensionAnnotation }) }
            services.flush().awaitResult()
            scenario.recreate()
            awaitDocument(scenario, anchor.id)
            compose.onNodeWithContentDescription(context.getString(R.string.annotation_placement_continuous)).assertIsSelected()
            scenario.onActivity { assertEquals(3, view(it).snapshotDocument().annotations.values.count { it is DimensionAnnotation }) }
            compose.onNodeWithContentDescription(context.getString(R.string.annotation_finish)).performClick()
            compose.onNodeWithContentDescription(context.getString(R.string.annotation_tool_move)).assertIsOn()
            compose.onNodeWithContentDescription(context.getString(R.string.annotation_finish)).assertDoesNotExist()
            compose.onNodeWithContentDescription(context.getString(R.string.annotation_delete_selected)).assertDoesNotExist()
            scenario.onActivity { activity ->
                val view = view(activity)
                val dimension = view.snapshotDocument().annotations.values.filterIsInstance<DimensionAnnotation>().first()
                val midpoint = PhotoPoint((dimension.start.x + dimension.end.x) / 2, (dimension.start.y + dimension.end.y) / 2)
                val point = view.photoToScreen(midpoint)
                touch(view, MotionEvent.ACTION_DOWN, point)
                touch(view, MotionEvent.ACTION_UP, point)
            }
            compose.onNodeWithContentDescription(context.getString(R.string.annotation_delete_selected)).performClick()
            compose.onNodeWithContentDescription(context.getString(R.string.annotation_delete_selected)).assertDoesNotExist()
            scenario.onActivity { assertEquals(2, view(it).snapshotDocument().annotations.values.count { it is DimensionAnnotation }) }
            withPausedActivity(scenario) {
                services.flush().awaitResult()
                val saved = services.loadAnnotation(project.manifest.projectId).awaitResult()!!.project.annotations.getValue(asset)
                assertEquals(2, saved.annotations.values.count { it is DimensionAnnotation })
                assertEquals(anchor, saved.annotations.getValue(anchor.id))
            }
        } finally {
            finishAndCloseScenario(scenario)
            services.flush().awaitResult()
            services.delete(project.manifest.projectId).awaitResult()
        }
    }

    @Test fun sharedStylePropertiesChangeOnlyStylesAndPreserveDoublePhotoPoints() {
        val services = ProjectServices.get(context)
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val jpeg = AndroidPhotoCodec.jpeg(bitmap)
        bitmap.recycle()
        val project = services.createAnnotation("Style fixture", jpeg, 400, 400).awaitResult()
        val asset = project.annotations.keys.single()
        val text = TextAnnotation("style-text", PhotoPoint(.251234567891, .251234567891), "Fixture", -65536, sizePreset = 0)
        val dimension = DimensionAnnotation("style-dimension", PhotoPoint(.251234567891, .701234567891),
            PhotoPoint(.751234567891, .701234567891), "Original", -65536, thicknessPreset = 0)
        val document = AnnotationDocument(mapOf(text.id to text, dimension.id to dimension), asset)
        services.saveAnnotation(project.manifest.projectId, asset, document, null).awaitResult()
        val scenario = ActivityScenario.launch<AnnotationActivity>(Intent(context, AnnotationActivity::class.java)
            .putExtra(AnnotationActivity.EXTRA_PROJECT_ID, project.manifest.projectId))
        fun select(point: PhotoPoint) {
            scenario.onActivity { activity ->
                val view = view(activity)
                val screen = view.photoToScreen(point)
                touch(view, MotionEvent.ACTION_DOWN, screen)
                touch(view, MotionEvent.ACTION_UP, screen)
            }
        }
        try {
            awaitDocument(scenario, text.id)
            compose.onNodeWithContentDescription(context.getString(R.string.annotation_tool_move)).performClick()
            select(text.position)
            scenario.onActivity { assertEquals(document, view(it).snapshotDocument()) }
            compose.onNodeWithContentDescription(context.getString(R.string.annotation_text_input)).performTextReplacement("Edited fixture")
            compose.onNodeWithContentDescription(context.getString(R.string.annotation_style_size) + " 大").performClick().assertIsSelected()
            compose.onNodeWithContentDescription(context.getString(R.string.annotation_style_background)).performClick().assertIsOff()
            compose.onNodeWithContentDescription(context.getString(R.string.annotation_color_green)).performClick().assertIsSelected()
            select(PhotoPoint((dimension.start.x + dimension.end.x) / 2, dimension.start.y))
            compose.onNodeWithContentDescription(context.getString(R.string.annotation_style_size) + " 大").assertDoesNotExist()
            compose.onNodeWithContentDescription(context.getString(R.string.annotation_distance_unit)).performClick()
            compose.onNodeWithContentDescription(context.getString(R.string.annotation_distance_unit) + " mm").performClick()
            scenario.onActivity { assertEquals(dimension, view(it).snapshotDocument().annotations.getValue(dimension.id)) }
            compose.onNodeWithContentDescription(context.getString(R.string.annotation_distance_input)).performTextReplacement("125.75")
            compose.onNodeWithContentDescription(context.getString(R.string.annotation_distance_unit)).performClick()
            compose.onNodeWithText("cm").performClick()
            compose.onNodeWithContentDescription(context.getString(R.string.annotation_style_thickness) + " 粗").performClick().assertIsSelected()
            compose.onNodeWithContentDescription(context.getString(R.string.annotation_color_green)).assertIsNotSelected()
            compose.onNodeWithContentDescription(context.getString(R.string.annotation_color_blue)).performClick().assertIsSelected()
            val expected = document.copy(annotations = mapOf(text.id to text.copy(text = "Edited fixture", sizePreset = 2, whiteBackground = false,
                colorArgb = context.getColor(R.color.annotation_tool_active)),
                dimension.id to dimension.copy(label = "125.75 cm", thicknessPreset = 2, colorArgb = context.getColor(R.color.annotation_color_blue))))
            scenario.onActivity { assertEquals(expected, view(it).snapshotDocument()) }
            withPausedActivity(scenario) {
                services.flush().awaitResult()
                assertEquals(expected, services.loadAnnotation(project.manifest.projectId).awaitResult()!!.project.annotations.getValue(asset))
            }
        } finally {
            finishAndCloseScenario(scenario)
            services.flush().awaitResult()
            services.delete(project.manifest.projectId).awaitResult()
        }
    }

    @Test fun sharedAngleInputPreservesDoubleRaysAndPersistsOnlyTheLabel() {
        val services = ProjectServices.get(context)
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val jpeg = AndroidPhotoCodec.jpeg(bitmap)
        bitmap.recycle()
        val project = services.createAnnotation("Angle input fixture", jpeg, 400, 400).awaitResult()
        val asset = project.annotations.keys.single()
        val angle = AngleAnnotation("input-angle", PhotoPoint(.301234567891, .301234567891),
            PhotoPoint(.651234567891, .301234567891), PhotoPoint(.301234567891, .651234567891), "45", -65536)
        val document = AnnotationDocument(mapOf(angle.id to angle), asset)
        services.saveAnnotation(project.manifest.projectId, asset, document, null).awaitResult()
        val scenario = ActivityScenario.launch<AnnotationActivity>(Intent(context, AnnotationActivity::class.java)
            .putExtra(AnnotationActivity.EXTRA_PROJECT_ID, project.manifest.projectId))
        try {
            awaitDocument(scenario, angle.id)
            compose.onNodeWithContentDescription(context.getString(R.string.annotation_tool_move)).performClick()
            scenario.onActivity { activity ->
                val view = view(activity)
                val point = view.photoToScreen(angle.vertex)
                touch(view, MotionEvent.ACTION_DOWN, point)
                touch(view, MotionEvent.ACTION_UP, point)
                assertEquals(document, view.snapshotDocument())
            }
            compose.onNodeWithContentDescription(context.getString(R.string.annotation_angle_input)).performTextReplacement("90.5")
            val expected = document.copy(annotations = mapOf(angle.id to angle.copy(label = "90.5")))
            scenario.onActivity { assertEquals(expected, view(it).snapshotDocument()) }
            withPausedActivity(scenario) {
                services.flush().awaitResult()
                assertEquals(expected, services.loadAnnotation(project.manifest.projectId).awaitResult()!!.project.annotations.getValue(asset))
            }
        } finally {
            finishAndCloseScenario(scenario)
            services.flush().awaitResult()
            services.delete(project.manifest.projectId).awaitResult()
        }
    }

    @Test fun sharedAreaQuantityEditsOnlyLabelAndPreservesDoublePolygon() {
        val services = ProjectServices.get(context)
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val jpeg = AndroidPhotoCodec.jpeg(bitmap)
        bitmap.recycle()
        val project = services.createAnnotation("Area input fixture", jpeg, 400, 400).awaitResult()
        val asset = project.annotations.keys.single()
        val area = AreaAnnotation("input-area", listOf(PhotoPoint(.201234567891, .201234567891),
            PhotoPoint(.801234567891, .201234567891), PhotoPoint(.501234567891, .801234567891)), "12.5 cm²", -65536)
        val document = AnnotationDocument(mapOf(area.id to area), asset)
        services.saveAnnotation(project.manifest.projectId, asset, document, null).awaitResult()
        val scenario = ActivityScenario.launch<AnnotationActivity>(Intent(context, AnnotationActivity::class.java)
            .putExtra(AnnotationActivity.EXTRA_PROJECT_ID, project.manifest.projectId))
        try {
            awaitDocument(scenario, area.id)
            compose.onNodeWithContentDescription(context.getString(R.string.annotation_tool_move)).performClick()
            scenario.onActivity { activity ->
                val view = view(activity)
                val point = view.photoToScreen(area.points.first())
                touch(view, MotionEvent.ACTION_DOWN, point)
                touch(view, MotionEvent.ACTION_UP, point)
                assertEquals(document, view.snapshotDocument())
            }
            compose.onNodeWithContentDescription(context.getString(R.string.annotation_delete_area_point)).performClick()
            scenario.onActivity { assertEquals(document, view(it).snapshotDocument()) }
            compose.onNodeWithContentDescription(context.getString(R.string.annotation_area_input)).performTextReplacement("25.75")
            compose.onNodeWithContentDescription(context.getString(R.string.annotation_area_unit)).performClick()
            compose.onNodeWithText("mm²").performClick()
            val expected = document.copy(annotations = mapOf(area.id to area.copy(label = "25.75 mm²")))
            scenario.onActivity { assertEquals(expected, view(it).snapshotDocument()) }
            withPausedActivity(scenario) {
                services.flush().awaitResult()
                assertEquals(expected, services.loadAnnotation(project.manifest.projectId).awaitResult()!!.project.annotations.getValue(asset))
            }
        } finally {
            finishAndCloseScenario(scenario)
            services.flush().awaitResult()
            services.delete(project.manifest.projectId).awaitResult()
        }
    }

}
