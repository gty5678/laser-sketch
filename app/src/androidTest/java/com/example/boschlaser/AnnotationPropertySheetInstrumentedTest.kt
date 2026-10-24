package com.example.boschlaser

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.boschlaser.ui.AnnotationPropertySheet
import com.example.boschlaser.ui.AnnotationPropertySheetStyle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnotationPropertySheetInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val visible = mutableStateOf(true)
    private val animateDismiss = mutableStateOf(false)
    private val resetKey = mutableStateOf(0)
    private var dismissals = 0
    private var canvasClicks = 0
    @After fun finishScreen() { finishAndCloseScenario(compose.activityRule.scenario) }

    private fun showSheet() {
        compose.setContent {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Box(Modifier.fillMaxSize().testTag("canvas").clickable { canvasClicks++ })
                AnnotationPropertySheet(visible.value, resetKey.value, animateDismiss.value, "Dismiss properties fixture",
                    AnnotationPropertySheetStyle(Color.White.copy(alpha = 245f / 255f), Color.Gray,
                        12.dp, 10.dp, 10.dp, 14.dp, 24.dp, 42.dp, 4.dp, 2.dp, 56.dp, 20.dp),
                    onSwipeDismiss = { dismissals++; animateDismiss.value = true; visible.value = false },
                    modifier = Modifier.width(260.dp)) {
                    Box(Modifier.fillMaxWidth().height(140.dp))
                }
            }
        }
    }
    private fun handle() = compose.onNodeWithTag("annotation-property-handle")
    private fun sheet() = compose.onNodeWithTag("annotation-property-sheet")
    private fun pixelsPerDp() = handle().fetchSemanticsNode().boundsInRoot.height / 24f

    @Test fun shortAndUpwardDragsReturnWithoutDismissalAndPanelBlocksCanvas() {
        showSheet()
        val original = sheet().fetchSemanticsNode().boundsInRoot
        val density = pixelsPerDp()
        handle().performTouchInput {
            down(center)
            moveBy(Offset(0f, 20f * density))
            moveBy(Offset(0f, 20f * density))
            up()
        }
        assertEquals(original, sheet().fetchSemanticsNode().boundsInRoot)
        handle().performTouchInput { down(center); moveBy(Offset(0f, -30f * density)); up() }
        assertEquals(original, sheet().fetchSemanticsNode().boundsInRoot)
        sheet().performClick()
        compose.runOnIdle { assertEquals(0, dismissals); assertEquals(0, canvasClicks) }
    }

    @Test fun cancelAboveThresholdDismissesOnceAndReleasesCanvasAfterAnimation() {
        showSheet()
        val density = pixelsPerDp()
        handle().performTouchInput { down(center); moveBy(Offset(0f, 80f * density)); cancel() }
        sheet().assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, dismissals) }
        compose.onNodeWithTag("canvas").performClick()
        compose.runOnIdle { assertEquals(1, canvasClicks) }
    }

    @Test fun newSelectionDuringDismissalCancelsTheOldAnimationAndResetsPosition() {
        showSheet()
        val original = sheet().fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { animateDismiss.value = true; visible.value = false }
            compose.mainClock.advanceTimeBy(80)
            compose.runOnIdle { resetKey.value++; animateDismiss.value = false; visible.value = true }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(300)
            assertEquals(original, sheet().fetchSemanticsNode().boundsInRoot)
            compose.runOnIdle { assertEquals(0, dismissals) }
        } finally { compose.mainClock.autoAdvance = true }
    }
}
