package com.example.boschlaser

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoCorrectionInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun correctingNewPhotoThenReopeningKeepsBaseImageAndEmptyDocument() = importPhoto(true)
    @Test fun originalCanSkipCorrectionAndStartAnnotating() = importPhoto(false)

    private fun importPhoto(apply: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("correction-fixture", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.GREEN)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val scenario = ActivityScenario.launch<AnnotationActivity>(Intent(context, AnnotationActivity::class.java))
        fun view(activity: AnnotationActivity) = AnnotationActivity::class.java.getDeclaredField("annotationView")
            .apply { isAccessible = true }.get(activity) as ImageAnnotationView
        var projectId: String? = null
        try {
            scenario.onActivity { activity ->
                AnnotationActivity::class.java.getDeclaredMethod("loadImage", Uri::class.java)
                    .apply { isAccessible = true }.invoke(activity, Uri.fromFile(file))
            }
            compose.waitUntil(10000) { compose.onAllNodesWithText(context.getString(R.string.photo_correction_title)).fetchSemanticsNodes().isNotEmpty() }
            val pixels = compose.onNodeWithTag("photo-correction-preview").captureToImage().toPixelMap()
            // The fixture is solid green: both reference axes must be visible over it.
            val centerX = pixels.width / 2
            val centerY = pixels.height / 2
            val fittedWidth = minOf(pixels.width.toFloat(), pixels.height * 1.5f)
            val fittedHeight = fittedWidth / 1.5f
            val offAxisX = (centerX + fittedWidth / 8f).toInt()
            val offAxisY = (centerY + fittedHeight / 8f).toInt()
            assertTrue(pixels[centerX, offAxisY].red > 0.5f)
            assertTrue(pixels[offAxisX, centerY].red > 0.5f)
            assertTrue(pixels[offAxisX, offAxisY].red < 0.1f)
            if (apply) {
                compose.onNodeWithTag("photo-correction-rotation").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(-15f) }
                compose.onNodeWithTag("photo-correction-horizontal").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(.2f) }
                compose.onNodeWithTag("photo-correction-vertical").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(-.1f) }
                compose.onNodeWithText(context.getString(R.string.photo_correction_reset)).performClick()
                compose.onNodeWithTag("photo-correction-rotation").assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(0f, -45f..45f))
                compose.onNodeWithTag("photo-correction-rotation").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(10f) }
                compose.onNodeWithTag("photo-correction-horizontal").assertRangeInfoEquals(androidx.compose.ui.semantics.ProgressBarRangeInfo(0f, -0.3f..0.3f))
                compose.onNodeWithTag("photo-correction-vertical").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(.2f) }
            }
            val action = context.getString(if (apply) R.string.photo_correction_apply else R.string.photo_correction_original)
            compose.waitUntil(10000) { !compose.onNodeWithText(action).fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) }
            compose.onNodeWithText(action).performClick()
            compose.waitUntil(10000) {
                var loaded = false
                scenario.onActivity { activity ->
                    loaded = view(activity).hasImage() && view(activity).isEnabled
                    if (loaded) {
                        assertTrue(view(activity).snapshotDocument().annotations.isEmpty())
                        projectId = AnnotationActivity::class.java.getDeclaredField("currentProjectId").apply { isAccessible = true }.get(activity) as String?
                    }
                }
                loaded
            }
            assertNotNull(projectId)
        } finally {
            finishAndCloseScenario(scenario)
            file.delete()
        }
        val reopened = ActivityScenario.launch<AnnotationActivity>(Intent(context, AnnotationActivity::class.java).putExtra(AnnotationActivity.EXTRA_PROJECT_ID, projectId))
        try {
            compose.waitUntil(10000) {
                var loaded = false
                reopened.onActivity { loaded = view(it).hasImage() && view(it).isEnabled }
                loaded
            }
            compose.onNodeWithText(context.getString(R.string.photo_correction_title)).assertDoesNotExist()
        } finally {
            finishAndCloseScenario(reopened)
            kotlinx.coroutines.runBlocking { ProjectServices.get(context).delete(requireNotNull(projectId)).await() }
        }
    }
}
